package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多租户插件配置属性，前缀 {@code jaravel.plugin-jar.multi-tenant}。
 * <pre>
 * jaravel:
 *   plugin-jar:
 *     multi-tenant:
 *       enabled: true        # 引入本模块后默认启用
 *       separator: "@"       # pluginId 中的租户分隔符
 * </pre>
 * <p>
 * 当 {@code enabled=false} 时，多租户自动装配不生效，
 * 系统回退到默认的 {@code PluginJarAutoConfiguration}（单例插件模式）。
 */
@ConfigurationProperties(prefix = "jaravel.plugin-jar.multi-tenant")
public class MultiTenantProperties {

    /** 是否启用多租户插件模式 */
    private boolean enabled = true;

    /** pluginId 中的租户分隔符，如 "studentA@blog" 中的 "@" */
    private String separator = "@";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }
}
