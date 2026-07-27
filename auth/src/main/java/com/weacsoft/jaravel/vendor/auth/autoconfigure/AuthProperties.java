package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证配置属性，前缀 {@code jaravel.auth}。
 * <pre>
 * jaravel:
 *   auth:
 *     default-guard: api
 *     guards:
 *       web:
 *         driver: session
 *         provider: users
 *         session-store: cookie      # 可选，默认 cookie（cookie / redis）
 *       api:
 *         driver: jwt
 *         provider: users
 *       admin:
 *         driver: jwt
 *         provider: admins
 * </pre>
 * <p>
 * 认证架构分两层：
 * <ul>
 *   <li><b>认证驱动</b>（driver）：session | jwt | ...（通过 {@link com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver} 的 support 方法匹配）</li>
 *   <li><b>Session 存储</b>（session-store）：cookie | redis | ...（仅 session 驱动使用，通过 {@link com.weacsoft.jaravel.vendor.auth.contract.SessionStore} 的 support 方法匹配）</li>
 * </ul>
 * <p>
 * JWT 相关配置在独立 jwt 模块的 {@code JwtProperties}（前缀 {@code jaravel.jwt}）。
 */
@ConfigurationProperties(prefix = "jaravel.auth")
public class AuthProperties {

    /** 默认守卫名 */
    private String defaultGuard = "web";

    /** 守卫配置，key 为守卫名称 */
    private Map<String, GuardConfig> guards = new LinkedHashMap<>();

    public String getDefaultGuard() {
        return defaultGuard;
    }

    public void setDefaultGuard(String defaultGuard) {
        this.defaultGuard = defaultGuard;
    }

    public Map<String, GuardConfig> getGuards() {
        return guards;
    }

    public void setGuards(Map<String, GuardConfig> guards) {
        this.guards = guards;
    }

    /** 单个守卫的配置 */
    public static class GuardConfig {
        /** 驱动名称：session / jwt */
        private String driver;
        /** 提供者名称 */
        private String provider;
        /** Session 存储后端：cookie / redis，仅 session 驱动使用，默认 cookie */
        private String sessionStore;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSessionStore() {
            return sessionStore;
        }

        public void setSessionStore(String sessionStore) {
            this.sessionStore = sessionStore;
        }
    }
}
