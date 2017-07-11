package org.isomorphism.limit.ratelimiter.impl;

import java.util.concurrent.TimeUnit;

public class SmoothWarmingUp extends SmoothRateLimiter {
    private final long warmupPeriodMicros;
    /**
     * 从许可== 0许可== maxPermits时的斜率
     */
    private double slope;
    private double thresholdPermits;
    private double coldFactor;

    SmoothWarmingUp(SleepingStopwatch stopwatch, long warmupPeriod, TimeUnit timeUnit, double coldFactor) {
        super(stopwatch);
        this.warmupPeriodMicros = timeUnit.toMicros(warmupPeriod);
        this.coldFactor = coldFactor;
    }

    @Override
    void doSetRate(double permitsPerSecond, double stableIntervalMicros) {
        double oldMaxPermits = maxPermits;
        double coldIntervalMicros = stableIntervalMicros * coldFactor;
        thresholdPermits = 0.5 * warmupPeriodMicros / stableIntervalMicros;
        maxPermits = thresholdPermits + 2.0 * warmupPeriodMicros / (stableIntervalMicros + coldIntervalMicros);
        slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits - thresholdPermits);
        if (oldMaxPermits == Double.POSITIVE_INFINITY) {
            //如果我们没有特殊情况，我们将在下面得到toredPermits == NaN
            storedPermits = 0.0;
        } else {//初始状态为cold (maxPermits)
            storedPermits = (oldMaxPermits == 0.0) ? maxPermits : storedPermits * maxPermits / oldMaxPermits;
        }
    }

    @Override
    long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
        double availablePermitsAboveThreshold = storedPermits - thresholdPermits;
        long micros = 0;
        // 测量功能正确部分的积分（攀爬线）
        if (availablePermitsAboveThreshold > 0.0) {
            double permitsAboveThresholdToTake = Math.min(availablePermitsAboveThreshold, permitsToTake);
            double length = permitsToTime(availablePermitsAboveThreshold) + permitsToTime(availablePermitsAboveThreshold - permitsAboveThresholdToTake);
            micros = (long) (permitsAboveThresholdToTake * length / 2.0);
            permitsToTake -= permitsAboveThresholdToTake;
        }
        // 测量功能左侧的积分（水平线）
        micros += (stableIntervalMicros * permitsToTake);
        return micros;
    }

    private double permitsToTime(double permits) {
        return stableIntervalMicros + permits * slope;
    }

    @Override
    double coolDownIntervalMicros() {
        return warmupPeriodMicros / maxPermits;
    }
}