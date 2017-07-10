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

import org.isomorphism.limit.tokenbuket.impl.LeakyTokenBucket;
import org.isomorphism.limit.tokenbuket.strategy.RefillStrategy;
import org.isomorphism.limit.tokenbuket.strategy.SleepStrategy;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class LeakyTokenBucketTest {
    private static final long CAPACITY = 10;

    private final MockRefillStrategy refillStrategy = new MockRefillStrategy();
    private final SleepStrategy sleepStrategy = mock(SleepStrategy.class);
    private final LeakyTokenBucket bucket = new LeakyTokenBucket(CAPACITY, 0, refillStrategy, sleepStrategy);

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeCapacity() {
        new LeakyTokenBucket(-1, 0, refillStrategy, sleepStrategy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroCapacity() {
        new LeakyTokenBucket(0, 0, refillStrategy, sleepStrategy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMoreInitialTokensThanCapacity() {
        new LeakyTokenBucket(1, 2, refillStrategy, sleepStrategy);
    }

    @Test
    public void testGetCapacity() {
        assertEquals(CAPACITY, bucket.getCapacity());
    }

    @Test
    public void testEmptyBucketHasZeroTokens() {
        assertEquals(0, bucket.getNumTokens());
    }

    @Test
    public void testBucketWithInitialTokens() {
        LeakyTokenBucket bucket = new LeakyTokenBucket(CAPACITY, CAPACITY, refillStrategy, sleepStrategy);
        assertEquals(CAPACITY, bucket.getNumTokens());
    }

    @Test
    public void testAddingTokenIncreasesNumTokens() {
        refillStrategy.addToken();
        assertEquals(1, bucket.getNumTokens());
    }

    @Test
    public void testAddingMultipleTokensIncreasesNumTokens() {
        refillStrategy.addTokens(2);
        assertEquals(2, bucket.getNumTokens());
    }

    @Test
    public void testAtCapacityNumTokens() {
        refillStrategy.addTokens(CAPACITY);
        assertEquals(CAPACITY, bucket.getNumTokens());
    }

    @Test
    public void testOverCapacityNumTokens() {
        refillStrategy.addTokens(CAPACITY + 1);
        assertEquals(CAPACITY, bucket.getNumTokens());
    }

    @Test
    public void testConsumingTokenDecreasesNumTokens() {
        refillStrategy.addTokens(1);
        bucket.consume();
        assertEquals(0, bucket.getNumTokens());
    }

    @Test
    public void testConsumingMultipleTokensDecreasesNumTokens() {
        refillStrategy.addTokens(CAPACITY);
        bucket.consume(2);
        assertEquals(CAPACITY - 2, bucket.getNumTokens());
    }

    @Test
    public void testEmptyNumTokens() {
        refillStrategy.addTokens(CAPACITY);
        bucket.consume(CAPACITY);
        assertEquals(0, bucket.getNumTokens());
    }

    @Test
    public void testFailedConsumeKeepsNumTokens() {
        refillStrategy.addTokens(1);
        bucket.tryConsume(2);
        assertEquals(1, bucket.getNumTokens());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTryConsumeZeroTokens() {
        bucket.tryConsume(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTryConsumeNegativeTokens() {
        bucket.tryConsume(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTryConsumeMoreThanCapacityTokens() {
        bucket.tryConsume(100);
    }

    @Test
    public void testTryConsumeOnEmptyBucket() {
        assertFalse(bucket.tryConsume());
    }

    @Test
    public void testTryConsumeOneToken() {
        refillStrategy.addToken();
        assertTrue(bucket.tryConsume());
    }

    @Test
    public void testTryConsumeMoreTokensThanAreAvailable() {
        refillStrategy.addToken();
        assertFalse(bucket.tryConsume(2));
    }

    @Test
    public void testTryManuallyRefillOneToken() {
        bucket.refill(1);
        assertTrue(bucket.tryConsume());
    }

    @Test
    public void testTryManuallyRefillCapacityTokens() {
        bucket.refill(CAPACITY);
        assertTrue(bucket.tryConsume(CAPACITY));
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    public void testTryManuallyRefillMoreThanCapacityTokens() {
        bucket.refill(CAPACITY + 1);
        assertTrue(bucket.tryConsume(CAPACITY));
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    public void testTryManualRefillAndStrategyRefill() {
        bucket.refill(CAPACITY);
        refillStrategy.addTokens(CAPACITY);
        assertTrue(bucket.tryConsume(CAPACITY));
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    public void testTryRefillMoreThanCapacityTokens() {
        refillStrategy.addTokens(CAPACITY + 1);
        assertTrue(bucket.tryConsume(CAPACITY));
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    public void testTryRefillWithTooManyTokens() {
        refillStrategy.addTokens(CAPACITY);
        assertTrue(bucket.tryConsume());

        refillStrategy.addTokens(Long.MAX_VALUE);
        assertTrue(bucket.tryConsume(CAPACITY));
        assertFalse(bucket.tryConsume(1));
    }

    private static final class MockRefillStrategy implements RefillStrategy {
        private long numTokensToAdd = 0;

        public long refill() {
            long numTokens = numTokensToAdd;
            numTokensToAdd = 0;
            return numTokens;
        }

        @Override
        public long getDurationUntilNextRefill(TimeUnit unit) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        public void addToken() {
            numTokensToAdd++;
        }

        public void addTokens(long numTokens) {
            numTokensToAdd += numTokens;
        }
    }
}
