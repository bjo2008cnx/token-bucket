package org.isomorphism.limit.ratelimiter.impl;

import java.util.concurrent.TimeUnit;


/**
 * Created by lenovo on 2017/7/11.
 */
public class RateLimiterTest {
    public static void main(String[] args) throws InterruptedException {
        RateLimiter limiter = RateLimiter.create(5, 1000, TimeUnit.MILLISECONDS);
        for(int i = 0; i < 5;i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        Thread.sleep(1000L);
        for(int i = 0; i < 5;i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        for(int i = 0; i < 5;i++) {
            System.out.println(limiter.acquire());
        }
        System.out.println("==============================================================");
        for(int i = 0; i < 5;i++) {
            System.out.println(limiter.acquire());
        }
    }

}