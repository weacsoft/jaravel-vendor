package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.wire.WireManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Wire 模块 SpringBoot 自动装配。
 * <p>
 * 当 {@code jaravel.wire.enabled=true}（默认）时，自动读取配置并应用到 {@link WireManager}：
 * <ul>
 *   <li>{@code auto-inject-js}：控制是否自动注入 wire.js 的 script 标签</li>
 *   <li>{@code js-path}：wire.js 的外部引用路径</li>
 * </ul>
 * <p>
 * 设为 {@code auto-inject-js=false} 后，Wire 渲染时只注入 wire:config 配置标签，
 * 开发者需自行在页面中引入 wire.js（可使用 {@link WireManager#getWireJsContent()} 获取 JS 内容）。
 */
@AutoConfiguration
@ConditionalOnClass(WireManager.class)
@ConditionalOnProperty(prefix = "jaravel.wire", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WireProperties.class)
public class WireAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WireAutoConfiguration.class);

    public WireAutoConfiguration(WireProperties properties) {
        WireManager.setAutoInjectJs(properties.isAutoInjectJs());
        WireManager.setJsPath(properties.getJsPath());
        // 应用排除列表
        if (properties.getExcludedSections() != null && !properties.getExcludedSections().isEmpty()) {
            WireManager.addExcludedSections(properties.getExcludedSections().toArray(new String[0]));
        }
        log.info("Wire 模块已初始化：autoInjectJs={}, jsPath={}, excludedSections={}",
                properties.isAutoInjectJs(), properties.getJsPath(), properties.getExcludedSections());
    }
}
