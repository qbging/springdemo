package com.ggg.servlet;

import com.ggg.annotation.GGGAutowired;
import com.ggg.annotation.GGGController;
import com.ggg.annotation.GGGRequestMapping;
import com.ggg.annotation.GGGService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class gggDispatherServlet extends HttpServlet{

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String, Object>();

    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    @Override
    public void destroy() {
        System.out.println("退出。。dd。。。。。。。。。。。。。。");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("工作、、。。。。。。。。。。。。。");
        //6等待请求
        doDispather(req,resp);
    }

    private void doDispather(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Fount");
            return;
        }
        Method m = handlerMapping.get(url);
        Class clazz = m.getDeclaringClass();
        String beanName = lowerFirstCase(clazz.getSimpleName());
        try {

            m.invoke((Object) ioc.get(beanName),null);
        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println(m);

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("Spring开始。。。。。。。。。。。。。。。。。");
        //1加载配置文件
        doLoadConfig(config.getInitParameter("applicationContext"));
        //2扫描所有相关类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3初始化扫描到的类
        doInstance();
        //4实现依赖注入
        doAutowired();
        //5初始化HandlerMapping
        initHandlerMapping();
        System.out.println("Spring开始。。。。。。。。。。。。。。。。。");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()){return;}

        //对controller进行处理
        for (Map.Entry entry :ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(GGGController.class)){continue;}

            String baseUrl = "";
            if (clazz.isAnnotationPresent(GGGRequestMapping.class)){
                GGGRequestMapping classMapping = clazz.getAnnotation(GGGRequestMapping.class);
                baseUrl = classMapping.value();
            }

            Method[] methods = clazz.getMethods();
            for (Method method :methods) {
                if (method.isAnnotationPresent(GGGRequestMapping.class)){
                    GGGRequestMapping methodMapping = method.getAnnotation(GGGRequestMapping.class);
                    String url = methodMapping.value();
                    url = baseUrl + url;
                    handlerMapping.put(url, method);
                    System.out.println("Mapped : "+url +","+method);
                }
            }

        }

    }

    private void doAutowired() {
        if (ioc.isEmpty()){return;}

        //对autowired注入对象处理
        for (Map.Entry entry: ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields) {
                //只要是加了autowired的字段都实施强吻策略
                if(!field.isAnnotationPresent(GGGAutowired.class)){continue;}

                GGGAutowired autowired = field.getAnnotation(GGGAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                //强吻授权
                field.setAccessible(true);
                //
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {return;}
        try {
            for (String className :classNames) {
                Class<?> clazz = Class.forName(className);
                //有注解的类才初始化
                if (clazz.isAnnotationPresent(GGGController.class)){
                    Object obj = clazz.newInstance();
                    //初始化后放到ioc
                    //默认类的首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,obj);
                }else if (clazz.isAnnotationPresent(GGGService.class)){
                    GGGService service = clazz.getAnnotation(GGGService.class);
                    String beanName = service.value();
                    if ("".equals(beanName)){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    //解决子类引用赋值给父类问题，如：
                    // 实现类实现多个接口，则ioc也要存接口引用全名为key，实现类的实例为value的entry
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i: interfaces) {
                        ioc.put(i.getName(),instance);
                    }
                } else{
                    //不是注解的忽略
                    continue;
                }
            }
        } catch (Exception e) {
        e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file:classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage+"."+file.getName());
            }else {
                String className = scanPackage+"."+file.getName().replace(".class","");
                classNames.add(className);
            }
        }
        System.out.println("");
    }

    private void doLoadConfig(String config) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(config);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0]+=32;
        return  String.valueOf(chars);
    }

}
