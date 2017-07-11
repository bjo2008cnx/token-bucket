package org.isomorphism.limit.ratelimiter;

import java.util.concurrent.TimeUnit;

/**
 * RateLimiter
 *
 * @author Michael.Wang
 * @date 2017/7/11
 */
public interface RateLimiter {
    /**
     * 从RateLimiter获取一个许可，该方法会被阻塞直到获取到请求。如果存在等待的情况的话，告诉调用者获取到该请求所需要的睡眠时间。该方法等同于acquire(1)。
     * <p>
     * 执行速率的所需要的睡眠时间，单位为妙；如果没有则返回0
     */
    double acquire();

    /**
     * 从RateLimiter获取指定许可数，该方法会被阻塞直到获取到请求数。如果存在等待的情况的话，告诉调用者获取到这些请求数所需要的睡眠时间。
     *
     * @param permits 需要获取的许可数
     * @return 执行速率的所需要的睡眠时间，单位为妙；如果没有则返回0
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    double acquire(int permits);

    /**
     * 从RateLimiter获取许可如果该许可可以在不超过timeout的时间内获取得到的话，或者如果无法在timeout 过期之前获取得到许可的话，那么立即返回false（无需等待）。
     * 该方法等同于tryAcquire(1, timeout, unit)。
     *
     * @param timeout 等待许可的最大时间，负数以0处理
     * @param unit    参数timeout 的时间单位
     * @return true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    boolean tryAcquire(long timeout, TimeUnit unit);

    /**
     * 从RateLimiter 获取许可数，如果该许可数可以在无延迟下的情况下立即获取得到的话。该方法等同于tryAcquire(permits, 0, anyUnit)。
     *
     * @param permits 需要获取的许可数
     * @return true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     * @since 14.0
     */
    boolean tryAcquire(int permits);

    /**
     * 从RateLimiter 获取许可，如果该许可可以在无延迟下的情况下立即获取得到的话
     * 该方法等同于tryAcquire(1)。
     *
     * @return true表示获取到许可，反之则是false
     */
    boolean tryAcquire();

    /**
     * 从RateLimiter 获取指定许可数如果该许可数可以在不超过timeout的时间内获取得到的话，或者如果无法在timeout 过期之前获取得到许可数的话，
     * 那么立即返回false （无需等待）
     *
     * @param permits 需要获取的许可数
     * @param timeout 等待许可数的最大时间，负数以0处理
     * @param unit    参数timeout 的时间单位
     * @return true表示获取到许可，反之则是false
     * @throws IllegalArgumentException 如果请求的许可数为负数或者为0
     */
    boolean tryAcquire(int permits, long timeout, TimeUnit unit);

}