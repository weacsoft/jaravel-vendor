package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * wire 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link WireAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code WireAutoConfiguration} 在<b>类级别</b>带有运行期开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.wire", name = "enabled", havingValue = "true", matchIfMissing = true)}。
 * 只要业务方显式写下 {@code jaravel.wire.enabled=false}（例如暂时关闭 Wire 运行能力、
 * 或在某个 profile 下关掉），整个自动配置就不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：能否生成
 * {@code WireConfig.java} 模板，不应该由「Wire 运行期是否启用」来决定——
 * 恰恰是关闭状态下的开发者更需要先拿到配置模板，填好之后再打开开关。
 * <p>
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>，
 * 不含任何运行时条件，确保任何情况下都能执行 {@code artisan vendor:publish --tag=wire}
 * （含其配置类与前端静态资源 wire.js）。
 * <p>
 * 说明：wire.js 已将 wire-component.js 与 wire-navigate.js 合并为单一文件，
 * 前端静态资源发布由 {@link WireStaticPublishable} 统一处理，不再区分三个文件。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class WirePublishAutoConfiguration {

    /**
     * 声明 wire 模块的可发布配置类，供 {@code artisan vendor:publish --tag=wire} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(WirePublishableConfig.class)
    public WirePublishableConfig wirePublishableConfig() {
        return new WirePublishableConfig();
    }
}
