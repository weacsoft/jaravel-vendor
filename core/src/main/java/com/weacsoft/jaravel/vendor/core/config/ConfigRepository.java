package com.weacsoft.jaravel.vendor.core.config;

import com.weacsoft.jaravel.vendor.core.support.Arr;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置仓库，对齐 Laravel {@code config()}。
 * <p>
 * 配置来源有三层，优先级从高到低：
 * <ol>
 *     <li>运行时覆盖（{@link #set} 内存写入）</li>
 *     <li>代码级配置（{@link ConfigDefinition}，对齐 Laravel 的 config/*.php）</li>
 *     <li>Spring {@link Environment}（application.yml 等外部配置）</li>
 * </ol>
 * 支持点号取值与默认值。
 */
public class ConfigRepository {

    private final Environment environment;
    /** 运行时覆盖配置，优先级最高 */
    private final Map<String, Object> overrides = new LinkedHashMap<>();
    /** 代码级配置，按命名空间组织，如 {"app": {...}, "database": {...}}，优先级高于 Environment */
    private final Map<String, Object> codeConfig = new LinkedHashMap<>();

    public ConfigRepository(Environment environment) {
        this.environment = environment;
    }

    /**
     * 注册代码级配置定义，将其 values 合并到 codeConfig 命名空间下。
     *
     * @param definition 代码级配置定义
     */
    public void registerConfigDefinition(ConfigDefinition definition) {
        if (definition == null) return;
        Map<String, Object> values = definition.values();
        if (values != null) {
            codeConfig.put(definition.namespace(), values);
        }
    }

    /**
     * 读取配置，支持点号，如 {@code get("app.name", "default")}。
     * <p>
     * 查找顺序：运行时覆盖 -> 代码级配置 -> Spring Environment。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        // 1. 运行时覆盖
        if (Arr.has(overrides, key)) {
            return Arr.get(overrides, key, defaultValue);
        }
        // 2. 代码级配置（支持点号路径，如 "app.name" -> codeConfig["app"]["name"]）
        if (Arr.has(codeConfig, key)) {
            return Arr.get(codeConfig, key, defaultValue);
        }
        // 3. Spring Environment
        if (environment != null) {
            String prop = environment.getProperty(key);
            if (prop != null) {
                return (T) prop;
            }
        }
        return defaultValue;
    }

    public <T> T get(String key) {
        return get(key, null);
    }

    public String string(String key, String defaultValue) {
        Object v = get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }

    public String string(String key) {
        return string(key, null);
    }

    public int getInt(String key, int defaultValue) {
        String v = string(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = string(key);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    /**
     * 运行时设置配置（覆盖 Environment）。
     */
    public void set(String key, Object value) {
        Arr.set(overrides, key, value);
    }

    public boolean has(String key) {
        return Arr.has(overrides, key)
                || Arr.has(codeConfig, key)
                || (environment != null && environment.getProperty(key) != null);
    }
}
