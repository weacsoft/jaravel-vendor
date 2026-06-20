package com.weacsoft.jaravel.vendor.core;

/**
 * Facade 门面工具基类。
 * <p>
 * 模仿 Laravel 的 Facade：门面是静态代理，背后真正干活的是容器里解析出的实例。
 * 本类提供 {@link #resolve(Class)} 静态工具，供各具体门面在静态方法中解析被代理 Bean。
 * <pre>
 * public final class Auth {
 *     private static AuthManager inst() { return Facade.resolve(AuthManager.class); }
 *     public static boolean check() { return inst().check(); }
 * }
 * </pre>
 */
public final class Facade {

    private Facade() {
    }

    /**
     * 从 Spring 容器解析指定类型的 Bean。
     */
    public static <T> T resolve(Class<T> beanClass) {
        return SpringContext.bean(beanClass);
    }

    /**
     * 从 Spring 容器按名称解析 Bean。
     */
    public static <T> T resolve(String name, Class<T> beanClass) {
        return SpringContext.bean(name, beanClass);
    }
}
