package com.cloud.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.exception.HystrixBadRequestException;

/**
 * @Author HaoBin
 * @Create 2020/3/18 17:33
 * @Description: 触发 fallback 测试(command execute 失败后走的逻辑)
 **/
public class FallbackHystrixCommand extends HystrixCommand<String> {
    private final String name;


    public FallbackHystrixCommand(String name) {
        super(HystrixCommandGroupKey.Factory.asKey("fallback-group"));
        this.name = name;
    }

    @Override
    protected String run() throws Exception {
        hystrixBadRequestException();
        return name + ",fallback. thread:" + Thread.currentThread().getName();
    }

    /**
     * 超时异常， 触发fallback
     */
    private void timeOutException() throws Exception {
        int j = 0;
        while (true) {
            j++;
        }
    }

    /**
     * 模拟业务代码异常， 触发 fallback
     */
    private void businessException() {
        int i = 1/0;
    }

    /**
     * 主动抛出异常， 触发 fallback
     */
    private void throwException() {
        throw new RuntimeException("make exception");
    }


    /**
     * 捕获异常， 不会触发 fallback
     */
    private void catchException() {
        try {
            throw new RuntimeException("no-fallback");
        } catch (Exception e) {
            System.out.println("catch exception:" + e.getMessage());
        }
    }

    /**
     * HystrixBadRequestException 由系统错误或非法参数引起的，不会触发 fallback
     */
    private void hystrixBadRequestException() {
        throw new HystrixBadRequestException("HystrixBadRequestException not trigger fallback");
    }

    @Override
    protected String getFallback() {
        System.out.println("fallback");
        return "fallback:" + name;
    }
}
