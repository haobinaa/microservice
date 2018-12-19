/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import com.microservice.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * @author HaoBin
 * @version $Id: ConsumerApp.java, v0.1 2018/12/19 18:12 HaoBin 
 */
@SpringBootApplication
@EnableDubboConfiguration
@RestController
@ComponentScan(basePackages = {"com.microservice.service"})
public class ConsumerApp {

    @Autowired
    private ConsumerService consumerService;


    @RequestMapping(value = "/testSayHello", method = RequestMethod.GET)
    String testSayHello(@RequestParam(value = "name", required = true) String name) {
        System.out.println(name);
        return  consumerService.sayHello(name);
    }

    @RequestMapping("/")
    String home() {
        return "Hello Demo!";
    }

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApp.class, args);
    }
}