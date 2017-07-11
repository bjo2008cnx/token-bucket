package org.isomorphism.limit.ratelimiter.impl;

import org.isomorphism.limit.ratelimiter.RateLimiter;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractRateLimiter implements RateLimiter {

    /**
     * 底层计时器; 用于必要时测量经过的时间和睡眠。 一个单独的对象来方便测试。
     */
    private final SleepingStopwatch stopwatch;

    //在构造函数中无法初始化，因为mock不调用构造函数。
    private volatile Object mutexDoNotUseDirectly;

    private Object mutex() {
        Object mutex = mutexDoNotUseDirectly;
        if (mutex == null) {
            synchronized (this) {
                mutex = mutexDoNotUseDirectly;
                if (mutex == null) {
                    mutexDoNotUseDirectly = mutex = new Object();
                }
            }
        }
        return mutex;
    }

    AbstractRateLimiter(SleepingStopwatch stopwatch) {
        this.stopwatch = checkNotNull(stopwatch);
    }

    /**
     * 更新RateLimite的稳定速率，参数permitsPerSecond 由构造RateLimiter的工厂方法提供。
     * 调用该方法后，当前限制线程不会被唤醒，因此他们不会注意到最新的速率；只有接下来的请求才会。
     * 需要注意的是，由于每次请求偿还了（通过等待，如果需要的话）上一次请求的开销，这意味着紧紧跟着的下一个请求不会被最新的速率影响到，在调用了setRate 之后；
     * 它会偿还上一次请求的开销，这个开销依赖于之前的速率。
     * RateLimiter的行为在任何方式下都不会被改变，比如如果 AbstractRateLimiter 有20秒的预热期配置，在此方法被调用后它还是会进行20秒的预热。
     *
     * @param permitsPerSecond RateLimiter的新的稳定速率
     * @throws IllegalArgumentException 如果permitsPerSecond为负数或者为0
     */
    public final void setRate(double permitsPerSecond) {
        checkArgument(permitsPerSecond > 0.0 && !Double.isNaN(permitsPerSecond), "rate must be positive");
        synchronized (mutex()) {
            doSetRate(permitsPerSecond, stopwatch.readMicros());
        }
    }

    abstract void doSetRate(double permitsPerSecond, long nowMicros);

    /**
     * 返回RateLimiter 配置中的稳定速率，该速率单位是每秒多少许可数
     * 它的初始值相当于构造这个RateLimiter的工厂方法中的参数permitsPerSecond ，并且只有在调用setRate(double)后才会被更新。
     */
    public final double getRate() {
        synchronized (mutex()) {
            return doGetRate();
        }
    }

    abstract double doGetRate();

    /**
     *从RateLimiter获取一个许可，该方法会被阻塞直到获取到请求。如果存在等待的情况的话，告诉调用者获取到该请求所需要的睡眠时间。该方法等同于acquire(1)。
     *
     * 执行速率的所需要的睡眠时间，单位为妙；如果没有则返回0
     */
    public double acquire() {
        return acquire(1);
    }

    /**
     *从RateLimiter获取指定许可数，该方法会被阻塞直到获取到请求数。如果存在等待的情况的话，告诉调用者获取到这些请求数所需要的睡眠时间。
     *
     * @param permits  需要获取的许可数
     * @return 执行速率的所需要的睡眠时间，单位为妙；如果没有则返回0
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    public double acquire(int permits) {
        long microsToWait = reserve(permits);
        stopwatch.sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / SECONDS.toMicros(1L);
    }

    /**
     * 从{@code AbstractRateLimiter}中预留给定数量的许可证以供将来使用，返回微秒数，直到预留被使用。
     *
     * @return 资源可用的时间，以毫秒为单位
     */
    final long reserve(int permits) {
        checkPermits(permits);
        synchronized (mutex()) {
            return reserveAndGetWaitLength(permits, stopwatch.readMicros());
        }
    }

    /**
     * 从RateLimiter获取许可如果该许可可以在不超过timeout的时间内获取得到的话，或者如果无法在timeout 过期之前获取得到许可的话，那么立即返回false（无需等待）。
     * 该方法等同于tryAcquire(1, timeout, unit)。
     *
     * @param timeout 等待许可的最大时间，负数以0处理
     * @param unit    参数timeout 的时间单位
     * @return true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 从RateLimiter 获取许可数，如果该许可数可以在无延迟下的情况下立即获取得到的话。该方法等同于tryAcquire(permits, 0, anyUnit)。
     *
     * @param permits  需要获取的许可数
     * @return  true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     * @since 14.0
     */
    public boolean tryAcquire(int permits) {
        return tryAcquire(permits, 0, MICROSECONDS);
    }

    /**
     * 从RateLimiter 获取许可，如果该许可可以在无延迟下的情况下立即获取得到的话
     * 该方法等同于tryAcquire(1)。
     *
     * @return true表示获取到许可，反之则是false
     */
    public boolean tryAcquire() {
        return tryAcquire(1, 0, MICROSECONDS);
    }

    /**
     * 从RateLimiter 获取指定许可数如果该许可数可以在不超过timeout的时间内获取得到的话，或者如果无法在timeout 过期之前获取得到许可数的话，
     * 那么立即返回false （无需等待）
     *
     * @param permits 需要获取的许可数
     * @param timeout 等待许可数的最大时间，负数以0处理
     * @param unit     参数timeout 的时间单位
     * @return true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        long timeoutMicros = max(unit.toMicros(timeout), 0);
        checkPermits(permits);
        long microsToWait;
        synchronized (mutex()) {
            long nowMicros = stopwatch.readMicros();
            if (!canAcquire(nowMicros, timeoutMicros)) {
                return false;
            } else {
                microsToWait = reserveAndGetWaitLength(permits, nowMicros);
            }
        }
        stopwatch.sleepMicrosUninterruptibly(microsToWait);
        return true;
    }

    private boolean canAcquire(long nowMicros, long timeoutMicros) {
        return queryEarliestAvailable(nowMicros) - timeoutMicros <= nowMicros;
    }

    /**
     * 保留下一张票并返回主叫方必须等待的等待时间。
     *
     * @return 所需要的等待时间，非负数
     */
    final long reserveAndGetWaitLength(int permits, long nowMicros) {
        long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
        return max(momentAvailable - nowMicros, 0);
    }

    /**
     * 返回permits的最早时间
     *
     * @return permits 可用的时间，或者如果permits可以立即可用，任意的过去或现在的时间
     */
    abstract long queryEarliestAvailable(long nowMicros);

    /**
     * 保留所需的许可证数量，并返回可以使用这些许可证的时间(with one caveat).
     *
     * @return 可以使用许可证的时间，或者如果许可证可以立即使用，任意过去或现在的时间
     */
    abstract long reserveEarliestAvailable(int permits, long nowMicros);

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "AbstractRateLimiter[stableRate=%3.1fqps]", getRate());
    }


    private static void checkPermits(int permits) {
        checkArgument(permits > 0, "Requested permits (%s) must be positive", permits);
    }
}
