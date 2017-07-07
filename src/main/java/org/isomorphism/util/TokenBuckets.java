/*
 * Copyright 2012-2014 Brandon Beck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.isomorphism.util;

import com.google.common.base.Ticker;
import com.google.common.util.concurrent.Uninterruptibles;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 创建{@link TokenBucket}实例的静态构造器。
 */
public final class TokenBuckets {
    private TokenBuckets() {
    }

    /**
     * 为令牌桶创建一个新的构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long capacity = null;
        private long initialTokens = 0;
        private TokenBucketImpl.RefillStrategy refillStrategy = null;
        private TokenBucketImpl.SleepStrategy sleepStrategy = YIELDING_SLEEP_STRATEGY;
        private final Ticker ticker = Ticker.systemTicker();

        /**
         * 指定令牌桶的总容量。
         */
        public Builder withCapacity(long numTokens) {
            checkArgument(numTokens > 0, "Must specify a positive number of tokens");
            capacity = numTokens;
            return this;
        }

        /**
         * 用特定数量的令牌初始化令牌桶。
         */
        public Builder withInitialTokens(long numTokens) {
            checkArgument(numTokens > 0, "Must specify a positive number of tokens");
            initialTokens = numTokens;
            return this;
        }

        /**
         * 以固定间隔补充令牌。
         */
        public Builder withFixedIntervalRefillStrategy(long refillTokens, long period, TimeUnit unit) {
            return withRefillStrategy(new FixedIntervalRefillStrategy(ticker, refillTokens, period, unit));
        }

        /**
         * 使用用户定义的充值策略。
         */
        public Builder withRefillStrategy(TokenBucket.RefillStrategy refillStrategy) {
            this.refillStrategy = checkNotNull(refillStrategy);
            return this;
        }

        /**
         * 使用一种让出CPU的策略
         */
        public Builder withYieldingSleepStrategy() {
            return withSleepStrategy(YIELDING_SLEEP_STRATEGY);
        }

        /**
         * 使用不会将CPU谦让(yield)给其他进程的睡眠策略。 它将忙碌等到更多的令牌可用。
         */
        public Builder withBusyWaitSleepStrategy() {
            return withSleepStrategy(BUSY_WAIT_SLEEP_STRATEGY);
        }

        /**
         * 使用用户定义的睡眠策略。
         */
        public Builder withSleepStrategy(TokenBucket.SleepStrategy sleepStrategy) {
            this.sleepStrategy = checkNotNull(sleepStrategy);
            return this;
        }

        /**
         * 构建令牌桶。
         */
        public TokenBucket build() {
            checkNotNull(capacity, "Must specify a capacity");
            checkNotNull(refillStrategy, "Must specify a refill strategy");

            return new TokenBucketImpl(capacity, initialTokens, refillStrategy, sleepStrategy);
        }
    }

    private static final TokenBucketImpl.SleepStrategy YIELDING_SLEEP_STRATEGY = new TokenBucketImpl.SleepStrategy() {
        @Override
        public void sleep() {
            // 睡眠1个ns的时间，放弃控制，允许其他线程运行。
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.NANOSECONDS);
        }
    };

    private static final TokenBucketImpl.SleepStrategy BUSY_WAIT_SLEEP_STRATEGY = new TokenBucketImpl.SleepStrategy() {
        @Override
        public void sleep() {
            // Do nothing, don't sleep.
        }
    };
}
