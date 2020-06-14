package com.cloud.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * @Description 多级 fallback, 适用场景： fallback 等于备用方案， 备用方案也有失效的时候， hystrix 提供多级 fallback
 * 其实就是:
 * 1. HystrixCommand1执行fallback1， fallback1的执行嵌入HystrixCommand2
 * 2. 当HystrixCommand2执行失败的时候，触发HystrixCommand2的fallback2，以此循环下去实现多级fallback，暂未上限，只要你的方法栈撑的起
 *
 * @Date 2020/6/14 4:02 下午
 * @Created by leobhao
 */
public class MultiFallback2 extends HystrixCommand<String> {

    private final boolean throwException;

    public MultiFallback2(boolean throwException) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.throwException = throwException;
    }

    @Override
    protected String run() {
        if (throwException) {
            throw new RuntimeException("failure from CommandThatFailsFast");
        } else {
            return "I'm fallback1";
        }
    }

    @Override
    protected String getFallback() {
        return "I'm fallback2";
    }
}
