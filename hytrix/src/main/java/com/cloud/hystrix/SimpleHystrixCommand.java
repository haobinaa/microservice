package com.cloud.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;

/**
 * @Author HaoBin
 * @Create 2020/3/18 15:03
 * @Description:
 **/
public class SimpleHystrixCommand extends HystrixCommand<String> {

    private final String name;

    public SimpleHystrixCommand(String name) {
        // 必须指定 group
        super(Setter
                // 分组key
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("simple-group-key"))
                // command key
                .andCommandKey(HystrixCommandKey.Factory.asKey("simple-command-key"))
                // command 属性配置
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        // 信号量隔离
                        .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
                        .withExecutionTimeoutInMilliseconds(5000))
                // 线程池 key
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("simple-thread-poll-key"))
                // 线程池属性配置
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(2).withMaxQueueSize(10))
        );
        this.name = name;
    }

    @Override
    protected String run() throws Exception {
        return name + ", Thread:" + Thread.currentThread().getName();
    }


    @Override
    protected String getFallback() {
        System.out.println("触发了降级");
        return "fallback";
    }
}
