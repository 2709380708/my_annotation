package com.dfxy.service.impl;
import com.dfxy.annotation.DFService;
import com.dfxy.service.IDemoService;

@DFService
public class DemoSerevice implements IDemoService {
    public String get(String name) {
        return "My name is "+name;
    }
}