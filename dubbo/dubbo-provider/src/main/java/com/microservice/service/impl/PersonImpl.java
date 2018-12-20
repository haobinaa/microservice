/**
 * BrandBigData.com Inc. Copyright (c) 2018 All Rights Reserved.
 */
package com.microservice.service.impl;

import com.microservice.service.Person;

/**
 *
 *
 * @author HaoBin
 * @version $Id: PersonImpl.java, v0.1 2018/12/20 15:35 HaoBin 
 */
public class PersonImpl implements Person {
    private String name;
    private String password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}