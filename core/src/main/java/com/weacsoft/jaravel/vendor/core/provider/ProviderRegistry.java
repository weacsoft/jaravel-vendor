package com.weacsoft.jaravel.vendor.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 服务提供者注册器（零 Spring 依赖）。
 * <p>
 * 收集所有 {@link ServiceProvider}，执行两阶段引导：
 * 先统一执行 {@code register()}，再统一执行 {@code boot()}，
 * 模仿 Laravel 的两阶段引导。
 * <p>
 * <h3>P3 解耦说明</h3>
 * 引导时机由宿主控制：Spring 宿主在 Bean 就绪后（{@code SmartInitializingSingleton} 包装）
 * 调用 {@link #boot()}；非 Spring 宿自主动调用。
 *
 */
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final List<ServiceProvider> providers;

    public ProviderRegistry(List<ServiceProvider> providers) {
        if (providers == null) {
            throw new IllegalArgumentException("providers 不能为 null（可为空列表）");
        }
        this.providers = providers;
    }

    /**
     * 执行两阶段引导（宿主在单例就绪后调用）。
     * 单个 provider 的 register/boot 失败不阻断其余 provider。
     */
    public void boot() {
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
