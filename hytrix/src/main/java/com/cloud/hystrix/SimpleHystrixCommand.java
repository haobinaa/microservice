package com.cloud.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.HystrixThreadPoolKey;

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
        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("simple-group-key"))
        .andCommandKey(HystrixCommandKey.Factory.asKey("simple-command-key"))
        .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("simple-threadpoll-key"))
        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
            // 信号量隔离
            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
            .withExecutionTimeoutInMilliseconds(5000)));
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
