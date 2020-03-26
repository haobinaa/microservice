package com.limit.guava;

import com.revinate.guava.util.concurrent.RateLimiter;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author HaoBin
 * @Create 2020/3/26 17:04
 * @Description:
 **/
public class GoogleRateLimiter {


    public static void main(String[] args) {
        testSmoothWarmingUp();
    }



    public static void testSmoothBursty() {
        // 1s 五个， 基本每0.2s拿到一个
        RateLimiter rateLimiter = RateLimiter.create(5);
        while (true) {
            System.out.println("get token:" + rateLimiter.acquire() + "s");
        }
    }

    public static void testSmoothBursty2() {
        RateLimiter rateLimiter = RateLimiter.create(2);
        while (true) {
            System.out.println("get token:" + rateLimiter.acquire() + "s");
            try {
                // rateLimiter 会累计令牌，这个时候没有请求，就累积了2s的令牌
                Thread.sleep(2000l);
            }catch (Exception e) {}
            // 由于之前累计了，这里不用等待就能获取
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("end");
        }
    }


    public static void testSmoothBursty3() {
        RateLimiter rateLimiter = RateLimiter.create(5);
        while (true) {
            System.out.println("get 5 tokens: " + rateLimiter.acquire(5) + "s");
            // 滞后效应，需要替前一个请求进行等待
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("end");
        }
    }

    public static void testSmoothWarmingUp() {
        // 有一段时间的预热，在预热期内主键达到配置速率，比如下面设置的是三分钟
        RateLimiter rateLimiter = RateLimiter.create(2, 3, TimeUnit.SECONDS);
        while (true) {
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("get 1 tokens: " + rateLimiter.acquire(1) + "s");
            System.out.println("end");
        }
    }

}
