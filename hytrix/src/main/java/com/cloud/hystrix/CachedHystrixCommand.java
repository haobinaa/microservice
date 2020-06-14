package com.cloud.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixRequestCache;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;

/**
 * @Description request cache HystrixCommand 使用
 * @Date 2020/6/14 12:16 下午
 * @Created by leobhao
 */
public class CachedHystrixCommand extends HystrixCommand<String> {

    private String key;

    public static final HystrixCommandKey COMMAND_KEY = HystrixCommandKey.Factory.asKey("cached-command");

    public CachedHystrixCommand(String key) {
        super(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("cache-group"))
                .andCommandKey(COMMAND_KEY)
        );
        this.key = key;
    }

    @Override
    protected String run() throws Exception {
        return "hello " + key;
    }

    /**
     * 覆盖 getCacheKey， 指定缓存的 key. 来开启缓存
     */
    @Override
    protected String getCacheKey() {
        return this.key;
    }

    /**
     * 清除上下文缓存
     * @param key
     */
    public static void flushCache(String key) {
        HystrixRequestCache.getInstance(COMMAND_KEY,
                HystrixConcurrencyStrategyDefault.getInstance()).clear(key);
    }
}
