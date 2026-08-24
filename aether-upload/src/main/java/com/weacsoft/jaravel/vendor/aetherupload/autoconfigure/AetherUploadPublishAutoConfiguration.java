package com.weacsoft.jaravel.vendor.aetherupload.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

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
 * 因此本类使用静态注册表，确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=aether-upload}（含其配置类与静态前端资源）。
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
public class AetherUploadPublishAutoConfiguration {
    static {
        PublishableRegistry.register(new AetherUploadPublishableConfig());
        PublishableRegistry.register(new AetherUploadStaticPublishable());
    }
}
