package com.dfxy.servlet;

import com.dfxy.annotation.DFAutowired;
import com.dfxy.annotation.DFController;
import com.dfxy.annotation.DFRequestMapping;
import com.dfxy.annotation.DFService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

@WebServlet(name = "DFDispatchServlet", value = "/DFDispatchServlet")
public class DFDispatchServlet extends HttpServlet {
    //保存application.properties配置文件的内容
    private Properties contextConfig = new Properties();
    //保存扫描的所有类名
    private List<String> classNames = new ArrayList<>();
    //ioc容器，用于保存扫描到的类
    private Map<String, Object> ioc = new HashMap<String, Object>();
    //保存url和Method的对应关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //初始化扫描到的类，并存入Ioc容器
        doInstance();
        //依赖注入
        doAutowired();
        //初始化HandlerMapping，实现url与访问方法的匹配
        initHandlerMapping();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse
            response) throws ServletException, IOException {
        this.doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse
            response) throws ServletException, IOException {
        try {
            doDispatch(request, response);
        } catch (Exception e) {
            response.getWriter().write("500 Exception " +
                    Arrays.toString(e.getStackTrace()));
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream fis =
                this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackage) {

        URL url =
                this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        System.out.println("++++++++++++++++:"+ url.getFile());
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className =
                        (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(className);
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(DFController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(DFService.class)) {
                    DFService service = clazz.getAnnotation(DFService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The “" + i.getName() + "” is exists!! ");
                        }
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(DFAutowired.class)) {
                    continue;
                }
                DFAutowired autowired = field.getAnnotation(DFAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));//TODO
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(DFController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(DFRequestMapping.class)) {
                DFRequestMapping requestMapping =
                        clazz.getAnnotation(DFRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(DFRequestMapping.class)) {
                    continue;
                }
                DFRequestMapping requestMapping =
                        method.getAnnotation(DFRequestMapping.class);
                String url =
                        ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest request, HttpServletResponse
            response) throws Exception {
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!this.handlerMapping.containsKey(url)) {
            response.getWriter().write("404 not found!!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        Map<String, String[]> params = request.getParameterMap();
        String beanName =
                toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), new Object[]
                {request, response, params.get("name")[0]});
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}

