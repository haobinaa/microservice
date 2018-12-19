/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * @author HaoBin
 * @version $Id: ProviderApp.java, v0.1 2018/12/19 17:44 HaoBin 
 */
@SpringBootApplication
@EnableDubboConfiguration
@RestController
@ComponentScan(basePackages = {"com.microservice.service"})
public class ProviderApp {

    @RequestMapping("/")
    String home() {
        return "hello world!";
    }


    public static void main(String[] args) {
        SpringApplication.run(ProviderApp.class, args);
    }
}