package org.isomorphism.limit.ratelimiter;

import com.google.common.annotations.VisibleForTesting;
import org.isomorphism.limit.ratelimiter.impl.SleepingStopwatch;
import org.isomorphism.limit.ratelimiter.impl.SmoothBursty;
import org.isomorphism.limit.ratelimiter.impl.SmoothWarmingUp;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * RateLimiterFacade
 *
 * @author Michael.Wang
 * @date 2017/7/11
 */
public class RateLimiters {
    /**
     * 根据指定的稳定吞吐率创建RateLimiter，这里的吞吐率是指每秒多少许可数（通常是指QPS，每秒多少查询）
     * <p>
     * 当传入请求率超过{@code permitPerSecond}时，速率限制器将每{@code（1.0 / licensesPerSecond）}秒释放一个许可证。
     * 速率限制器未使用时，将允许多达{@code permitPerSecond}许可证的突发数据，随后的请求以{@code licensesPerSecond}的稳定速率平滑地受到限制。
     * <p>
     * 返回的RateLimiter 确保了在平均情况下，每秒发布的许可数不会超过permitsPerSecond，每秒钟会持续发送请求。当传入请求速率超过permitsPerSecond，
     * 速率限制器会每秒释放一个许可(1.0 / permitsPerSecond 这里是指设定了permitsPerSecond为1.0) 。
     * 当速率限制器闲置时，允许许可数暴增到permitsPerSecond，随后的请求会被平滑地限制在稳定速率permitsPerSecond中。
     * 相当于{@code createWithCapacity(permitsPerSecond, 1, TimeUnit.SECONDS)}
     *
     * @param permitsPerSecond 返回的{@code RateLimiter}的速率，以每秒可用的许可证数量为单位
     * @throws IllegalArgumentException 如果{@code permitPerSecond}为负数或零
     */
    public static RateLimiter create(double permitsPerSecond) {
     /*
     * 默认的RateLimiter配置,可以保存长达一秒钟的未使用的许可证。
     * 这是为了避免在这种情况下不必要的停顿：一个1qps的RateLimiter和4个线程，在这些时刻调用acquire（）：
     *
     * T0 at 0 seconds
     * T1 at 1.05 seconds
     * T2 at 2 seconds
     * T3 at 3 seconds
     *
     * 由于T1的轻微延迟，T2将不得不睡到2.05秒，T3也必须睡到3.05秒。
     */
        return create(SleepingStopwatch.createFromSystemTimer(), permitsPerSecond);
    }

    /**
     * 根据指定的稳定吞吐率和预热期来创建RateLimiter，这里的吞吐率是指每秒多少许可数（通常是指QPS，每秒多少个请求量），
     * 在这段预热时间内，RateLimiter每秒分配的许可数会平稳地增长直到预热期结束时达到其最大速率。（只要存在足够请求数来使其饱和）
     * <p>
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
    public static RateLimiter create(double permitsPerSecond, long warmupPeriod, TimeUnit unit) {
        checkArgument(warmupPeriod >= 0, "warmupPeriod must not be negative: %s", warmupPeriod);
        return create(SleepingStopwatch.createFromSystemTimer(), permitsPerSecond, warmupPeriod, unit, 3.0);
    }

    @VisibleForTesting
    static RateLimiter create(SleepingStopwatch stopwatch, double permitsPerSecond) {
        RateLimiter rateLimiter = new SmoothBursty(stopwatch, 1.0 /* maxBurstSeconds */);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    @VisibleForTesting
    static RateLimiter create(SleepingStopwatch stopwatch, double permitsPerSecond, long warmupPeriod, TimeUnit unit, double coldFactor) {
        RateLimiter rateLimiter = new SmoothWarmingUp(stopwatch, warmupPeriod, unit, coldFactor);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }
}