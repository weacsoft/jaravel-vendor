package com.weacsoft.jaravel.vendor.springboot.cache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.RegisterCacheStore;
import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterCacheStore} 注解方法，调用并注册到 {@link CacheManager}。
 * <p>
 * 继承 {@link AnnotationDrivenRegistrar}，在所有单例 Bean 初始化完成后执行扫描，
 * 从 Spring 容器按类型解析方法参数后反射调用，将返回的 {@link CacheStore}
 * 按 {@link RegisterCacheStore#value()} 指定的名称注册到 {@link CacheManager}。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>产物不注册为 {@code @Bean}，因此 store 名称不会与 Spring bean name 冲突</li>
 *   <li>方法参数从 Spring 容器按类型自动注入，行为与 {@code @Bean} 方法一致</li>
 *   <li>注册时机在 {@code CacheManager.initFromConfig} 之后，因此覆盖同名配置式 store</li>
 * </ul>
 */
public class CacheStoreRegistrar extends AnnotationDrivenRegistrar<RegisterCacheStore> {

    private final CacheManager cacheManager;

    public CacheStoreRegistrar(CacheManager cacheManager) {
        super(RegisterCacheStore.class);
        this.cacheManager = cacheManager;
    }

    /**
     * 登记 {@link RegisterCacheStore @RegisterCacheStore} 方法返回的缓存 store。
     */
    @Override
    protected void register(Object result, Method method, RegisterCacheStore annotation) {
        CacheStore store = requireType(result, CacheStore.class, method);
        String storeName = annotation.value();

        cacheManager.addStore(storeName, store);
        log.info("[cache] @RegisterCacheStore 注册 store: name={}, type={}{}",
                storeName, store.getClass().getSimpleName(),
                annotation.defaultStore() ? " (默认)" : "");

        if (annotation.defaultStore()) {
            cacheManager.setDefaultStore(storeName);
            log.info("[cache] @RegisterCacheStore 设置默认 store: {}", storeName);
        }
    }
}
