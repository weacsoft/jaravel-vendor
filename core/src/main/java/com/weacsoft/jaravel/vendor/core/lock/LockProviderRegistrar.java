package com.weacsoft.jaravel.vendor.core.lock;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterLockProvider} 注解方法，调用并注册到 {@link LockProviderManager}（零 Spring 依赖）。
 * <p>
 * 继承 {@link AnnotationDrivenRegistrar}，宿主在 Bean 就绪后调用 {@link #scan()} 执行扫描，
 * 从宿主容器按类型解析方法参数后反射调用，将返回的 {@link LockProvider}
 * 按 {@link RegisterLockProvider#value()} 指定的名称注册到 {@link LockProviderManager}。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>产物不注册为 {@code @Bean}，因此 provider 名称不会与 Spring bean name 冲突</li>
 *   <li>方法参数从宿主容器按类型自动注入，行为与宿主 {@code @Bean} 方法一致</li>
 *   <li>定义于 core 模块，由各业务模块使用</li>
 * </ul>
 *
 * @see RegisterLockProvider
 * @see LockProviderManager
 */
public class LockProviderRegistrar extends AnnotationDrivenRegistrar<RegisterLockProvider> {

    private final LockProviderManager lockProviderManager;

    public LockProviderRegistrar(LockProviderManager lockProviderManager) {
        super(RegisterLockProvider.class);
        this.lockProviderManager = lockProviderManager;
    }

    /**
     * 登记 {@link RegisterLockProvider @RegisterLockProvider} 方法返回的锁提供者。
     */
    @Override
    protected void register(Object result, Method method, RegisterLockProvider annotation) {
        LockProvider provider = requireType(result, LockProvider.class, method);
        String name = annotation.value();

        lockProviderManager.addProvider(name, provider);
        log.info("[lock] @RegisterLockProvider 注册 provider: name={}, type={}{}",
                name, provider.getClass().getSimpleName(),
                annotation.defaultProvider() ? " (默认)" : "");

        if (annotation.defaultProvider()) {
            lockProviderManager.setDefaultProvider(name);
            log.info("[lock] @RegisterLockProvider 设置默认 provider: {}", name);
        }
    }
}