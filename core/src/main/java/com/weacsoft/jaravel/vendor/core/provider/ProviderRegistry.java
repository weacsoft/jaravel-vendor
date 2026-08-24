package com.weacsoft.jaravel.vendor.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务提供者注册器。
 * <p>
 * 收集容器中所有 {@link ServiceProvider}，在所有单例 Bean 初始化完成后，
 * 先统一执行 {@code register()}，再统一执行 {@code boot()}，
 * 模仿 Laravel 的两阶段引导。
 */
@Component
public class ProviderRegistry implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final List<ServiceProvider> providers;

    @Autowired
    public ProviderRegistry(List<ServiceProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // register 阶段
        for (ServiceProvider p : providers) {
            try {
                p.register();
            } catch (Exception e) {
                log.error("ServiceProvider[{}] register 失败", p.getClass().getSimpleName(), e);
            }
        }
        // boot 阶段
        for (ServiceProvider p : providers) {
            try {
                p.boot();
            } catch (Exception e) {
                log.error("ServiceProvider[{}] boot 失败", p.getClass().getSimpleName(), e);
            }
        }
        log.info("Jaravel 服务提供者引导完成，共 {} 个", providers.size());
    }
}
