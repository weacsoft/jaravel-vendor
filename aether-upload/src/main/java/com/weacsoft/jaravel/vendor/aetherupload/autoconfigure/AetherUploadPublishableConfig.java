package com.weacsoft.jaravel.vendor.aetherupload.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * aether-upload 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=aether-upload} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/AetherUploadConfig.java}，
 * 内含 {@code jaravel.aether-upload.*} 配置项说明。
 */
public class AetherUploadPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "aether-upload";
    }

    @Override
    public String className() {
        return "AetherUploadConfig";
    }

    @Override
    public String description() {
        return "大文件分片上传配置（路由前缀、分组规则、中间件）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * 大文件分片上传配置。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=aether-upload} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   aether-upload:
                 *     enabled: true                # 是否启用上传模块，默认 true
                 *     route-prefix: aetherupload   # 上传接口路由前缀，默认 aetherupload
                 *     default-group: file          # 默认分组名，默认 file
                 *     middleware: []               # 应用到上传路由的中间件别名列表，默认空
                 *     groups:                      # 分组规则，key 为分组名
                 *       file:
                 *         disk: local              # 使用的 storage disk
                 *         max-size: 0              # 单文件大小上限（字节），0 表示不限制
                 *         extensions: []           # 允许的扩展名白名单
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的
                 *       AetherUploadManager / AetherUploadController。</li>
                 *   <li>前端脚本可执行 {@code artisan vendor:publish --tag=aether-upload}（或 {@code --tag=resources}）发布。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class AetherUploadConfig {

                    /**
                     * 上传模块生效配置快照。
                     *
                     * @param provider AetherUploadProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> aetherUploadConfigMetadata(
                            ObjectProvider<AetherUploadProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        AetherUploadProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.aether-upload", "未装配（aether-upload 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.aether-upload.enabled", properties.isEnabled());
                        metadata.put("jaravel.aether-upload.route-prefix", properties.getRoutePrefix());
                        metadata.put("jaravel.aether-upload.default-group", properties.getDefaultGroup());
                        metadata.put("jaravel.aether-upload.middleware", properties.getMiddleware());
                        metadata.put("jaravel.aether-upload.groups", properties.getGroups().keySet());
                        return metadata;
                    }
                }
                """;
    }
}
