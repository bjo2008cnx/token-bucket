package org.isomorphism.util.strategy;

import java.util.concurrent.TimeUnit;

/**
     * 封装令牌桶的重新填充策略。
     */
public interface RefillStrategy {
        /**
         * 返回要添加到令牌桶的令牌数。
         *
         * @return 要添加到令牌桶的令牌数。
         */
        long refill();

        /**
         * 返回直到下一组令牌可以添加到令牌桶的时间。
         * 请注意，根据令牌桶使用的{@code SleepStrategy}，令牌可能不会在返回的持续时间之后实际添加。
         * 如果由于某些原因，{@code RefillStrategy}的实例不支持“知道何时将添加下一批令牌”，则可能会抛出{@code UnsupportedOperationException}。
         * 如果下一次令牌添加到令牌桶中的时间小于个时间单位，则该方法将返回0。
         *
         * @param unit 表示返回值的时间单位。
         * @return 下一组令牌可以添加到令牌桶中的时间量。
         */
        long getDurationUntilNextRefill(TimeUnit unit) throws UnsupportedOperationException;
    }