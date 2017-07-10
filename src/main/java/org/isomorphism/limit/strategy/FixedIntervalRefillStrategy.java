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
package org.isomorphism.limit.strategy;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;

/**
 * 令牌桶补充策略，每T个时间单位将为令牌桶提供N个令牌。
 * 令牌以突发而不是固定速率重新填充。这种补充策略将永远不会允许在时间T的窗口内消耗多于N个令牌。
 */
public class FixedIntervalRefillStrategy implements RefillStrategy {
    private final Ticker ticker;
    private final long numTokensPerPeriod;
    private final long periodDurationInNanos;
    private long lastRefillTime;
    private long nextRefillTime;

    /**
     * 创建fixedIntervalRefillStrategy.
     *
     * @param ticker             用于衡量时间的ticker
     * @param numTokensPerPeriod 每个时期添加到桶中的令牌数。
     * @param period             重新填充桶的频率
     * @param unit               时间单位
     */
    public FixedIntervalRefillStrategy(Ticker ticker, long numTokensPerPeriod, long period, TimeUnit unit) {
        this.ticker = ticker;
        this.numTokensPerPeriod = numTokensPerPeriod;
        this.periodDurationInNanos = unit.toNanos(period);
        this.lastRefillTime = -periodDurationInNanos;
        this.nextRefillTime = -periodDurationInNanos;
    }

    @Override
    public synchronized long refill() {
        long now = ticker.read();
        if (now < nextRefillTime) {
            return 0;
        }


        // 需要用一些令牌来重新填充桶， 我们需要计算出我们错过了多少个令牌值。
        long numPeriods = Math.max(0, (now - lastRefillTime) / periodDurationInNanos);

        // 将最后一次充值时间提前一段时间
        lastRefillTime += numPeriods * periodDurationInNanos;

        // 我们将在上一次重新填补后再次补充一次。
        nextRefillTime = lastRefillTime + periodDurationInNanos;

        return numPeriods * numTokensPerPeriod;
    }

    @Override
    public long getDurationUntilNextRefill(TimeUnit unit) {
        long now = ticker.read();
        return unit.convert(Math.max(0, nextRefillTime - now), TimeUnit.NANOSECONDS);
    }
}

