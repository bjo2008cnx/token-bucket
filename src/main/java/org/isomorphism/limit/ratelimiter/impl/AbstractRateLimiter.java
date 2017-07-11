package org.isomorphism.limit.ratelimiter.impl;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 速率限制器以可配置的速率分配许可证。 如果需要，每个{@link #acquire（）}将阻塞，直到许可证可用，然后才能使用它。 一旦获得，许可证不需要被释放。
 * 速率限制器通常用于限制访问某些物理或逻辑资源的速率。
 * 这与{@link java.util.concurrent.Semaphore}相反，{@link java.util.concurrent.Semaphore}限制了并发访问次数而不是速率（注意并发和速率是密切相关的，
 * 参见<a href="http://en.wikipedia.org/wiki/Little%27s_law">Little's  * Law</a>).
 * {@code AbstractRateLimiter}主要由许可证发放的速率定义。 没有额外的配置，许可证将以固定的速率分配，按照许可证/每秒定义。 许可证将顺利分发，调整许可证之间的延迟，使速率得以维持。
 * <p>
 * <p>可以配置{@code AbstractRateLimiter}进行预热，在此期间，每次发出的许可证会稳定增加，直到达到稳定速率。 <p>
 * <p>例如，假设我们有一个要执行的任务列表，但是我们不想每秒提交超过2个：
 * <pre>   {@code
 *  final AbstractRateLimiter rateLimiter = AbstractRateLimiter.create(2.0); // 每秒不超过2个
 *  void submitTasks(List<Runnable> tasks, Executor executor) {
 *    for (Runnable task : tasks) {
 *      rateLimiter.acquire(); // 可能会等待
 *      executor.execute(task);
 *    }
 *  }}</pre>
 * <p>
 * <p>另一个例子，假设我们产生一个数据流，我们希望以每秒5kb的速度上限。 这可以通过要求每个字节的许可证，并指定每秒5000个许可证的速率来实现：
 * <pre>   {@code
 *  final AbstractRateLimiter rateLimiter = AbstractRateLimiter.create(5000.0); // rate = 5000 permits per second
 *  void submitPacket(byte[] packet) {
 *    rateLimiter.acquire(packet.length);
 *    networkService.send(packet);
 *  }}</pre>
 * <p>
 *  有一点很重要，那就是请求的许可数从来不会影响到请求本身的限制（调用acquire(1) 和调用acquire(1000) 将得到相同的限制效果，如果存在这样的调用的话），
 *  但会影响下一次请求的限制，也就是说，如果一个高开销的任务抵达一个空闲的RateLimiter，它会被马上许可，但是下一个请求会经历额外的限制，从而来偿付高开销任务。
 *  注意：AbstractRateLimiter 并不提供公平性的保证。
 */
@ThreadSafe
@Beta
@GwtIncompatible
public abstract class AbstractRateLimiter {
    /**
     * 根据指定的稳定吞吐率创建RateLimiter，这里的吞吐率是指每秒多少许可数（通常是指QPS，每秒多少查询）
     *
     * 当传入请求率超过{@code permitPerSecond}时，速率限制器将每{@code（1.0 / licensesPerSecond）}秒释放一个许可证。
     * 速率限制器未使用时，将允许多达{@code permitPerSecond}许可证的突发数据，随后的请求以{@code licensesPerSecond}的稳定速率平滑地受到限制。
     *
     * 返回的RateLimiter 确保了在平均情况下，每秒发布的许可数不会超过permitsPerSecond，每秒钟会持续发送请求。当传入请求速率超过permitsPerSecond，
     * 速率限制器会每秒释放一个许可(1.0 / permitsPerSecond 这里是指设定了permitsPerSecond为1.0) 。
     * 当速率限制器闲置时，允许许可数暴增到permitsPerSecond，随后的请求会被平滑地限制在稳定速率permitsPerSecond中。
     * @param permitsPerSecond 返回的{@code AbstractRateLimiter}的速率，以每秒可用的许可证数量为单位
     * @throws IllegalArgumentException 如果{@code permitPerSecond}为负数或零
     */
    // 这相当于{@code createWithCapacity(permitsPerSecond, 1, TimeUnit.SECONDS)}
    public static AbstractRateLimiter create(double permitsPerSecond) {
    /*
     * The default AbstractRateLimiter configuration can save the unused permits of up to one second. This
     * is to avoid unnecessary stalls in situations like this: A AbstractRateLimiter of 1qps, and 4 threads,
     * all calling acquire() at these moments:
     *
     * T0 at 0 seconds
     * T1 at 1.05 seconds
     * T2 at 2 seconds
     * T3 at 3 seconds
     *
     * Due to the slight delay of T1, T2 would have to sleep till 2.05 seconds, and T3 would also
     * have to sleep till 3.05 seconds.
     */
        return create(SleepingStopwatch.createFromSystemTimer(), permitsPerSecond);
    }

    @VisibleForTesting
    static AbstractRateLimiter create(SleepingStopwatch stopwatch, double permitsPerSecond) {
        AbstractRateLimiter rateLimiter = new SmoothBursty(stopwatch, 1.0 /* maxBurstSeconds */);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    /**
     *
     * 根据指定的稳定吞吐率和预热期来创建RateLimiter，这里的吞吐率是指每秒多少许可数（通常是指QPS，每秒多少个请求量），
     * 在这段预热时间内，RateLimiter每秒分配的许可数会平稳地增长直到预热期结束时达到其最大速率。（只要存在足够请求数来使其饱和）
     *
     * 根据指定的稳定吞吐率和预热期来创建RateLimiter，这里的吞吐率是指每秒多少许可数（通常是指QPS，每秒多少查询），
     * 在这段预热时间内，RateLimiter每秒分配的许可数会平稳地增长直到预热期结束时达到其最大速率（只要存在足够请求数来使其饱和）。
     * 同样地，如果RateLimiter 在warmupPeriod时间内闲置不用，它将会逐步地返回冷却状态。也就是说，它会像它第一次被创建般经历同样的预热期。
     * 返回的RateLimiter 主要用于那些需要预热期的资源，这些资源实际上满足了请求（比如一个远程服务），而不是在稳定（最大）的速率下可以立即被访问的资源。
     * 返回的RateLimiter 在冷却状态下启动（即预热期将会紧跟着发生），并且如果被长期闲置不用，它将回到冷却状态。
     * 应用场景：如果资源池长期不用，资源池将收缩到最小数量
     *
     * @param permitsPerSecond 返回的RateLimiter的速率，意味着每秒有多少个许可变成有效。
     * @param warmupPeriod     在这段时间内RateLimiter会增加它的速率，在抵达它的稳定速率或者最大速率之前
     * @param unit             参数warmupPeriod 的时间单位
     * @throws IllegalArgumentException 如果permitsPerSecond为负数或者为0
     */
    public static AbstractRateLimiter create(double permitsPerSecond, long warmupPeriod, TimeUnit unit) {
        checkArgument(warmupPeriod >= 0, "warmupPeriod must not be negative: %s", warmupPeriod);
        return create(SleepingStopwatch.createFromSystemTimer(), permitsPerSecond, warmupPeriod, unit, 3.0);
    }

    @VisibleForTesting
    static AbstractRateLimiter create(SleepingStopwatch stopwatch, double permitsPerSecond, long warmupPeriod, TimeUnit unit, double coldFactor) {
        AbstractRateLimiter rateLimiter = new SmoothWarmingUp(stopwatch, warmupPeriod, unit, coldFactor);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    /**
     * The underlying timer; used both to measure elapsed time and sleep as necessary. A separate
     * object to facilitate testing.
     */
    private final SleepingStopwatch stopwatch;

    // Can't be initialized in the constructor because mocks don't call the constructor.
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
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public double acquire(int permits) {
        long microsToWait = reserve(permits);
        stopwatch.sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / SECONDS.toMicros(1L);
    }

    /**
     * Reserves the given number of permits from this {@code AbstractRateLimiter} for future use, returning
     * the number of microseconds until the reservation can be consumed.
     *
     * @return time in microseconds to wait until the resource can be acquired, never negative
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
     * Reserves next ticket and returns the wait time that the caller must wait for.
     *
     * @return the required wait time, never negative
     */
    final long reserveAndGetWaitLength(int permits, long nowMicros) {
        long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
        return max(momentAvailable - nowMicros, 0);
    }

    /**
     * Returns the earliest time that permits are available (with one caveat).
     *
     * @return the time that permits are available, or, if permits are available immediately, an
     * arbitrary past or present time
     */
    abstract long queryEarliestAvailable(long nowMicros);

    /**
     * Reserves the requested number of permits and returns the time that those permits can be used
     * (with one caveat).
     *
     * @return the time that the permits may be used, or, if the permits may be used immediately, an
     * arbitrary past or present time
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
