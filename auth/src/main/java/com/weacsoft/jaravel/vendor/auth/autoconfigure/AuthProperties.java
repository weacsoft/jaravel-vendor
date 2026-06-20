package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置属性，前缀 {@code jaravel.auth}。
 * <pre>
 * jaravel:
 *   auth:
 *     default-guard: api
 * </pre>
 * <p>
 * JWT 相关配置已移至独立 jwt 模块的 {@code JwtProperties}（前缀 {@code jaravel.jwt}）。
 */
@ConfigurationProperties(prefix = "jaravel.auth")
public class AuthProperties {

    /** 默认守卫名 */
    private String defaultGuard = "web";

    public String getDefaultGuard() {
        return defaultGuard;
    }

    public void setDefaultGuard(String defaultGuard) {
        this.defaultGuard = defaultGuard;
    }
}
