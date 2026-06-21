package com.weacsoft.jaravel.vendor.session.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Session 配置属性，前缀 {@code jaravel.session.redis}，对齐 Laravel {@code config/session.php}。
 * <pre>
 * jaravel:
 *   session:
 *     redis:
 *       connection: session          # Redis 连接名，对应 jaravel.redis.connections.session
 *       prefix: laravel_session      # Session 键前缀
 *       lifetime: 30                 # Session 生命周期（分钟）
 *       cookie: manage_session       # Cookie 名称
 *       auto-register: true          # 是否自动注册 redis-session guard 驱动到 AuthManager
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.session.redis")
public class SessionRedisProperties {

    /** Redis 连接名，对应 jaravel.redis.connections 中的配置 */
    private String connection = "session";

    /** Session 键前缀 */
    private String prefix = "laravel_session";

    /** Session 生命周期（分钟），默认 30 */
    private long lifetime = 30;

    /** Cookie 名称 */
    private String cookie = "manage_session";

    /** 是否自动注册 redis-session guard 驱动到 AuthManager */
    private boolean autoRegister = true;

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public long getLifetime() {
        return lifetime;
    }

    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
