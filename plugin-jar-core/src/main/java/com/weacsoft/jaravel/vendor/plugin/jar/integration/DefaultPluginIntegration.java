package com.weacsoft.jaravel.vendor.plugin.jar.integration;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 默认的 Jaravel 集成实现：空操作。
 * <p>
 * 当 jaravel 不可用（仅使用 plugin-jar-core 而未引入 jaravel starter）时使用此实现。
 * 所有回调均为空操作，共享包前缀与额外共享 JAR 返回空集合。
 * <p>
 * 实现 {@link EnvironmentAware} 以便 {@link #getConfigValue} 从 Spring Environment 读取配置。
 */
public class DefaultPluginIntegration implements PluginIntegration, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onPluginEnabled(String pluginId, ConfigurableApplicationContext context) {
        // 空操作
    }

    @Override
    public void onPluginDisabled(String pluginId) {
        // 空操作
    }

    @Override
    public Set<String> getSharedPackagePrefixes() {
        return Collections.emptySet();
    }

    @Override
    public List<java.nio.file.Path> getAdditionalSharedJars() {
        return Collections.emptyList();
    }

    @Override
    public Object createPluginRequest(jakarta.servlet.http.HttpServletRequest servletRequest) {
        // jaravel 不可用，返回 null 表示使用 HttpServletRequest
        return null;
    }

    @Override
    public boolean writePluginResponse(Object result, jakarta.servlet.http.HttpServletResponse servletResponse, String produces) {
        // jaravel 不可用，返回 false 表示使用默认写入逻辑
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        if (environment == null) {
            return defaultValue;
        }
        Class<?> clazz = defaultValue.getClass();
        if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) Boolean.valueOf(environment.getProperty(key, String.valueOf(defaultValue)));
        }
        if (clazz == Integer.class || clazz == int.class) {
            return (T) Integer.valueOf(environment.getProperty(key, String.valueOf(defaultValue)));
        }
        if (clazz == String.class) {
            return (T) environment.getProperty(key, String.valueOf(defaultValue));
        }
        return environment.getProperty(key, (Class<T>) clazz, defaultValue);
    }
}
