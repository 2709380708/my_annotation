package com.dfxy.controller;

import com.dfxy.annotation.DFAutowired;
import com.dfxy.annotation.DFController;
import com.dfxy.annotation.DFRequestMapping;
import com.dfxy.annotation.DFRequestParam;
import com.dfxy.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@DFController
@DFRequestMapping("/demo")
public class DemoAction {
    @DFAutowired
    private IDemoService demoService;
    @DFRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
                      @DFRequestParam("name") String name) {
        String result = demoService.get(name);
        try{
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
