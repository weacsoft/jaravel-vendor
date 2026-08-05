package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * model-cache 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=model-cache} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/ModelCacheConfig.java}，
 * 内含 {@code jaravel.model-cache.*} 配置项说明。
 */
public class ModelCachePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "model-cache";
    }

    @Override
    public String className() {
        return "ModelCacheConfig";
    }

    @Override
    public String description() {
        return "模型缓存配置（缓存 store、默认 TTL、key 前缀）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.modelcache.ModelCacheProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * 模型缓存配置，对齐 Laravel 的 Model 查询缓存能力。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=model-cache} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   model-cache:
                 *     enabled: true                # 是否启用模型缓存，默认 true
                 *     store: ""                    # 使用的缓存 store 名，留空则用默认 store
                 *     default-ttl: 3600            # 默认缓存秒数，默认 3600
                 *     key-prefix: "model-cache:"   # 缓存 key 前缀，默认 model-cache:
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>在模型上实现 {@code CachableModel} 即可启用该模型的缓存。</li>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 ModelCacheService。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class ModelCacheConfig {

                    /**
                     * 模型缓存生效配置快照。
                     *
                     * @param provider ModelCacheProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> modelCacheConfigMetadata(
                            ObjectProvider<ModelCacheProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        ModelCacheProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.model-cache", "未装配（model-cache 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.model-cache.enabled", properties.isEnabled());
                        metadata.put("jaravel.model-cache.store", properties.getStore());
                        metadata.put("jaravel.model-cache.default-ttl", properties.getDefaultTtl());
                        metadata.put("jaravel.model-cache.key-prefix", properties.getKeyPrefix());
                        return metadata;
                    }
                }
                """;
    }
}
