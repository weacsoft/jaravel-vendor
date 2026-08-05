package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.wire.pjax.PjaxManager;
import com.weacsoft.jaravel.vendor.wire.pjax.PjaxMiddleware;
import com.weacsoft.jaravel.vendor.wire.pjax.PjaxViewRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * PJAX 无感切换自动装配。
 *
 * <p>当 {@code jaravel.pjax.enabled=true}（默认）时：</p>
 * <ol>
 *   <li>把配置应用到 {@link PjaxManager}；</li>
 *   <li>向 {@link ResponseBuilder} 注册 {@link PjaxViewRenderer}，
 *       使所有 {@code ResponseBuilder.view()} 自动具备无感切换能力；</li>
 *   <li>暴露 {@link PjaxMiddleware} Bean，供应用注册为全局中间件。</li>
 * </ol>
 *
 * <p>注册渲染器采用「http 定义接口 + wire 提供实现」的策略模式，
 * 避免 http 模块反向依赖 wire 造成循环依赖。</p>
 */
@AutoConfiguration
@ConditionalOnClass({PjaxManager.class, ResponseBuilder.class})
@ConditionalOnProperty(prefix = "jaravel.pjax", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PjaxProperties.class)
public class PjaxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PjaxAutoConfiguration.class);

    public PjaxAutoConfiguration(PjaxProperties properties) {
        PjaxManager.setAutoInjectJs(properties.isAutoInjectJs());
        PjaxManager.setJsPath(properties.getJsPath());
        PjaxManager.clearExcludedRegions();
        if (properties.getExcludedRegions() != null && !properties.getExcludedRegions().isEmpty()) {
            PjaxManager.addExcludedRegions(properties.getExcludedRegions().toArray(new String[0]));
        }
        PjaxMiddleware.setExcludedPrefixes(properties.getExcludedPrefixes());
        ResponseBuilder.setPjaxRenderer(new PjaxViewRenderer());
        log.info("PJAX 无感切换已启用：jsPath={}, excludedRegions={}, excludedPrefixes={}",
                properties.getJsPath(), properties.getExcludedRegions(), properties.getExcludedPrefixes());
    }

    /**
     * PJAX 全局中间件 Bean。应用可注入后注册到根路由：
     * <pre>{@code
     * baseRouter.middleware(pjaxMiddleware);
     * }</pre>
     */
    @Bean
    @ConditionalOnMissingBean(PjaxMiddleware.class)
    public PjaxMiddleware pjaxMiddleware() {
        return new PjaxMiddleware();
    }

    /**
     * 声明 {@code pjax.js} 为可发布静态资源，供 {@code vendor:publish:static --tag=pjax} 使用。
     */
    @Bean
    @ConditionalOnMissingBean(PjaxStaticPublishable.class)
    public PjaxStaticPublishable pjaxStaticPublishable() {
        return new PjaxStaticPublishable();
    }
}
