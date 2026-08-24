package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * redis-cache 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=redis-cache} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/RedisCacheConfig.java}，
 * 内含 {@code jaravel.cache.redis.*} 配置项说明。
 */
public class RedisCachePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "redis-cache";
    }

    @Override
    public String className() {
        return "RedisCacheConfig";
    }

    @Override
    public String description() {
        return "Redis 缓存驱动配置（连接名、自动注册开关）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.redis.cache.RedisCacheProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * Redis 缓存驱动配置，对齐 Laravel config/cache.php 的 redis store。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=redis-cache} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   cache:
                 *     stores:
                 *       redis:
                 *         driver: redis        # 声明一个 driver=redis 的 store 才会装配本模块
                 *     redis:
                 *       connection: cache      # 使用的 jaravel.redis.connections 名称，默认 cache
                 *       auto-register:         # 是否自动注册 redis 驱动工厂，留空由框架判定
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 CacheDriverFactory。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class RedisCacheConfig {

                    /**
                     * Redis 缓存生效配置快照。
                     *
                     * @param provider RedisCacheProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> redisCacheConfigMetadata(
                            ObjectProvider<RedisCacheProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        RedisCacheProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.cache.redis", "未装配（redis-cache 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.cache.redis.connection", properties.getConnection());
                        metadata.put("jaravel.cache.redis.auto-register", properties.getAutoRegister());
                        return metadata;
                    }
                }
                """;
    }
}
