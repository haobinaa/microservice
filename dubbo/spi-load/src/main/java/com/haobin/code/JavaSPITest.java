/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.haobin.code;

import com.haobin.code.service.Robot;
import java.util.ServiceLoader;

/**
 *
 *
 * @author HaoBin
 * @version $Id: JavaSPITest.java, v0.1 2019/3/18 23:54 HaoBin 
 */
public class JavaSPITest {

    public static void main(String[] args) {
        ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
        System.out.println("Java SPI");
        serviceLoader.forEach(Robot::sayHello);
    }
}