package com.weacsoft.jaravel.vendor.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 代码级配置自动注册器。
 * <p>
 * 在所有单例 Bean 初始化完成后，自动发现容器中所有 {@link ConfigDefinition} Bean，
 * 逐个注册到 {@link ConfigRepository}，对齐 Laravel 在引导阶段加载 config/*.php 的行为。
 * <p>
 * 优先级：运行时覆盖 > 代码级配置(ConfigDefinition) > Spring Environment(yml)。
 */
@Component
public class ConfigDefinitionRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ConfigDefinitionRegistrar.class);

    @Autowired
    private ConfigRepository configRepository;

    /** 容器中所有 ConfigDefinition Bean，可能为空（用户未定义任何代码级配置） */
    @Autowired(required = false)
    private List<ConfigDefinition> definitions;

    @Override
    public void afterSingletonsInstantiated() {
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
