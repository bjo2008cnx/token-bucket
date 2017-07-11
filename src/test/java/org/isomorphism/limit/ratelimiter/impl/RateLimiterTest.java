package org.isomorphism.limit.ratelimiter.impl;

import java.util.concurrent.TimeUnit;


/**
 * 平滑测试：一开始等待的时间比较多，后面逐渐趋于平稳
 * Created by lenovo on 2017/7/11.
 */
public class RateLimiterTest {
    public static void main(String[] args) throws InterruptedException {
        AbstractRateLimiter limiter = AbstractRateLimiter.create(5, 1000, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 5; i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        Thread.sleep(1000L);
        for (int i = 0; i < 5; i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        for (int i = 0; i < 5; i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        for (int i = 0; i < 5; i++) {
            System.out.println(limiter.acquire());
        }
    }

}