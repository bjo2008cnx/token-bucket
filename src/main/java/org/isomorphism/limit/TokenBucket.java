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
package org.isomorphism.limit;

import java.util.concurrent.TimeUnit;

/**
 * 令牌桶用于限制某个时间窗口的访问次数。
 *
 * @see <a href="http://en.wikipedia.org/wiki/Token_bucket">Token Bucket on Wikipedia</a>
 * @see <a href="http://en.wikipedia.org/wiki/Leaky_bucket">Leaky Bucket on Wikipedia</a>
 */
public interface TokenBucket {
    /**
     * 返回此令牌桶的容量。 这是桶可以在任何一个时间点保存的令牌的最大数量。
     *
     * @return 令牌桶的容量
     */
    long getCapacity();

    /**
     * 返回桶中当前的令牌数。 如果bucket为空，那么这个方法将返回0。
     *
     * @return 桶中当前的令牌数
     */
    long getNumTokens();

    /**
     * 返回直到下一组令牌可以添加到令牌桶的时间。
     *
     * @param unit 表示返回值的时间单位.
     * @return 下一组令牌可以添加到令牌桶中的时间。
     * @see org.isomorphism.limit.strategy.RefillStrategy#getDurationUntilNextRefill(java.util.concurrent.TimeUnit)
     */
    long getDurationUntilNextRefill(TimeUnit unit) throws UnsupportedOperationException;

    /**
     * 尝试从桶中消耗单个令牌。 如果它被消耗，则返回{@code true}，否则返回{@code false}。
     *
     * @return 如果它被消耗，则返回{@code true}，否则返回{@code false}。
     */
    boolean tryConsume();

    /**
     * 尝试从桶中消耗指定数量的令牌。 如果令牌被消耗，则返回{@code true}，否则返回{@code false}。
     *
     * @param numTokens 从桶中消耗的令牌数,必须是正数。
     * @return {@code true} 如果令牌被消费，否则{@code false}
     */
    boolean tryConsume(long numTokens);

    /**
     * 从桶中消耗单个令牌。 如果当前没有令牌可用，则该方法将阻塞，直到令牌变得可用。
     */
    void consume();

    /**
     * 从桶中消耗多个令牌。 如果足够的令牌当前不可用，那么这种方法将被阻塞
     *
     * @param numTokens 从桶中消耗的令牌数,必须是正数。
     */
    void consume(long numTokens);

    /**
     * 用指定数量的令牌重新填充桶。 如果桶当前已满或接近容量，则可能会添加少于{@code numTokens}。
     *
     * @param numTokens 要添加到桶中的令牌数。
     */
    void refill(long numTokens);




}
