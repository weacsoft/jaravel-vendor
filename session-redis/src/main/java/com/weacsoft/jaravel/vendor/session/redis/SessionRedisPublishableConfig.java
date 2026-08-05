package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * session-redis 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=session-redis} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/SessionRedisConfig.java}，
 * 内含 {@code jaravel.session.redis.*} 配置项说明。
 */
public class SessionRedisPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "session-redis";
    }

    @Override
    public String className() {
        return "SessionRedisConfig";
    }

    @Override
    public String description() {
        return "Redis Session 驱动配置（连接、前缀、有效期、Cookie 名）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.session.redis.SessionRedisProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * Redis Session 配置，对齐 Laravel config/session.php 的 redis driver。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=session-redis} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   session:
                 *     driver: redis                # 启用 Redis Session 驱动的开关
                 *     redis:
                 *       connection: session        # 使用的 jaravel.redis.connections 名称，默认 session
                 *       prefix: laravel_session    # Redis key 前缀，默认 laravel_session
                 *       lifetime: 30               # Session 有效期（分钟），默认 30
                 *       cookie: manage_session     # Session Cookie 名称，默认 manage_session
                 *       auto-register:             # 是否强制注册为默认 SessionStore，留空由框架判定
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 SessionStore。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class SessionRedisConfig {

                    /**
                     * Redis Session 生效配置快照。
                     *
                     * @param provider SessionRedisProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> sessionRedisConfigMetadata(
                            ObjectProvider<SessionRedisProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        SessionRedisProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.session.redis", "未装配（session-redis 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.session.redis.connection", properties.getConnection());
                        metadata.put("jaravel.session.redis.prefix", properties.getPrefix());
                        metadata.put("jaravel.session.redis.lifetime", properties.getLifetime());
                        metadata.put("jaravel.session.redis.cookie", properties.getCookie());
                        metadata.put("jaravel.session.redis.auto-register", properties.getAutoRegister());
                        return metadata;
                    }
                }
                """;
    }
}
