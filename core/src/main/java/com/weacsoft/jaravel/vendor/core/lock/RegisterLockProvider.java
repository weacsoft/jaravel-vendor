package com.weacsoft.jaravel.vendor.core.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册分布式锁提供者，替代 {@code @Bean} 方式（避免 bean name 冲突）。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回 {@link LockProvider} 实例。
 * {@link LockProviderRegistrar} 会在所有 Bean 初始化完成后扫描此注解，调用方法并按
 * {@link #value()} 指定的名称注册到 {@link LockProviderManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean} 的 bean name 在整个 Spring 容器内必须唯一。多个模块（如 schedule、redis）
 * 各自注册 LockProvider 时，容易与业务 Bean 产生同名冲突。使用本注解后，
 * provider 名称与 bean name 解耦，不会注册为 Spring Bean。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class MyLockConfig {
 *
 *     &#64;RegisterLockProvider("redis", defaultProvider = true)
 *     public LockProvider redisLock(RedisManager redis) {
 *         return new RedisLockProviderImpl(redis, "default");
 *     }
 * }
 * </pre>
 *
 * @see LockProvider
 * @see LockProviderManager
 * @see LockProviderRegistrar
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterLockProvider {

    /**
     * Provider 名称，用于 {@code LockProviderManager.provider(name)} 解析。
     */
    String value();

    /**
     * 是否设为默认 provider。
     * <p>
     * 设为 {@code true} 时，等效于调用 {@link LockProviderManager#setDefaultProvider(String)}，
     * 会覆盖已有默认值。
     * <p>
     * 若多个 {@code @RegisterLockProvider} 同时标记 {@code defaultProvider = true}，
     * 最后注册的生效。
     *
     * @return 是否为默认 provider，默认 {@code false}
     */
    boolean defaultProvider() default false;
}