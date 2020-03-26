package com.limit.guava;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import sun.nio.ch.ThreadPool;

/**
 * @Author HaoBin
 * @Create 2020/3/26 17:08
 * @Description: semaphore 限流实现
 **/
public class SemaphoreLimiter {


    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(10);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60l, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>());
        for (int i = 0; i < 100; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    semaphore.acquireUninterruptibly();
                    try {
                        // 模拟业务处理2s
                        System.out.println(System.currentTimeMillis() + ":开始执行处理逻辑");
                        Thread.sleep(2000l);
                    } catch (InterruptedException e) {

                    } finally {
                        semaphore.release();
                    }
                }
            });
        }
    }
}
