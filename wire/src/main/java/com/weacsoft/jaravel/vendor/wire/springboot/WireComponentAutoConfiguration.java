package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.jblade.BladeFunctions;
import com.weacsoft.jaravel.vendor.wire.component.WireComponents;
import com.weacsoft.jaravel.vendor.wire.component.WireOutlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wire 命名组件自动装配（Outlet 中间件 + 注册表 + 前端运行时发布）。
 * <p>
 * 与 {@link WireAutoConfiguration} 同属 wire 模块自动装配，在本模块配置就绪后执行：
 * <ul>
 *   <li>注册 {@code WireOutlet} 中间件别名（机制与 {@code VerifyCsrfToken} 完全对齐，
 *       应用只需在 Web 路由组引用 {@code "WireOutlet"} 即可启用）；</li>
 *   <li>注册 {@code wire_outlet()} 模板辅助函数，输出加载位置容器；</li>
 *   <li>按 {@code jaravel.wire.components} 配置批量注册命名组件；</li>
 *   <li>应用 {@code jaravel.wire.outlet} 子配置（位置 / 例外 / 是否注入 js / js 路径）；</li>
 *   <li>声明 {@code wire-component.js} 为可发布静态资源（{@code vendor:publish:static --tag=wire}）。</li>
 * </ul>
 */
@AutoConfiguration(after = WireAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jaravel.wire", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WireProperties.class)
public class WireComponentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WireComponentAutoConfiguration.class);

    public WireComponentAutoConfiguration(WireProperties properties) {
        // 1) 内置 WireOutlet 中间件别名（开箱即用，对齐 VerifyCsrfToken 的注册方式）
        MiddlewareAliasRegistry.getGlobal().register("WireOutlet", WireOutlet.instance());

        // 2) wire_outlet() 模板辅助函数：输出加载位置容器（中间件未启用时自动返回空串）
        BladeFunctions.register("wire_outlet", args -> WireOutlet.outletTag(RequestFactory.getCurrentRequest()));

        // 3) 按配置注册命名组件（名称 → 模板）
        if (properties.getComponents() != null && !properties.getComponents().isEmpty()) {
            WireComponents.registerAll(properties.getComponents());
        }

        // 4) 应用 outlet 子配置
        WireProperties.Outlet outlet = properties.getOutlet();
        WireOutlet.setExcept(outlet.getExcept());
        WireOutlet.setPosition(outlet.getPosition());
        WireOutlet.setAutoInjectJs(outlet.isAutoInjectJs());
        WireOutlet.setJsPath(outlet.getJsPath());

        // 自检：模板辅助函数必须确实注册成功，避免静默不可用
        if (!BladeFunctions.has("wire_outlet")) {
            throw new IllegalStateException(
                    "[wire-component] 模板辅助函数 wire_outlet() 注册失败，{!! wire_outlet() !!} 将不可用。");
        }

        log.info("[wire-component] 已注册 WireOutlet 别名与 wire_outlet() 辅助函数；已注册组件: {}；outlet 位置={}, 例外={}",
                WireComponents.names(), outlet.getPosition(), outlet.getExcept());
    }

    /**
     * 声明 {@code wire-component.js} 为可发布静态资源（tag 复用 {@code wire}，
     * 与 {@code wire.js} 一起通过 {@code vendor:publish:static --tag=wire} 发布）。
     */
    @Bean
    @ConditionalOnMissingBean(WireComponentStaticPublishable.class)
    public WireComponentStaticPublishable wireComponentStaticPublishable() {
        return new WireComponentStaticPublishable();
    }
}
