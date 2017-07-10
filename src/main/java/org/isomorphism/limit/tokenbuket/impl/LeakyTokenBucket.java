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
package org.isomorphism.limit.tokenbuket.impl;

import org.isomorphism.limit.tokenbuket.TokenBucket;
import org.isomorphism.limit.tokenbuket.strategy.RefillStrategy;
import org.isomorphism.limit.tokenbuket.strategy.SleepStrategy;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 令牌桶实现是一个漏斗桶，因为它具有有限的容量，任何增加的令牌将超出这个容量将“溢出”出来，并永远丢失。
 * <p/>
 * 在这个实现中，重新填充桶的规则被封装在提供的{@code RefillStrategy}实例中。 在尝试使用任何令牌之前，将咨询补充策略，以了解应该将多少个令牌添加到存储桶中。
 * <p/>
 * 另外在这个实现中，退让CPU控制(yield cpu control)的方法封装在提供的{@code SleepStrategy}实例中。
 * 对于高性能应用程序，其中令牌快速重新填充，需要不让出CPU， 这个策略允许调用者为自己做出这个决定。
 */
public class LeakyTokenBucket implements TokenBucket {
    private final long capacity;
    private final RefillStrategy refillStrategy;
    private final SleepStrategy sleepStrategy;
    private long size;

    public LeakyTokenBucket(long capacity, long initialTokens, RefillStrategy refillStrategy, SleepStrategy sleepStrategy) {
        checkArgument(capacity > 0);
        checkArgument(initialTokens <= capacity);

        this.capacity = capacity;
        this.refillStrategy = checkNotNull(refillStrategy);
        this.sleepStrategy = checkNotNull(sleepStrategy);
        this.size = initialTokens;
    }

    /**
     * 返回此令牌桶的容量。 这是桶可以在任何一个时间点保存的令牌的最大数量。
     *
     * @return 令牌桶的容量
     */
    @Override
    public long getCapacity() {
        return capacity;
    }

    /**
     * 返回桶中当前的令牌数。 如果bucket为空，那么这个方法将返回0。
     *
     * @return 桶中当前的令牌数
     */
    @Override
    public synchronized long getNumTokens() {
        // 给予补充策略一个机会添加标记，使我们有一个准确的计数。
        refill(refillStrategy.refill());
        return size;
    }

    /**
     * 返回直到下一组令牌可以添加到令牌桶的时间
     *
     * @param unit 时间单位
     * @return 直到下一组令牌可以添加到令牌桶的时间
     * @see org.isomorphism.limit.tokenbuket.strategy.RefillStrategy#getDurationUntilNextRefill(java.util.concurrent.TimeUnit)
     */
    @Override
    public long getDurationUntilNextRefill(TimeUnit unit) throws UnsupportedOperationException {
        return refillStrategy.getDurationUntilNextRefill(unit);
    }

    /**
     * 尝试从桶中消耗单个令牌。 如果它被消耗，则返回{@code true}，否则返回{@code false}。
     *
     * @return 如果它被消耗，则返回{@code true}，否则返回{@code false}。
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * 尝试从桶中消耗指定数量的令牌。 如果令牌被消耗，则返回{@code true}，否则返回{@code false}。
     *
     * @param numTokens 从桶中消耗的令牌数,必须是正数。
     * @return {@code true} 如果令牌被消费，否则{@code false}
     */
    public synchronized boolean tryConsume(long numTokens) {
        checkArgument(numTokens > 0, "Number of tokens to consume must be positive");
        checkArgument(numTokens <= capacity, "Number of tokens to consume must be less than the capacity of the bucket.");

        refill(refillStrategy.refill());

        // Now try to consume some tokens
        if (numTokens <= size) {
            size -= numTokens;
            return true;
        }

        return false;
    }

    /**
     * 从桶中消耗单个令牌。 如果当前没有令牌可用，则该方法将阻塞，直到令牌变得可用。
     */
    public void consume() {
        consume(1);
    }

    /**
     * 从桶中消耗多个令牌。 如果足够的令牌当前不可用，那么这种方法将被阻塞
     *
     * @param numTokens 从桶中消耗的令牌数,必须是正数。
     */
    public void consume(long numTokens) {
        while (true) {
            if (tryConsume(numTokens)) {
                break;
            }

            sleepStrategy.sleep();
        }
    }

    /**
     * 用指定数量的令牌重新填充桶。 如果桶当前已满或接近容量，则可能会添加少于{@code numTokens}。
     *
     * @param numTokens 要添加到桶中的令牌数。
     */
    public synchronized void refill(long numTokens) {
        long newTokens = Math.min(capacity, Math.max(0, numTokens));
        size = Math.max(0, Math.min(size + newTokens, capacity));
    }
}
