/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice.service;

import com.alibaba.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Service;

/**
 *
 *
 * @author HaoBin
 * @version $Id: ConsumerService.java, v0.1 2018/12/19 18:14 HaoBin 
 */
@Service
public class ConsumerService {
    @Reference(group = "dubbo", interfaceClass = UserServiceBo.class, version = "1.0.0")
    private UserServiceBo userService;

    public String sayHello(String string) {
        return userService.sayHello(string);
    }

}