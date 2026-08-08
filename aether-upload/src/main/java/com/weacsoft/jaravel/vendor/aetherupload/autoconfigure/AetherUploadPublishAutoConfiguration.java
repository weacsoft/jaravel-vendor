package com.weacsoft.jaravel.vendor.aetherupload.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * aether-upload 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link AetherUploadAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code AetherUploadAutoConfiguration} 在<b>类级别</b>带有运行期开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.aether-upload", name = "enabled", havingValue = "true", matchIfMissing = true)}。
 * 业务方一旦写下 {@code jaravel.aether-upload.enabled=false} 整体关闭上传能力，
 * 整个自动配置便不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：能否生成
 * {@code AetherUploadConfig.java} 模板，与「上传运行期是否启用」是两回事——
 * 恰恰是尚未启用的开发者更需要先拿到模板，配置好分组、落盘磁盘与记录头 store 之后再打开开关。
 * <p>
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>，
 * 不含任何运行时条件，确保任何情况下都能执行 {@code artisan vendor:publish --tag=aether-upload}
 * （含其配置类与静态前端资源）。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class AetherUploadPublishAutoConfiguration {

    /**
     * 声明 aether-upload 模块的可发布配置类，供 {@code artisan vendor:publish --tag=aether-upload} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(AetherUploadPublishableConfig.class)
    public AetherUploadPublishableConfig aetherUploadPublishableConfig() {
        return new AetherUploadPublishableConfig();
    }

    /**
     * 声明 aether-upload 模块的静态前端资源（上传运行时 js），
     * 供 {@code artisan vendor:publish --tag=aether-upload} 或 {@code --tag=resources} 发布。
     * <p>
     * 与可发布配置同理：本类只保留 {@code @ConditionalOnClass} 条件，不含运行时开关，
     * 确保任何情况下都能发布前端脚本，无需先开启上传能力。
     *
     * @return 静态资源发布声明
     */
    @Bean
    @ConditionalOnMissingBean(AetherUploadStaticPublishable.class)
    public AetherUploadStaticPublishable aetherUploadStaticPublishable() {
        return new AetherUploadStaticPublishable();
    }
}
