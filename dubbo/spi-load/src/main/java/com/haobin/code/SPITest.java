/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.haobin.code;


import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.haobin.code.service.Robot;
import java.util.ServiceLoader;

/**
 * java 中 SPI 机制
 *
 * @author HaoBin
 * @version $Id: JavaSPITest.java, v0.1 2019/3/18 23:54 HaoBin 
 */
public class SPITest {

    public static void main(String[] args) {
        dubboSPI();
    }

    private static void javaSPI() {
        ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
        System.out.println("Java SPI");
        serviceLoader.forEach((robot -> robot.sayHello()));
    }

    private static void dubboSPI() {
        ExtensionLoader<Robot> extensionLoader = ExtensionLoader.getExtensionLoader(Robot.class);
        Robot optimusPrime = extensionLoader.getExtension("zkconfig");
        optimusPrime.sayHello();
    }
}