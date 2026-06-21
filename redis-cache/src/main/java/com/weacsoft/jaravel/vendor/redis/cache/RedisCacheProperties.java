package com.weacsoft.jaravel.vendor.redis.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 缓存配置属性，前缀 {@code jaravel.cache.redis}。
 * <pre>
 * jaravel:
 *   cache:
 *     redis:
 *       connection: cache          # Redis 连接名，对应 jaravel.redis.connections 中的配置
 *       auto-register: true        # 是否自动注册到 CacheManager
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.cache.redis")
public class RedisCacheProperties {

    /** Redis 连接名，对应 jaravel.redis.connections 中的配置，默认 cache */
    private String connection = "cache";

    /** 是否自动注册到 CacheManager，默认 true */
    private boolean autoRegister = true;

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
