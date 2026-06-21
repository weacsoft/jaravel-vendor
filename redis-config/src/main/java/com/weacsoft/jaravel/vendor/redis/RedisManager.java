package com.weacsoft.jaravel.vendor.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Redis 连接管理器，对齐 Laravel {@code RedisManager}（{@code Illuminate\Redis\RedisManager}）。
 * <p>
 * 管理多个命名连接（default / cache / session / model-cache 等），每个连接对应一个独立的
 * Lettuce 连接对象。所有连接均为线程安全，可被多线程共享。
 *
 * <h3>连接模式</h3>
 * <ul>
 *   <li><b>standalone</b>（默认）：单机或主从，通过 host:port 连接</li>
 *   <li><b>sentinel</b>：哨兵高可用，通过 sentinel 列表自动发现 master</li>
 *   <li><b>cluster</b>：Redis Cluster，通过集群节点列表连接，自动路由</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * Lettuce 的连接对象本身是线程安全的，单个连接可被多线程并发使用。
 * 本管理器使用 {@link ConcurrentHashMap} 维护连接名到连接对象的映射，
 * 连接在首次访问时惰性创建，进程生命周期内复用。
 *
 * <pre>
 * RedisManager redis = ...;
 * RedisCommands&lt;String, String&gt; cmd = redis.sync("cache");
 * String value = cmd.get("key");
 * </pre>
 */
public class RedisManager {

    private static final Logger logger = LoggerFactory.getLogger(RedisManager.class);

    /** 连接名 -> 连接配置，进程级共享，启动后只读 */
    private final Map<String, RedisProperties.ConnectionConfig> connectionConfigs;

    /** 全局选项 */
    private final RedisProperties.Options options;

    /** 连接名 -> 字符串编码连接（惰性创建），进程级共享 */
    private final ConcurrentMap<String, StatefulConnection<String, String>> stringConnections = new ConcurrentHashMap<>();

    /** 连接名 -> 字节编码连接（惰性创建），进程级共享 */
    private final ConcurrentMap<String, StatefulConnection<byte[], byte[]>> binaryConnections = new ConcurrentHashMap<>();

    /** Lettuce 客户端实例（单机/哨兵模式每连接一个），进程级共享 */
    private final ConcurrentMap<String, RedisClient> clients = new ConcurrentHashMap<>();

    /** 集群客户端实例（集群模式每连接一个），进程级共享 */
    private final ConcurrentMap<String, RedisClusterClient> clusterClients = new ConcurrentHashMap<>();

    /** 共享客户端资源（线程池、事件循环），所有连接复用 */
    private final ClientResources clientResources;

    /** 默认连接名 */
    private final String defaultConnection;

    /**
     * 构造 Redis 管理器。
     *
     * @param properties Redis 配置属性
     */
    public RedisManager(RedisProperties properties) {
        this.connectionConfigs = properties.getConnections();
        this.options = properties.getOptions();
        this.clientResources = DefaultClientResources.builder().build();
        this.defaultConnection = connectionConfigs.containsKey("default") ? "default"
                : (connectionConfigs.isEmpty() ? null : connectionConfigs.keySet().iterator().next());

        logger.info("[redis] RedisManager 初始化: connections={}, default={}, cluster={}, prefix='{}'",
                connectionConfigs.keySet(), defaultConnection,
                options.getCluster(), options.getPrefix());
    }

    /**
     * 获取默认连接的字符串编码同步命令接口。
     *
     * @return 默认连接的 sync 命令接口
     */
    public RedisCommands<String, String> sync() {
        return sync(defaultConnection);
    }

    /**
     * 获取指定连接的字符串编码同步命令接口。
     *
     * @param name 连接名，null 使用默认连接
     * @return 该连接的 sync 命令接口
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RedisCommands<String, String> sync(String name) {
        StatefulConnection<String, String> conn = connection(name);
        if (conn instanceof StatefulRedisConnection) {
            return ((StatefulRedisConnection<String, String>) conn).sync();
        } else if (conn instanceof StatefulRedisClusterConnection) {
            // 集群连接的 sync() 返回 RedisAdvancedClusterCommands，通过 Object 中转避免泛型协变问题
            Object sync = ((StatefulRedisClusterConnection) conn).sync();
            return (RedisCommands<String, String>) sync;
        }
        throw new IllegalStateException("未知连接类型: " + conn.getClass());
    }

    /**
     * 获取默认连接的字符串编码异步命令接口。
     */
    public RedisAsyncCommands<String, String> async() {
        return async(defaultConnection);
    }

    /**
     * 获取指定连接的字符串编码异步命令接口。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RedisAsyncCommands<String, String> async(String name) {
        StatefulConnection<String, String> conn = connection(name);
        if (conn instanceof StatefulRedisConnection) {
            return ((StatefulRedisConnection<String, String>) conn).async();
        } else if (conn instanceof StatefulRedisClusterConnection) {
            Object async = ((StatefulRedisClusterConnection) conn).async();
            return (RedisAsyncCommands<String, String>) async;
        }
        throw new IllegalStateException("未知连接类型: " + conn.getClass());
    }

    /**
     * 获取指定连接的字符串编码连接对象（线程安全，可共享）。
     *
     * @param name 连接名，null 或空使用默认连接
     * @return 该连接的 StatefulConnection
     */
    @SuppressWarnings("unchecked")
    public StatefulConnection<String, String> connection(String name) {
        String connName = resolveName(name);
        return stringConnections.computeIfAbsent(connName, this::createStringConnection);
    }

    /**
     * 获取指定连接的字节编码连接对象（用于序列化对象存储）。
     *
     * @param name 连接名，null 或空使用默认连接
     * @return 该连接的字节编码 StatefulConnection
     */
    @SuppressWarnings("unchecked")
    public StatefulConnection<byte[], byte[]> binaryConnection(String name) {
        String connName = resolveName(name);
        return binaryConnections.computeIfAbsent(connName, this::createBinaryConnection);
    }

    /** 解析连接名，null/空返回默认连接 */
    private String resolveName(String name) {
        if (name == null || name.isEmpty()) {
            if (defaultConnection == null) {
                throw new IllegalStateException("未配置任何 Redis 连接");
            }
            return defaultConnection;
        }
        if (!connectionConfigs.containsKey(name)) {
            throw new IllegalArgumentException("未配置的 Redis 连接: " + name
                    + "，可用连接: " + connectionConfigs.keySet());
        }
        return name;
    }

    /** 创建字符串编码连接 */
    private StatefulConnection<String, String> createStringConnection(String name) {
        RedisProperties.ConnectionConfig cfg = connectionConfigs.get(name);
        String clusterMode = options.getCluster();

        if ("cluster".equalsIgnoreCase(clusterMode) && hasClusterNodes(cfg)) {
            RedisClusterClient client = clusterClients.computeIfAbsent(name,
                    n -> RedisClusterClient.create(clientResources, buildClusterUris(cfg)));
            StatefulRedisClusterConnection<String, String> conn = client.connect(StringCodec.UTF8);
            logger.info("[redis] 创建集群字符串连接 '{}'", name);
            return conn;
        } else if ("sentinel".equalsIgnoreCase(clusterMode) && hasSentinels(cfg)) {
            RedisClient client = clients.computeIfAbsent(name,
                    n -> RedisClient.create(clientResources, buildSentinelUri(cfg)));
            StatefulRedisConnection<String, String> conn = client.connect(StringCodec.UTF8);
            logger.info("[redis] 创建哨兵字符串连接 '{}'", name);
            return conn;
        } else {
            RedisClient client = clients.computeIfAbsent(name,
                    n -> RedisClient.create(clientResources, buildStandaloneUri(cfg)));
            StatefulRedisConnection<String, String> conn = client.connect(StringCodec.UTF8);
            logger.info("[redis] 创建单机字符串连接 '{}'", name);
            return conn;
        }
    }

    /** 创建字节编码连接 */
    private StatefulConnection<byte[], byte[]> createBinaryConnection(String name) {
        RedisProperties.ConnectionConfig cfg = connectionConfigs.get(name);
        String clusterMode = options.getCluster();

        if ("cluster".equalsIgnoreCase(clusterMode) && hasClusterNodes(cfg)) {
            RedisClusterClient client = clusterClients.computeIfAbsent(name,
                    n -> RedisClusterClient.create(clientResources, buildClusterUris(cfg)));
            StatefulRedisClusterConnection<byte[], byte[]> conn = client.connect(ByteArrayCodec.INSTANCE);
            logger.info("[redis] 创建集群字节连接 '{}'", name);
            return conn;
        } else if ("sentinel".equalsIgnoreCase(clusterMode) && hasSentinels(cfg)) {
            RedisClient client = clients.computeIfAbsent(name,
                    n -> RedisClient.create(clientResources, buildSentinelUri(cfg)));
            StatefulRedisConnection<byte[], byte[]> conn = client.connect(ByteArrayCodec.INSTANCE);
            logger.info("[redis] 创建哨兵字节连接 '{}'", name);
            return conn;
        } else {
            RedisClient client = clients.computeIfAbsent(name,
                    n -> RedisClient.create(clientResources, buildStandaloneUri(cfg)));
            StatefulRedisConnection<byte[], byte[]> conn = client.connect(ByteArrayCodec.INSTANCE);
            logger.info("[redis] 创建单机字节连接 '{}'", name);
            return conn;
        }
    }

    /** 构建单机 RedisURI */
    private RedisURI buildStandaloneUri(RedisProperties.ConnectionConfig cfg) {
        if (cfg.getUrl() != null && !cfg.getUrl().isEmpty()) {
            return RedisURI.create(cfg.getUrl());
        }
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(cfg.getHost())
                .withPort(cfg.getPort())
                .withDatabase(cfg.getDatabase())
                .withTimeout(Duration.ofMillis(cfg.getTimeoutMs()));
        if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
            if (cfg.getUsername() != null && !cfg.getUsername().isEmpty()) {
                builder.withAuthentication(cfg.getUsername(), cfg.getPassword().toCharArray());
            } else {
                builder.withPassword(cfg.getPassword().toCharArray());
            }
        }
        return builder.build();
    }

    /** 构建哨兵 RedisURI */
    private RedisURI buildSentinelUri(RedisProperties.ConnectionConfig cfg) {
        RedisURI.Builder builder = RedisURI.builder()
                .withSentinelMasterId(cfg.getSentinelMaster())
                .withTimeout(Duration.ofMillis(cfg.getTimeoutMs()));
        for (String node : cfg.getSentinels().split(",")) {
            String[] parts = node.trim().split(":");
            if (parts.length == 2) {
                builder.withSentinel(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            }
        }
        if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
            if (cfg.getUsername() != null && !cfg.getUsername().isEmpty()) {
                builder.withAuthentication(cfg.getUsername(), cfg.getPassword().toCharArray());
            } else {
                builder.withPassword(cfg.getPassword().toCharArray());
            }
        }
        return builder.build();
    }

    /** 构建集群 RedisURI 列表 */
    private List<RedisURI> buildClusterUris(RedisProperties.ConnectionConfig cfg) {
        List<RedisURI> uris = new ArrayList<>();
        for (String node : cfg.getClusterNodes().split(",")) {
            String[] parts = node.trim().split(":");
            if (parts.length == 2) {
                RedisURI.Builder builder = RedisURI.builder()
                        .withHost(parts[0].trim())
                        .withPort(Integer.parseInt(parts[1].trim()))
                        .withTimeout(Duration.ofMillis(cfg.getTimeoutMs()));
                if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
                    if (cfg.getUsername() != null && !cfg.getUsername().isEmpty()) {
                        builder.withAuthentication(cfg.getUsername(), cfg.getPassword().toCharArray());
                    } else {
                        builder.withPassword(cfg.getPassword().toCharArray());
                    }
                }
                uris.add(builder.build());
            }
        }
        return uris;
    }

    private boolean hasClusterNodes(RedisProperties.ConnectionConfig cfg) {
        return cfg.getClusterNodes() != null && !cfg.getClusterNodes().isEmpty();
    }

    private boolean hasSentinels(RedisProperties.ConnectionConfig cfg) {
        return cfg.getSentinels() != null && !cfg.getSentinels().isEmpty()
                && cfg.getSentinelMaster() != null && !cfg.getSentinelMaster().isEmpty();
    }

    /**
     * @return 全局键前缀
     */
    public String getPrefix() {
        return options.getPrefix();
    }

    /**
     * @return 所有已配置的连接名
     */
    public Set<String> connectionNames() {
        return connectionConfigs.keySet();
    }

    /**
     * @return 默认连接名
     */
    public String getDefaultConnection() {
        return defaultConnection;
    }

    /**
     * 优雅关闭所有连接与客户端资源。
     */
    @PreDestroy
    public void shutdown() {
        logger.info("[redis] RedisManager 正在关闭...");
        // 关闭字符串连接
        for (var entry : stringConnections.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                logger.warn("[redis] 关闭字符串连接 '{}' 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        // 关闭字节连接
        for (var entry : binaryConnections.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                logger.warn("[redis] 关闭字节连接 '{}' 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        // 关闭客户端
        for (var entry : clients.entrySet()) {
            try {
                entry.getValue().shutdown();
            } catch (Exception e) {
                logger.warn("[redis] 关闭客户端 '{}' 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        for (var entry : clusterClients.entrySet()) {
            try {
                entry.getValue().shutdown();
            } catch (Exception e) {
                logger.warn("[redis] 关闭集群客户端 '{}' 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        // 关闭共享资源
        try {
            clientResources.shutdown();
        } catch (Exception e) {
            logger.warn("[redis] 关闭客户端资源失败: {}", e.getMessage());
        }
        logger.info("[redis] RedisManager 已关闭");
    }
}
