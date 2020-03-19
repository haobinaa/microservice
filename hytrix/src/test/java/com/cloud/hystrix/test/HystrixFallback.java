package com.cloud.hystrix.test;

import com.cloud.hystrix.FallbackHystrixCommand;
import org.junit.Test;

/**
 * @Author HaoBin
 * @Create 2020/3/18 17:30
 * @Description: 降级
 **/
public class HystrixFallback {


    @Test
    public void fallback() {
        new FallbackHystrixCommand("hello-fallback").execute();
    }
}
