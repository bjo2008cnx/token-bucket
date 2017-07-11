package org.isomorphism.limit.ratelimiter.impl;

import com.google.common.math.LongMath;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class SmoothRateLimiter extends AbstractRateLimiter {

    /**
     * 实现了以下功能，其中coldInterval = coldFactor * stableInterval
     * <p>
     * <pre>
     *          ^ throttling
     *          |
     *    cold  +                  /
     * interval |                 /.
     *          |                / .
     *          |               /  .   ← "warmup period" is the area of the trapezoid between
     *          |              /   .     thresholdPermits and maxPermits
     *          |             /    .
     *          |            /     .
     *          |           /      .
     *   stable +----------/  WARM .
     * interval |          .   UP  .
     *          |          . PERIOD.
     *          |          .       .
     *        0 +----------+-------+--------------→ storedPermits
     *          0 thresholdPermits maxPermits
     * </pre>
     * <p>
     * 在介绍这个特定功能的细节之前，让我们牢记基础知识：
     * <ol>
     * <li> AbstractRateLimiter（storedPermits）的状态是该图中的垂直线。
     * <li>当没有使用RateLimiter时，这是正确的（最大值为 maxPermits)
     * <li>当使用RateLimiter时，这会向左（下降到零）
     * <li>因为如果我们有storedPermits，我们从那些先取(since if we have storedPermits, we serve from those first)
     * <li>When _unused_, we go right at a constant rate! The rate at which we move to the right is  chosen as maxPermits / warmupPeriod. This ensures that the time it takes to go from 0 to
     * maxPermits is equal to warmupPeriod.
     * <li>When _used_, the time it takes, as explained in the introductory class note, is equal to the integral of our function, between X permits and X-K permits, assuming we want to
     * spend K saved permits.
     * </ol>
     *
     * <p>In summary, the time it takes to move to the left (spend K permits), is equal to the area of  the function of width == K.
     * <p>Assuming we have saturated demand, the time to go from maxPermits to thresholdPermits is
     * equa to warmupPeriod. And the time to go from thresholdPermits to 0 is warmupPeriod/2. (The
     * reason that this is warmupPeriod/2 is to maintain the behavior of the original implementation
     * where coldFactor was hard coded as 3.)
     *
     * <p>It remains to calculate thresholdsPermits and maxPermits.
     * <ul>
     * <li>The time to go from thresholdPermits to 0 is equal to the integral of the function
     * between 0 and thresholdPermits. This is thresholdPermits * stableIntervals. By (5) it is
     * also equal to warmupPeriod/2. Therefore
     * <blockquote>
     * thresholdPermits = 0.5 * warmupPeriod / stableInterval
     * </blockquote>
     * <p>
     * <li>The time to go from maxPermits to thresholdPermits is equal to the integral of the
     * function between thresholdPermits and maxPermits. This is the area of the pictured
     * trapezoid, and it is equal to 0.5 * (stableInterval + coldInterval) * (maxPermits -
     * thresholdPermits). It is also equal to warmupPeriod, so
     * <blockquote>
     * maxPermits = thresholdPermits + 2 * warmupPeriod / (stableInterval + coldInterval)
     * </blockquote>
     * <p>
     * </ul>
     */

    /**
     * The currently stored permits.
     */
    double storedPermits;

    /**
     * The maximum number of stored permits.
     */
    double maxPermits;

    /**
     * The interval between two unit requests, at our stable rate. E.g., a stable rate of 5 permits
     * per second has a stable interval of 200ms.
     */
    double stableIntervalMicros;

    /**
     * The time when the next request (no matter its size) will be granted. After granting a request,
     * this is pushed further in the future. Large requests push this further than small requests.
     */
    private long nextFreeTicketMicros = 0L; // could be either in the past or future

    SmoothRateLimiter(SleepingStopwatch stopwatch) {
        super(stopwatch);
    }

    @Override
    final void doSetRate(double permitsPerSecond, long nowMicros) {
        resync(nowMicros);
        double stableIntervalMicros = SECONDS.toMicros(1L) / permitsPerSecond;
        this.stableIntervalMicros = stableIntervalMicros;
        doSetRate(permitsPerSecond, stableIntervalMicros);
    }

    abstract void doSetRate(double permitsPerSecond, double stableIntervalMicros);

    @Override
    final double doGetRate() {
        return SECONDS.toMicros(1L) / stableIntervalMicros;
    }

    @Override
    final long queryEarliestAvailable(long nowMicros) {
        return nextFreeTicketMicros;
    }

    @Override
    final long reserveEarliestAvailable(int requiredPermits, long nowMicros) {
        resync(nowMicros);
        long returnValue = nextFreeTicketMicros;
        double storedPermitsToSpend = min(requiredPermits, this.storedPermits);
        double freshPermits = requiredPermits - storedPermitsToSpend;
        long waitMicros = storedPermitsToWaitTime(this.storedPermits, storedPermitsToSpend) + (long) (freshPermits * stableIntervalMicros);

        this.nextFreeTicketMicros = LongMath.saturatedAdd(nextFreeTicketMicros, waitMicros);
        this.storedPermits -= storedPermitsToSpend;
        return returnValue;
    }

    /**
     * Translates a specified portion of our currently stored permits which we want to spend/acquire,
     * into a throttling time. Conceptually, this evaluates the integral of the underlying function we
     * use, for the range of [(storedPermits - permitsToTake), storedPermits].
     * <p>
     * <p>This always holds: {@code 0 <= permitsToTake <= storedPermits}
     */
    abstract long storedPermitsToWaitTime(double storedPermits, double permitsToTake);

    /**
     * Returns the number of microseconds during cool down that we have to wait to get a new permit.
     */
    abstract double coolDownIntervalMicros();

    /**
     * Updates {@code storedPermits} and {@code nextFreeTicketMicros} based on the current time.
     */
    void resync(long nowMicros) {
        // if nextFreeTicket is in the past, resync to now
        if (nowMicros > nextFreeTicketMicros) {
            double newPermits = (nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
            storedPermits = min(maxPermits, storedPermits + newPermits);
            nextFreeTicketMicros = nowMicros;
        }
    }
}
