package com.weacsoft.jaravel.vendor.http.middleware;

/**
 * 类中间件规格，封装 {@code Class} 对象和参数数组。
 * <p>
 * 当路由通过 {@code middleware(AuthMiddleware.class, "api", "admin")} 引用中间件时，
 * 系统将 Class 和参数封装为本对象存入中间件规格列表，解析时通过
 * {@link MiddlewareAliasRegistry#resolve(Class, String...)} 查找对应实例。
 * <p>
 * 本类为内部数据载体，无需用户直接构造。
 *
 * @see MiddlewareAliasRegistry#resolve(Class, String...)
 */
public class ClassMiddlewareSpec {

    private final Class<?> clazz;
    private final String[] params;

    /**
     * 构造类中间件规格。
     *
     * @param clazz  中间件类（必须实现 {@link Middleware}）
     * @param params 中间件参数（来自路由调用的可变参数）
     */
    public ClassMiddlewareSpec(Class<?> clazz, String... params) {
        this.clazz = clazz;
        this.params = params != null ? params : new String[0];
    }

    /**
     * @return 中间件类
     */
    public Class<?> getClazz() {
        return clazz;
    }

    /**
     * @return 参数数组（非 null）
     */
    public String[] getParams() {
        return params;
    }
}
