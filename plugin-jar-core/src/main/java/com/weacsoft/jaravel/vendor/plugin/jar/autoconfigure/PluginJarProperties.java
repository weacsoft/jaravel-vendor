package com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 插件 JAR 系统配置属性，前缀 {@code jaravel.plugin-jar}。
 * <pre>
 * jaravel:
 *   plugin-jar:
 *     enabled: true
 *     plugins-dir: plugins
 *     auto-restore: true
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.plugin-jar")
public class PluginJarProperties {

    /** 是否启用插件 JAR 系统 */
    private boolean enabled = true;

    /** 插件目录（相对路径基于工作目录） */
    private String pluginsDir = "plugins";

    /** 是否在启动时自动恢复已启用的插件 */
    private boolean autoRestore = true;

    /** 是否自动注册插件路由（true=自动注册@PluginMapping，false=手动注册） */
    private boolean autoRegister = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(String pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public boolean isAutoRestore() {
        return autoRestore;
    }

    public void setAutoRestore(boolean autoRestore) {
        this.autoRestore = autoRestore;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
