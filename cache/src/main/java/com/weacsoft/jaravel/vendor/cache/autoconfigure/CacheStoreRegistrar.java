package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.RegisterCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 扫描 {@link RegisterCacheStore} 注解方法，调用并注册到 {@link CacheManager}。
 * <p>
 * 在所有单例 Bean 初始化完成后执行（{@link SmartInitializingSingleton}），
 * 遍历容器中所有 Bean，查找标注了 {@link RegisterCacheStore} 的方法，
 * 从 Spring 容器按类型解析方法参数后反射调用，将返回的 {@link CacheStore}
 * 按 {@link RegisterCacheStore#value()} 指定的名称注册到 {@link CacheManager}。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>不使用 {@code @Bean} 注册，因此 store 名称不会与 Spring bean name 冲突</li>
 *   <li>方法参数从 Spring 容器按类型自动注入，行为与 {@code @Bean} 方法一致</li>
 *   <li>注册时机在 {@code CacheManager.initFromConfig} 之后，因此覆盖同名配置式 store</li>
 * </ul>
 */
public class CacheStoreRegistrar implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(CacheStoreRegistrar.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        ConfigurableListableBeanFactory beanFactory =
                ((org.springframework.context.ConfigurableApplicationContext) applicationContext)
                        .getBeanFactory();

        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType;
            try {
                beanType = applicationContext.getType(beanName);
            } catch (Exception e) {
                continue;
            }
            if (beanType == null) {
                continue;
            }

            // 扫描该 Bean 的所有方法（含继承的 public 方法）
            for (Method method : beanType.getMethods()) {
                RegisterCacheStore annotation = method.getAnnotation(RegisterCacheStore.class);
                if (annotation == null) {
                    continue;
                }

                String storeName = annotation.value();
                boolean isDefault = annotation.defaultStore();
                Object bean = applicationContext.getBean(beanName);

                // 解析方法参数（从 Spring 容器按类型注入）
                Object[] args = resolveArguments(method, beanFactory);

                try {
                    method.setAccessible(true);
                    Object result = method.invoke(bean, args);

                    if (result instanceof CacheStore) {
                        cacheManager.addStore(storeName, (CacheStore) result);
                        logger.info("[cache] @RegisterCacheStore 注册 store: name={}, type={}{}",
                                storeName, result.getClass().getSimpleName(),
                                isDefault ? " (默认)" : "");

                        // 处理默认 store 标记
                        if (isDefault) {
                            cacheManager.setDefaultStore(storeName);
                            logger.info("[cache] @RegisterCacheStore 设置默认 store: {}", storeName);
                        }
                    } else {
                        logger.warn("[cache] @RegisterCacheStore 方法返回值不是 CacheStore: {}.{}()",
                                beanType.getSimpleName(), method.getName());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "调用 @RegisterCacheStore 方法失败: " + beanType.getName()
                                    + "." + method.getName() + "()", e);
                }
            }
        }
    }

    /**
     * 从 Spring 容器按类型解析方法参数。
     *
     * @param method      目标方法
     * @param beanFactory Bean 工厂
     * @return 参数值数组
     */
    private Object[] resolveArguments(Method method, ConfigurableListableBeanFactory beanFactory) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = beanFactory.getBean(paramTypes[i]);
        }
        return args;
    }
}
