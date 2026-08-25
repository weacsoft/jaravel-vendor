package com.weacsoft.jaravel.vendor.springboot.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadPublishableConfig;
import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadStaticPublishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * aether-upload 模块「发布配置」自动装配。
 * <p>
 * <b>为什么从 {@link AetherUploadAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code AetherUploadAutoConfiguration} 在<b>类级</b>带有运行时开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.aether-upload", name = "enabled", havingValue = "true", matchIfMissing = true)}。
 * 业务方一旦写入 {@code jaravel.aether-upload.enabled=false} 整体关闭上传能力，
 * 整个自动装配便不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：能否生成
 * {@code AetherUploadConfig.java} 模板，与「上传运行时是否启用」是两回事——
 * 恰恰是尚未启用的开发者更需要先拿到模板，配置好分组、落盘磁盘与记录头 store 之后才打开开关。
 * <p>
 * 因此本类使用静态注册表，确保任何情况下都能执行
 * {@code artisan vendor:publish --tag=aether-upload}（含其配置类与静态前端资源）。
 * <p>
 * 两个 Publishable 契约类为纯 Java，保留在 aether-upload 模块。
 */
@AutoConfiguration
@ConditionalOnClass({AetherUploadPublishableConfig.class, AetherUploadStaticPublishable.class})
public class AetherUploadPublishAutoConfiguration {

    static {
        PublishableRegistry.register(new AetherUploadPublishableConfig());
        PublishableRegistry.register(new AetherUploadStaticPublishable());
    }
}
