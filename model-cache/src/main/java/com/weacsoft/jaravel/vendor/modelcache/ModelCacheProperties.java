package com.weacsoft.jaravel.vendor.modelcache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型缓存配置属性，前缀 {@code jaravel.model-cache}。
 * <pre>
 * jaravel:
 *   model-cache:
 *     enabled: true              # 全局开关，关闭后所有模型缓存不生效（直接回源）
 *     store:                     # 缓存 store 名称，为空时使用 cache 模块默认 store
 *     default-ttl: 3600          # 默认缓存 TTL（秒）
 *     key-prefix: "model-cache:" # 缓存键前缀
 * </pre>
 * TTL 单位为秒（对齐 cache 模块）。
 */
@ConfigurationProperties(prefix = "jaravel.model-cache")
public class ModelCacheProperties {

    /** 全局开关，关闭后所有模型缓存不生效（直接回源） */
    private boolean enabled = true;

    /**
     * 缓存 store 名称，为空时使用 cache 模块的默认 store
     *（由 {@code jaravel.cache.default-store} 决定，不关心具体实现）。
     * 可显式指定 store 名（如 "redis"）以覆盖默认行为。
     */
    private String store = "";

    /** 默认缓存 TTL（秒），@CachableModel 未指定或为 -1 时使用 */
    private long defaultTtl = 3600;

    /** 缓存键前缀 */
    private String keyPrefix = "model-cache:";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public long getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
