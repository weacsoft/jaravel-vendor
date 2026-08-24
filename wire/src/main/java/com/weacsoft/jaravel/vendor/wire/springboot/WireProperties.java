package com.weacsoft.jaravel.vendor.wire.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 命名组件注册表（名称 → 模板名）。例如：
     * <pre>
     * jaravel:
     *   wire:
     *     components:
     *       toast:   components.toast
     *       confirm: components.confirm
     * </pre>
     */
    private Map<String, String> components = new LinkedHashMap<>();

    /** 命名组件加载位置（Outlet）子配置。 */
    private final Outlet outlet = new Outlet();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoInjectJs() { return autoInjectJs; }
    public void setAutoInjectJs(boolean autoInjectJs) { this.autoInjectJs = autoInjectJs; }

    public String getJsPath() { return jsPath; }
    public void setJsPath(String jsPath) { this.jsPath = jsPath; }

    public List<String> getExcludedSections() { return excludedSections; }
    public void setExcludedSections(List<String> excludedSections) { this.excludedSections = excludedSections != null ? excludedSections : new ArrayList<>(); }

    public Map<String, String> getComponents() { return components; }
    public void setComponents(Map<String, String> components) { this.components = components != null ? new LinkedHashMap<>(components) : new LinkedHashMap<>(); }

    public Outlet getOutlet() { return outlet; }

    /**
     * 命名组件加载位置（Outlet）子配置：
     * <pre>
     * jaravel:
     *   wire:
     *     outlet:
     *       enabled: true
     *       position: body-end          # body-end（默认，插到 </body> 前）| body-start（插到 <body> 后）
     *       except:                     # 排除路径，支持精确 /login 与前缀通配 /admin/*
     *         - /login
     *       auto-inject-js: true        # 是否自动注入 wire-component.js
     *       js-path: /static/wire-component.js
     * </pre>
     */
    public static class Outlet {
        private boolean enabled = true;
        private String position = "body-end";
        private List<String> except = new ArrayList<>();
        private boolean autoInjectJs = true;
        private String jsPath = "/static/wire-component.js";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }

        public List<String> getExcept() { return except; }
        public void setExcept(List<String> except) { this.except = except != null ? except : new ArrayList<>(); }

        public boolean isAutoInjectJs() { return autoInjectJs; }
        public void setAutoInjectJs(boolean autoInjectJs) { this.autoInjectJs = autoInjectJs; }

        public String getJsPath() { return jsPath; }
        public void setJsPath(String jsPath) { this.jsPath = jsPath; }
    }
}
