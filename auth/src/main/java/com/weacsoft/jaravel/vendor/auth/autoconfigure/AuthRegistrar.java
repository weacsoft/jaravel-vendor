package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.RegisterGuard;
import com.weacsoft.jaravel.vendor.auth.RegisterProvider;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.core.registrar.AnnotationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

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
        AnnotationScanner scanner = new AnnotationScanner(applicationContext);

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
        scanRegisterProvider(scanner);

        // 4. 配置式守卫注册
        if (properties.getGuards() != null) {
            properties.getGuards().forEach((name, cfg) -> {
                // 兜底：写了 guards 但没写 driver，使用最基础的 session 守卫保证功能可用
                String driver = (cfg.getDriver() == null || cfg.getDriver().isBlank())
                        ? "session" : cfg.getDriver();
                authManager.registerGuard(name, driver, cfg.getProvider());
            });
        }

        // 5. 注解声明式守卫注册（@RegisterGuard，覆盖同名配置式，含默认守卫标记）
        scanRegisterGuard(scanner);

        // 6. 守卫驱动注册
        for (AuthGuardDriver driver : guardDrivers) {
            authManager.registerGuardDriver(driver);
        }
    }

    /**
     * 扫描所有 Bean 中标注 {@link RegisterProvider} 的方法，调用并注册到 {@link AuthManager}。
     */
    private void scanRegisterProvider(AnnotationScanner scanner) {
        scanner.scan(RegisterProvider.class, (result, annotation, method) -> {
            String providerName = annotation.value();
            if (result instanceof UserProvider) {
                authManager.registerProvider(providerName, (UserProvider) result);
                logger.info("[auth] @RegisterProvider 注册 provider: name={}, type={}",
                        providerName, result.getClass().getSimpleName());
            } else {
                logger.warn("[auth] @RegisterProvider 方法返回值不是 UserProvider: {}.{}()",
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
        });
    }

    /**
     * 扫描所有 Bean 中标注 {@link RegisterGuard} 的方法，调用并注册到 {@link AuthManager}。
     * 处理 {@link RegisterGuard#defaultGuard()} 标记。
     */
    private void scanRegisterGuard(AnnotationScanner scanner) {
        scanner.scan(RegisterGuard.class, (result, annotation, method) -> {
            String guardName = annotation.value();
            boolean isDefault = annotation.defaultGuard();

            if (result instanceof GuardDefinition def) {
                authManager.registerGuard(guardName, def.driver(), def.provider(), def.config());
                logger.info("[auth] @RegisterGuard 注册 guard: name={}, driver={}, provider={}{}",
                        guardName, def.driver(), def.provider(), isDefault ? " (默认)" : "");

                // 处理默认守卫标记
                if (isDefault) {
                    authManager.setDefaultGuard(guardName);
                    logger.info("[auth] @RegisterGuard 设置默认守卫: {}", guardName);
                }
            } else {
                logger.warn("[auth] @RegisterGuard 方法返回值不是 GuardDefinition: {}.{}()",
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
        });
    }
}
