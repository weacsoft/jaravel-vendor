package com.weacsoft.jaravel.vendor.wire.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PJAX 无感切换配置属性。
 *
 * <p>在 application.yml 中通过 {@code jaravel.pjax.*} 配置：</p>
 * <pre>
 * jaravel:
 *   pjax:
 *     enabled: true                   # 是否启用 PJAX 无感切换
 *     auto-inject-js: true            # 是否自动注入 pjax.js 的 script 标签
 *     js-path: /static/pjax.js        # pjax.js 的外部引用路径
 *     excluded-regions:               # 不参与切换的区域名（始终保留，不做 DOM 替换）
 *       - drawer
 *     excluded-prefixes:              # 完全不介入的路径前缀
 *       - /api
 *       - /static
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.pjax")
public class PjaxProperties {

    /** 是否启用 PJAX 无感切换 */
    private boolean enabled = true;

    /** 是否自动注入 pjax.js 的 script 标签 */
    private boolean autoInjectJs = true;

    /** pjax.js 的外部引用路径 */
    private String jsPath = "/static/pjax.js";

    /** 不参与切换的区域名（这些区域渲染后会被去掉锚点，始终保持原样） */
    private List<String> excludedRegions = new ArrayList<>();

    /** 完全不介入的路径前缀，默认排除接口与静态资源 */
    private List<String> excludedPrefixes = new ArrayList<>(Arrays.asList("/api", "/static", "/assets"));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoInjectJs() { return autoInjectJs; }
    public void setAutoInjectJs(boolean autoInjectJs) { this.autoInjectJs = autoInjectJs; }

    public String getJsPath() { return jsPath; }
    public void setJsPath(String jsPath) { this.jsPath = jsPath; }

    public List<String> getExcludedRegions() { return excludedRegions; }
    public void setExcludedRegions(List<String> excludedRegions) {
        this.excludedRegions = excludedRegions != null ? excludedRegions : new ArrayList<>();
    }

    public List<String> getExcludedPrefixes() { return excludedPrefixes; }
    public void setExcludedPrefixes(List<String> excludedPrefixes) {
        this.excludedPrefixes = excludedPrefixes != null ? excludedPrefixes : new ArrayList<>();
    }
}
