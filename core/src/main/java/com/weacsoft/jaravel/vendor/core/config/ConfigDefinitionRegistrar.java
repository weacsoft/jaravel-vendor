package com.weacsoft.jaravel.vendor.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 代码级配置自动注册器（零 Spring 依赖）。
 * <p>
 * 在宿主 Bean 就绪后（Spring 宿主经 {@code SmartInitializingSingleton} 包装调用 {@link #boot()}），
 * 把收集的 {@link ConfigDefinition} 逐个注册到 {@link ConfigRepository}，
 * 对齐 Laravel 在引导阶段加载 config/*.php 的行为。
 * <p>
 * 优先级：运行时覆盖 &gt; 代码级配置(ConfigDefinition) &gt; 宿主外部配置(yml)。
 */
public class ConfigDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ConfigDefinitionRegistrar.class);

    private final ConfigRepository configRepository;

    /** 待注册的代码级配置，可能为空（用户未定义任何代码级配置） */
    private List<ConfigDefinition> definitions;

    public ConfigDefinitionRegistrar(ConfigRepository configRepository) {
        if (configRepository == null) {
            throw new IllegalArgumentException("ConfigRepository 不能为 null");
        }
        this.configRepository = configRepository;
        this.definitions = List.of();
    }

    /**
     * 设置待注册的代码级配置定义（宿主注入；可为 null 表示为空）。
     *
     * @param definitions ConfigDefinition 列表
     */
    public void setDefinitions(List<ConfigDefinition> definitions) {
        this.definitions = definitions == null ? List.of() : definitions;
    }

    /**
     * 执行代码级配置加载（宿主在单例就绪后调用）。
     */
    public void boot() {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (ConfigDefinition def : definitions) {
            try {
                configRepository.registerConfigDefinition(def);
                log.debug("已注册代码级配置 namespace={}", def.namespace());
            } catch (Exception e) {
                log.error("注册代码级配置失败 namespace={}", def.namespace(), e);
            }
        }
        log.info("Jaravel 代码级配置加载完成，共 {} 个", definitions.size());
    }
}
