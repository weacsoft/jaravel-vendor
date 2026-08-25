package com.weacsoft.jaravel.vendor.springboot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证配置属性，前缀 {@code jaravel.auth}。
 * <pre>
 * jaravel:
 *   auth:
 *     default-guard: api
 *     providers:
 *       users:
 *         driver: eloquent
 *         model: com.weacsoft.jaravel.app.model.User
 *         credential-field: number
 *       admins:
 *         driver: eloquent
 *         model: com.weacsoft.jaravel.app.model.admin.Admin
 *         credential-field: username
 *     guards:
 *       web:
 *         driver: session
 *         provider: users
 *       api:
 *         driver: jwt
 *         provider: users
 *       admin:
 *         driver: jwt
 *         provider: admins
 * </pre>
 * <p>
 * 除了配置式注册，也支持注解声明式注册：
 * <ul>
 *   <li>{@code @RegisterProvider("users")} 声明 {@code com.weacsoft.jaravel.vendor.auth.contract.UserProvider}（注解 value 即 provider name）</li>
 *   <li>{@code @RegisterGuard("web")} 声明 {@code com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition}（注解 value 即 guard name）</li>
 * </ul>
 * 注解声明优先于配置式（同名时覆盖）。
 * <p>
 * JWT 相关配置在独立 jwt 模块的配置属性（前缀 {@code jaravel.jwt}）。
 */
@ConfigurationProperties(prefix = "jaravel.auth")
public class AuthProperties {

    /** 默认守卫名 */
    private String defaultGuard = "web";

    /** 提供者配置，key 为提供者名称 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 守卫配置，key 为守卫名称 */
    private Map<String, GuardConfig> guards = new LinkedHashMap<>();

    public String getDefaultGuard() {
        return defaultGuard;
    }

    public void setDefaultGuard(String defaultGuard) {
        this.defaultGuard = defaultGuard;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public Map<String, GuardConfig> getGuards() {
        return guards;
    }

    public void setGuards(Map<String, GuardConfig> guards) {
        this.guards = guards;
    }

    /** 单个提供者的配置 */
    public static class ProviderConfig {
        /** 驱动名称：eloquent / 自定义 */
        private String driver;
        /** Model 类全名（eloquent 驱动使用） */
        private String model;
        /** 凭证字段名（如 number / username） */
        private String credentialField;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getCredentialField() {
            return credentialField;
        }

        public void setCredentialField(String credentialField) {
            this.credentialField = credentialField;
        }

        /**
         * 转为工厂驱动的配置 Map。
         */
        public Map<String, Object> toConfigMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (model != null) map.put("model", model);
            if (credentialField != null) map.put("credential-field", credentialField);
            return map;
        }
    }

    /** 单个守卫的配置 */
    public static class GuardConfig {
        /** 驱动名称：session / jwt */
        private String driver;
        /** 提供者名称 */
        private String provider;

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
    }
}
