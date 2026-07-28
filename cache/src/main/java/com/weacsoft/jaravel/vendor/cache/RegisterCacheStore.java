package com.weacsoft.jaravel.vendor.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册缓存 Store，替代 {@code @Bean} 方式（避免 bean name 冲突）。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回 {@link CacheStore}（或其子类型）。
 * {@link com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheStoreRegistrar CacheStoreRegistrar}
 * 会在所有 Bean 初始化完成后扫描此注解，调用方法并按 {@link #value()} 指定的名称注册到
 * {@link CacheManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean("admin")} 的 bean name 在整个 Spring 容器内必须唯一。如果另一处也有
 * {@code @Bean("admin")}（返回不同类型），Spring Boot 会抛出
 * {@code BeanDefinitionOverrideException}。使用本注解后，store 名称与 bean name 解耦，
 * 不会注册为 Spring Bean，因此不会与同名 bean 冲突。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class CacheConfig {
 *
 *     // 额外 store：名称 "file"，不会与名为 "file" 的其他 bean 冲突
 *     &#64;RegisterCacheStore("file")
 *     public CacheStore fileStore(CacheProperties properties) {
 *         return new DefaultCacheStore(new FileCacheDriver(properties.getFileDir()),
 *                 properties.getPrefix());
 *     }
 *
 *     // 默认 store：标记 defaultStore = true，自动设为默认 store
 *     &#64;RegisterCacheStore(value = "array", defaultStore = true)
 *     public DefaultCacheStore arrayStore() {
 *         return new DefaultCacheStore(new ArrayCacheDriver(), "jaravel");
 *     }
 * }
 * </pre>
 *
 * <h3>方法参数注入</h3>
 * 方法可声明任意参数，{@code CacheStoreRegistrar} 会从 Spring 容器中按类型自动解析注入，
 * 行为与 {@code @Bean} 方法的参数注入一致。
 *
 * <h3>与配置式的关系</h3>
 * 本注解注册的 store 优先级与 {@code @Bean} 方式相同：覆盖同名配置式 store
 * （在 {@code initFromConfig} 之后执行）。
 *
 * @see CacheManager#addStore(String, CacheStore)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterCacheStore {

    /**
     * Store 名称，用于 {@code Cache.store(name)} 或 {@code CacheManager.store(name)} 解析。
     *
     * @return store 名称
     */
    String value();

    /**
     * 是否设为默认 store。
     * <p>
     * 设为 {@code true} 时，等效于调用 {@link CacheManager#setDefaultStore(String)}，
     * 会覆盖 {@code jaravel.cache.default-store} 配置值。
     * <p>
     * 若多个 {@code @RegisterCacheStore} 同时标记 {@code defaultStore = true}，
     * 最后注册的生效。
     *
     * @return 是否为默认 store，默认 {@code false}
     */
    boolean defaultStore() default false;
}
