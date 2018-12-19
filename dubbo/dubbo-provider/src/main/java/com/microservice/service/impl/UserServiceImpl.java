/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice.service.impl;

import com.alibaba.fastjson.JSON;
import com.microservice.service.Person;
import com.microservice.service.UserServiceBo;
import org.springframework.stereotype.Service;

/**
 *
 *
 * @author HaoBin
 * @version $Id: UserServiceImpl.java, v0.1 2018/12/19 16:48 HaoBin 
 */
@com.alibaba.dubbo.config.annotation.Service(interfaceClass = UserServiceBo.class, group = "dubbo", version = "1.0.0")
@Service
public class UserServiceImpl implements UserServiceBo {

    public String sayHello(String name) {
        //让当前当前线程休眠2s
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.println("accept name");
        return name;
    }

    public String testPojo(Person person) {
        return JSON.toJSONString(person);
    }
}