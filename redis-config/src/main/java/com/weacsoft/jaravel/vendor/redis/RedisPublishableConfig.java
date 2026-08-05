package com.weacsoft.jaravel.vendor.redis;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * redis-config 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=redis-config} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/RedisConfig.java}，
 * 内含 {@code jaravel.redis.*} 配置项说明。
 */
public class RedisPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "redis-config";
    }

    @Override
    public String className() {
        return "RedisConfig";
    }

    @Override
    public String description() {
        return "Redis 多连接配置（client、options、connections）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.redis.RedisProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * Redis 配置，对齐 Laravel config/database.php 的 redis 段。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=redis-config} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   redis:
                 *     client: lettuce            # 客户端实现，默认 lettuce
                 *     options:
                 *       cluster: redis           # 集群模式，默认 redis
                 *       prefix: ""               # 全局 key 前缀
                 *     connections:               # 命名连接表，key 即连接名
                 *       default:
                 *         url: ""                # 完整 URL，优先级高于 host/port
                 *         host: 127.0.0.1
                 *         port: 6379
                 *         username: ""
                 *         password: ""
                 *         database: 0
                 *         timeout-ms: 2000
                 *         sentinel-master: ""    # 哨兵模式主节点名
                 *         sentinels: ""          # 哨兵地址，逗号分隔
                 *         cluster-nodes: ""      # 集群节点地址，逗号分隔
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>只有配置了 {@code jaravel.redis.connections} 时 redis 模块才会装配。</li>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 RedisManager。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class RedisConfig {

                    /**
                     * Redis 生效配置快照。
                     *
                     * @param provider RedisProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> redisConfigMetadata(
                            ObjectProvider<RedisProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        RedisProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.redis", "未装配（未配置 jaravel.redis.connections）");
                            return metadata;
                        }
                        metadata.put("jaravel.redis.client", properties.getClient());
                        metadata.put("jaravel.redis.options.cluster", properties.getOptions().getCluster());
                        metadata.put("jaravel.redis.options.prefix", properties.getOptions().getPrefix());
                        metadata.put("jaravel.redis.connections", properties.getConnections().keySet());
                        return metadata;
                    }
                }
                """;
    }
}
