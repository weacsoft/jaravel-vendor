package com.weacsoft.jaravel.vendor.springboot.sessionredis;

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
 *       auto-register: true          # 强制开关（可省略）
 * </pre>
 *
 * <h3>关于 auto-register</h3>
 * 本模块是否装配，默认由 {@code jaravel.session.driver} 是否为 {@code redis} 决定
 * （<b>安装 ≠ 启用</b>）。{@code auto-register} 仅作为覆盖开关，优先级最高：
 * <ul>
 *   <li>不配置（默认）—— 由 {@code jaravel.session.driver} 自动判定；</li>
 *   <li>{@code true} —— 强制启用；</li>
 *   <li>{@code false} —— 强制关闭。</li>
 * </ul>
 *
 * @see OnRedisSessionDriverCondition
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

    /**
     * 装配覆盖开关：{@code null}（默认）表示由 {@code jaravel.session.driver} 自动判定，
     * {@code true} 强制启用，{@code false} 强制关闭。
     */
    private Boolean autoRegister;

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

    public Boolean getAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(Boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
