package com.cloud.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * @Description TODO
 * @Date 2020/6/14 4:11 下午
 * @Created by leobhao
 */
public class MultiFallback1 extends HystrixCommand<String> {


    private final boolean throwException;

    public MultiFallback1(boolean throwException) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.throwException = throwException;
    }

    @Override
    protected String run() {
        if (throwException) {
            throw new RuntimeException("failure from CommandThatFailsFast");
        } else {
            return "success";
        }
    }

    @Override
    protected String getFallback() {
        return new MultiFallback2(true).execute();
    }
}
