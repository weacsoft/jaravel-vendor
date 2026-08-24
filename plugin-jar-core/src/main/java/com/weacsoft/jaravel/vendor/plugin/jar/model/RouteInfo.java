package com.weacsoft.jaravel.vendor.plugin.jar.model;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;

import java.util.Objects;

/**
 * 路由信息模型，描述一条插件路由的元数据。
 * <p>
 * 由插件扫描器从 {@link com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginMapping}
 * 注解中提取，传递给 {@link com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar} 进行注册。
 */
public class RouteInfo {

    /** 路由路径，如 /users/{id} */
    private String path;

    /** HTTP 方法 */
    private HttpMethod method;

    /** Bean 名称（控制器组件在 Spring 容器中的名称） */
    private String beanName;

    /** 处理方法名 */
    private String methodName;

    /** 响应内容类型，如 application/json */
    private String produces;

    public RouteInfo() {
    }

    /**
     * 全参构造。
     *
     * @param path       路由路径
     * @param method     HTTP 方法
     * @param beanName   Bean 名称
     * @param methodName 处理方法名
     * @param produces   响应内容类型
     */
    public RouteInfo(String path, HttpMethod method, String beanName, String methodName, String produces) {
        this.path = path;
        this.method = method;
        this.beanName = beanName;
        this.methodName = methodName;
        this.produces = produces;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteInfo routeInfo = (RouteInfo) o;
        return Objects.equals(path, routeInfo.path)
                && method == routeInfo.method
                && Objects.equals(beanName, routeInfo.beanName)
                && Objects.equals(methodName, routeInfo.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, method, beanName, methodName);
    }

    @Override
    public String toString() {
        return (method != null ? method.name() : "?") + " " + path + " -> " + beanName + "." + methodName + "()";
    }
}
