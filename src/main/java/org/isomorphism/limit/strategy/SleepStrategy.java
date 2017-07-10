package org.isomorphism.limit.strategy;

/**
     * 封装放弃CPU控制策略。
     */
    public interface SleepStrategy {
        /**
         * 休息一段时间以允许其他线程和系统进程执行。
         */
        void sleep();
    }