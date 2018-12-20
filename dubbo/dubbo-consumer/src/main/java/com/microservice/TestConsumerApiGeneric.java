/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.fastjson.JSON;
import java.util.HashMap;
import java.util.Map;

/**
 * 泛化接口调用消费方
 *
 * @author HaoBin
 * @version $Id: TestConsumerApiGeneric.java, v0.1 2018/12/19 19:23 HaoBin 
 */
public class TestConsumerApiGeneric {

    public static void main(String[] args) {
        // 当前应用配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName("dubboConsumer");

        // 连接注册中心配置
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("118.24.72.64:2181");
        registry.setProtocol("zookeeper");

        // 泛型参数设置为GenericService
        ReferenceConfig<GenericService> reference = new ReferenceConfig<GenericService>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setVersion("1.0.0");
        reference.setGroup("dubbo");
        reference.setTimeout(3000);

        //设置为泛化
        reference.setInterface("com.microservice.service.UserServiceBo");
        reference.setGeneric(true);

        //用com.alibaba.dubbo.rpc.service.GenericService替代所有接口引用
        GenericService userService = reference.get(); //

        // 基本类型以及Date,List,Map等不需要转换，直接调用,如果返回值为POJO也将自动转成Map
        Object result = userService.$invoke("sayHello", new String[] {"java.lang.String"},
                new Object[] {"哈哈哈"});

        System.out.println(JSON.toJSON(result));
        //POJO参数转换为map
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("class", "com.microservice.service.impl.PersonImpl");
        map.put("name", "jiaduo");
        map.put("password", "password");

        result = userService.$invoke("testPojo", new String[] { "com.microservice.service.Person" }, new Object[] { map
        });
        System.out.println("result is ================================");
        System.out.println((result));
    }

}