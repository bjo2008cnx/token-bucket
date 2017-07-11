package org.isomorphism.limit.ratelimiter.impl;

/**
 * 实现了“突发” AbstractRateLimiter，其中storedPermits被转换到零节流(are translated to zero throttling)。
 * （当RateLimiter未使用时）可以保存的许可证的最大数量由时间来定义，如：如果RateLimiter是2qps，并且这个时间被指定为10秒，我们可以节省高达2 * 10 = 20个许可证。
 */
public class SmoothBursty extends SmoothRateLimiter {
    /**
     * 如果没有使用RateLimiter，可以保存多少秒的工作（许可）?
     */
    final double maxBurstSeconds;

    SmoothBursty(SleepingStopwatch stopwatch, double maxBurstSeconds) {
        super(stopwatch);
        this.maxBurstSeconds = maxBurstSeconds;
    }

    @Override
    void doSetRate(double permitsPerSecond, double stableIntervalMicros) {
        double oldMaxPermits = this.maxPermits;
        maxPermits = maxBurstSeconds * permitsPerSecond;
        if (oldMaxPermits == Double.POSITIVE_INFINITY) {
            // if we don't special-case this, we would get storedPermits == NaN, below
            storedPermits = maxPermits;
        } else {
            storedPermits = (oldMaxPermits == 0.0) ? 0.0 // initial state
                    : storedPermits * maxPermits / oldMaxPermits;
        }
    }

    @Override
    long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
        return 0L;
    }

    @Override
    double coolDownIntervalMicros() {
        return stableIntervalMicros;
    }
}