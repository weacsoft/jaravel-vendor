package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.RegisterGuard;
import com.weacsoft.jaravel.vendor.auth.RegisterProvider;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 认证配置注册器：在所有单例 Bean 初始化完成后统一执行全部认证注册。
 * <p>
 * 由 {@link AuthAutoConfiguration} 注册为 Bean，实现 {@link SmartInitializingSingleton}，
 * 在所有单例 Bean 就绪后执行。
 *
 * <h3>注册顺序</h3>
 * <ol>
 *   <li>注册提供者驱动（{@link UserProviderDriver}）</li>
 *   <li>注册配置式提供者（通过工厂驱动创建）</li>
 *   <li>扫描 {@link RegisterProvider @RegisterProvider} 注解方法，注册注解声明式提供者</li>
 *   <li>注册配置式守卫</li>
 *   <li>扫描 {@link RegisterGuard @RegisterGuard} 注解方法，注册注解声明式守卫（含默认守卫标记）</li>
 *   <li>注册守卫驱动（{@link AuthGuardDriver}）</li>
 * </ol>
 * 先注册 provider 再注册 guard，保证 guard 引用的 provider 已存在。
 * 注解声明优先于配置式（同名时覆盖）。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>不使用 {@code @Bean} 注册 guard / provider，因此名称不会与 Spring bean name 冲突</li>
 *   <li>方法参数从 Spring 容器按类型自动注入，行为与 {@code @Bean} 方法一致</li>
 *   <li>{@link RegisterGuard#defaultGuard()} 为 {@code true} 时，自动调用
 *       {@link AuthManager#setDefaultGuard(String)} 设置默认守卫</li>
 * </ul>
 */
public class AuthRegistrar implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(AuthRegistrar.class);

    private final AuthProperties properties;
    private final List<AuthGuardDriver> guardDrivers;
    private final List<UserProviderDriver> providerDrivers;
    private final AuthManager authManager;

    private ApplicationContext applicationContext;

    public AuthRegistrar(AuthProperties properties,
                         List<AuthGuardDriver> guardDrivers,
                         List<UserProviderDriver> providerDrivers,
                         AuthManager authManager) {
        this.properties = properties;
        this.guardDrivers = guardDrivers;
        this.providerDrivers = providerDrivers;
        this.authManager = authManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ConfigurableListableBeanFactory beanFactory =
                ((org.springframework.context.ConfigurableApplicationContext) applicationContext)
                        .getBeanFactory();

        // 1. 注册提供者驱动
        for (UserProviderDriver driver : providerDrivers) {
            authManager.registerProviderDriver(driver);
        }

        // 2. 配置式提供者注册（通过工厂驱动创建）
        if (properties.getProviders() != null) {
            properties.getProviders().forEach((name, cfg) -> {
                authManager.registerProvider(name, cfg.getDriver(), cfg.toConfigMap());
            });
        }

        // 3. 注解声明式提供者注册（@RegisterProvider，覆盖同名配置式）
        scanRegisterProvider(beanFactory);

        // 4. 配置式守卫注册
        if (properties.getGuards() != null) {
            properties.getGuards().forEach((name, cfg) -> {
                authManager.registerGuard(name, cfg.getDriver(), cfg.getProvider());
            });
        }

        // 5. 注解声明式守卫注册（@RegisterGuard，覆盖同名配置式，含默认守卫标记）
        scanRegisterGuard(beanFactory);

        // 6. 守卫驱动注册
        for (AuthGuardDriver driver : guardDrivers) {
            authManager.registerGuardDriver(driver);
        }
    }

    /**
     * 扫描所有 Bean 中标注 {@link RegisterProvider} 的方法，调用并注册到 {@link AuthManager}。
     */
    private void scanRegisterProvider(ConfigurableListableBeanFactory beanFactory) {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = getBeanType(beanName);
            if (beanType == null) continue;

            for (Method method : beanType.getMethods()) {
                RegisterProvider annotation = method.getAnnotation(RegisterProvider.class);
                if (annotation == null) continue;

                String providerName = annotation.value();
                Object result = invokeAnnotatedMethod(method, beanName, beanType, beanFactory,
                        "RegisterProvider");

                if (result instanceof UserProvider) {
                    authManager.registerProvider(providerName, (UserProvider) result);
                    logger.info("[auth] @RegisterProvider 注册 provider: name={}, type={}",
                            providerName, result.getClass().getSimpleName());
                } else {
                    logger.warn("[auth] @RegisterProvider 方法返回值不是 UserProvider: {}.{}()",
                            beanType.getSimpleName(), method.getName());
                }
            }
        }
    }

    /**
     * 扫描所有 Bean 中标注 {@link RegisterGuard} 的方法，调用并注册到 {@link AuthManager}。
     * 处理 {@link RegisterGuard#defaultGuard()} 标记。
     */
    private void scanRegisterGuard(ConfigurableListableBeanFactory beanFactory) {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = getBeanType(beanName);
            if (beanType == null) continue;

            for (Method method : beanType.getMethods()) {
                RegisterGuard annotation = method.getAnnotation(RegisterGuard.class);
                if (annotation == null) continue;

                String guardName = annotation.value();
                boolean isDefault = annotation.defaultGuard();
                Object result = invokeAnnotatedMethod(method, beanName, beanType, beanFactory,
                        "RegisterGuard");

                if (result instanceof GuardDefinition) {
                    GuardDefinition def = (GuardDefinition) result;
                    authManager.registerGuard(guardName, def.driver(), def.provider(), def.config());
                    logger.info("[auth] @RegisterGuard 注册 guard: name={}, driver={}, provider={}{}",
                            guardName, def.driver(), def.provider(),
                            isDefault ? " (默认)" : "");

                    // 处理默认守卫标记
                    if (isDefault) {
                        authManager.setDefaultGuard(guardName);
                        logger.info("[auth] @RegisterGuard 设置默认守卫: {}", guardName);
                    }
                } else {
                    logger.warn("[auth] @RegisterGuard 方法返回值不是 GuardDefinition: {}.{}()",
                            beanType.getSimpleName(), method.getName());
                }
            }
        }
    }

    /**
     * 获取 Bean 类型，异常或无法确定时返回 null。
     */
    private Class<?> getBeanType(String beanName) {
        try {
            return applicationContext.getType(beanName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射调用注解方法，自动从 Spring 容器按类型解析方法参数。
     */
    private Object invokeAnnotatedMethod(Method method, String beanName, Class<?> beanType,
                                          ConfigurableListableBeanFactory beanFactory,
                                          String annotationName) {
        Object bean = applicationContext.getBean(beanName);
        Object[] args = resolveArguments(method, beanFactory);

        try {
            method.setAccessible(true);
            return method.invoke(bean, args);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "调用 @" + annotationName + " 方法失败: " + beanType.getName()
                            + "." + method.getName() + "()", e);
        }
    }

    /**
     * 从 Spring 容器按类型解析方法参数。
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
