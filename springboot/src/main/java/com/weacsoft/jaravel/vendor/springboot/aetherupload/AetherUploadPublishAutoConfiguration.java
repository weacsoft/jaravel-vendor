package com.weacsoft.jaravel.vendor.springboot.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadPublishableConfig;
import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadStaticPublishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * aether-upload 模块「发布配置」自动装配。
 * <p>
 * <b>为什么从 {@link AetherUploadAutoConfiguration} 中拆出来单独成类</b>：
 * {@code AetherUploadAutoConfiguration} 在类级带有运行时开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.aether-upload", name = "enabled", ...)}，
 * 业务方写入 {@code enabled=false} 后整个自动装配不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：模板能否生成，
 * 与「上传运行时是否启用」相互独立。因此本类使用静态注册表，
 * 确保任何情况下都能执行 {@code artisan vendor:publish --tag=aether-upload}
 * （含其配置类与静态前端资源）。
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
