package org.isomorphism.limit.ratelimiter;

import java.util.concurrent.TimeUnit;

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
 *  final RateLimiter rateLimiter = RateLimiters.create(2.0); // 每秒不超过2个
 *  void submitTasks(List<Runnable> tasks, Executor executor) {
 *    for (Runnable task : tasks) {
 *      rateLimiter.acquire(); // 可能会等待
 *      executor.execute(task);
 *    }
 *  }}</pre>
 * <p>
 * <p>另一个例子，假设我们产生一个数据流，我们希望以每秒5kb的速度上限。 这可以通过要求每个字节的许可证，并指定每秒5000个许可证的速率来实现：
 * <pre>   {@code
 *  final RateLimiter rateLimiter = RateLimiters.create(5000.0); // rate = 5000 permits per second
 *  void submitPacket(byte[] packet) {
 *    rateLimiter.acquire(packet.length);
 *    networkService.send(packet);
 *  }}</pre>
 * <p>
 *  有一点很重要，那就是请求的许可数从来不会影响到请求本身的限制（调用acquire(1) 和调用acquire(1000) 将得到相同的限制效果，如果存在这样的调用的话），
 *  但会影响下一次请求的限制，也就是说，如果一个高开销的任务抵达一个空闲的RateLimiter，它会被马上许可，但是下一个请求会经历额外的限制，从而来偿付高开销任务。
 *  注意：AbstractRateLimiter 并不提供公平性的保证。
 *
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
    void setRate(double permitsPerSecond);
}