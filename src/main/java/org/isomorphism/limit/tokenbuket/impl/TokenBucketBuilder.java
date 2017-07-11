package org.isomorphism.limit.tokenbuket.impl;

import com.google.common.base.Ticker;
import com.google.common.util.concurrent.Uninterruptibles;
import org.isomorphism.limit.tokenbuket.TokenBucket;
import org.isomorphism.limit.tokenbuket.strategy.FixedIntervalRefillStrategy;
import org.isomorphism.limit.tokenbuket.strategy.RefillStrategy;
import org.isomorphism.limit.tokenbuket.strategy.SleepStrategy;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TokenBucketBuilder {
    private Long capacity = null;
    private long initialTokens = 0;
    private RefillStrategy refillStrategy = null;
    private SleepStrategy sleepStrategy = YIELDING_SLEEP_STRATEGY;
    private final Ticker ticker = Ticker.systemTicker();

    static final SleepStrategy YIELDING_SLEEP_STRATEGY = new SleepStrategy() {
        @Override
        public void sleep() {
            // 睡眠1个ns的时间，放弃控制，允许其他线程运行。
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.NANOSECONDS);
        }
    };

    static final SleepStrategy BUSY_WAIT_SLEEP_STRATEGY = new SleepStrategy() {
        @Override
        public void sleep() {
            // Do nothing, don't sleep.
        }
    };


    public static TokenBucketBuilder builder() {
        return new TokenBucketBuilder();
    }

    /**
     * 指定令牌桶的总容量。
     */
    public TokenBucketBuilder withCapacity(long numTokens) {
        checkArgument(numTokens > 0, "Must specify a positive number of tokens");
        capacity = numTokens;
        return this;
    }

    /**
     * 用特定数量的令牌初始化令牌桶。
     */
    public TokenBucketBuilder withInitialTokens(long numTokens) {
        checkArgument(numTokens > 0, "Must specify a positive number of tokens");
        initialTokens = numTokens;
        return this;
    }

    /**
     * 以固定间隔补充令牌。
     */
    public TokenBucketBuilder withFixedIntervalRefillStrategy(long refillTokens, long period, TimeUnit unit) {
        return withRefillStrategy(new FixedIntervalRefillStrategy(ticker, refillTokens, period, unit));
    }

    /**
     * 使用用户定义的充值策略。
     */
    public TokenBucketBuilder withRefillStrategy(RefillStrategy refillStrategy) {
        this.refillStrategy = checkNotNull(refillStrategy);
        return this;
    }

    /**
     * 使用一种让出CPU的策略
     */
    public TokenBucketBuilder withYieldingSleepStrategy() {
        return withSleepStrategy(YIELDING_SLEEP_STRATEGY);
    }

    /**
     * 使用不会将CPU谦让(yield)给其他进程的睡眠策略。 它将忙碌等到更多的令牌可用。
     */
    public TokenBucketBuilder withBusyWaitSleepStrategy() {
        return withSleepStrategy(BUSY_WAIT_SLEEP_STRATEGY);
    }

    /**
     * 使用用户定义的睡眠策略。
     */
    public TokenBucketBuilder withSleepStrategy(SleepStrategy sleepStrategy) {
        this.sleepStrategy = checkNotNull(sleepStrategy);
        return this;
    }

    /**
     * 构建令牌桶
     */
    public TokenBucket build() {
        checkNotNull(capacity, "Must specify a capacity");
        checkNotNull(refillStrategy, "Must specify a refill strategy");

        return new LeakyTokenBucket(capacity, initialTokens, refillStrategy, sleepStrategy);
    }

}