package com.weacsoft.jaravel.vendor.plugin.java.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Java 文件插件配置属性。
 * <p>
 * 配置前缀：{@code jaravel.plugin-java}
 * <p>
 * 示例配置：
 * <pre>
 * jaravel:
 *   plugin-java:
 *     enabled: true
 *     source-dir: plugins-java
 *     auto-scan: true
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.plugin-java")
public class PluginJavaProperties {

    /** 是否启用 Java 文件插件系统 */
    private boolean enabled = true;

    /** .java 文件插件源目录（相对于工作目录） */
    private String sourceDir = "plugins-java";

    /** 启动时是否自动扫描源目录并注册插件 */
    private boolean autoScan = true;

    /** 是否自动注册插件路由（true=自动注册@PluginMapping，false=手动注册） */
    private boolean autoRegister = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }
}
