package com.weacsoft.jaravel.vendor.core.provider;

/**
 * 服务提供者基类，对齐 Laravel Service Provider。
 * <p>
 * 在 Spring 中以 {@code @Component} 注册，容器刷新时由 {@link ProviderRegistry}
 * 依次调用 {@link #register()}（注册阶段）与 {@link #boot()}（启动阶段）。
 * <pre>
 * &#64;Component
 * public class AppServiceProvider extends ServiceProvider {
 *     public void register() { /* 绑定轻量服务 *\/ }
 *     public void boot() { /* 注册事件监听、配置回调 *\/ }
 * }
 * </pre>
 */
public abstract class ServiceProvider {

    /**
     * 注册阶段：用于注册/绑定服务，此时其它 Bean 可能尚未就绪。
     */
    public void register() {
    }

    /**
     * 启动阶段：所有 Bean 就绪后执行，可安全注入并使用其它服务。
     */
    public void boot() {
    }
}
