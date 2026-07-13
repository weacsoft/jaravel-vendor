package com.weacsoft.jaravel.vendor.wire.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire 模块 SpringBoot 配置属性。
 * <p>
 * 在 application.yml 中通过 {@code jaravel.wire.*} 配置：
 * <pre>
 * jaravel:
 *   wire:
 *     enabled: true
 *     auto-inject-js: true            # 是否自动注入 wire.js 的 script 标签
 *     js-path: /static/wire.js        # wire.js 的外部引用路径
 *     excluded-sections:              # 排除的 section 名列表（不生成 wire 标记）
 *       - header
 *       - footer
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.wire")
public class WireProperties {

    /** 是否启用自动装配 */
    private boolean enabled = true;

    /** 是否自动注入 wire.js 的 script 标签（false 时需手动引入） */
    private boolean autoInjectJs = true;

    /** wire.js 的外部引用路径 */
    private String jsPath = "/static/wire.js";

    /** Wire section 排除列表：这些 section 不会被 wire:section 标记包裹 */
    private List<String> excludedSections = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoInjectJs() { return autoInjectJs; }
    public void setAutoInjectJs(boolean autoInjectJs) { this.autoInjectJs = autoInjectJs; }

    public String getJsPath() { return jsPath; }
    public void setJsPath(String jsPath) { this.jsPath = jsPath; }

    public List<String> getExcludedSections() { return excludedSections; }
    public void setExcludedSections(List<String> excludedSections) { this.excludedSections = excludedSections != null ? excludedSections : new ArrayList<>(); }
}
