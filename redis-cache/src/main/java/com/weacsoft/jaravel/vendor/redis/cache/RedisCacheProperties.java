package com.weacsoft.jaravel.vendor.redis.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 缓存配置属性，前缀 {@code jaravel.cache.redis}。
 * <pre>
 * jaravel:
 *   cache:
 *     redis:
 *       connection: cache          # Redis 连接名，对应 jaravel.redis.connections 中的配置
 *       auto-register: true        # 装配覆盖开关（可省略）
 * </pre>
 *
 * <h3>关于 auto-register</h3>
 * 本模块是否装配，默认由「是否存在 {@code driver: redis} 的缓存 store」决定
 * （<b>安装 ≠ 启用</b>）。{@code auto-register} 仅作为覆盖开关，优先级最高：
 * <ul>
 *   <li>不配置（默认）—— 由 {@code jaravel.cache.stores.*.driver} 自动判定；</li>
 *   <li>{@code true} —— 强制启用；</li>
 *   <li>{@code false} —— 强制关闭。</li>
 * </ul>
 *
 * @see OnRedisCacheStoreCondition
 */
@ConfigurationProperties(prefix = "jaravel.cache.redis")
public class RedisCacheProperties {

    /** Redis 连接名，对应 jaravel.redis.connections 中的配置，默认 cache */
    private String connection = "cache";

    /**
     * 装配覆盖开关：{@code null}（默认）表示由缓存 store 的 driver 自动判定，
     * {@code true} 强制启用，{@code false} 强制关闭。
     */
    private Boolean autoRegister;

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public Boolean getAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(Boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
