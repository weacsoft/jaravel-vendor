package com.weacsoft.jaravel.vendor.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis 配置属性，前缀 {@code jaravel.redis}，对齐 Laravel {@code config/database.php} 的 redis 段。
 * <p>
 * 支持多命名连接（default / cache / session / model-cache 等），每个连接独立 host/port/database/password，
 * 生产环境通过同一 Redis 实例不同 database 或不同实例实现隔离。
 *
 * <pre>
 * jaravel:
 *   redis:
 *     client: lettuce              # 客户端实现，目前仅支持 lettuce
 *     options:
 *       cluster: redis             # 集群模式：redis(单机) / cluster / sentinel
 *       prefix: "manage_database_" # 全局键前缀
 *     connections:
 *       default:
 *         host: 127.0.0.1
 *         port: 6379
 *         username: ""
 *         password: ""
 *         database: 0
 *       cache:
 *         host: 127.0.0.1
 *         port: 6379
 *         database: 1
 *       session:
 *         host: 127.0.0.1
 *         port: 6379
 *         database: 2
 *       model-cache:
 *         host: 127.0.0.1
 *         port: 6379
 *         database: 3
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.redis")
public class RedisProperties {

    /** 客户端实现名称，目前仅支持 lettuce */
    private String client = "lettuce";

    /** 全局选项 */
    private Options options = new Options();

    /** 命名连接映射：name -> connection config，对齐 Laravel redis.default / redis.cache 等 */
    private Map<String, ConnectionConfig> connections = new LinkedHashMap<>();

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public Map<String, ConnectionConfig> getConnections() {
        return connections;
    }

    public void setConnections(Map<String, ConnectionConfig> connections) {
        this.connections = connections;
    }

    /**
     * 全局 Redis 选项，对齐 Laravel {@code redis.options}。
     */
    public static class Options {

        /** 集群模式：redis(单机/默认) / cluster / sentinel */
        private String cluster = "redis";

        /** 全局键前缀，对齐 Laravel REDIS_PREFIX */
        private String prefix = "";

        public String getCluster() {
            return cluster;
        }

        public void setCluster(String cluster) {
            this.cluster = cluster;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * 单个 Redis 连接配置，对齐 Laravel {@code redis.default} / {@code redis.cache} 等。
     * <p>
     * 支持 url 形式（redis://[user:password@]host:port/db）或独立字段配置。
     */
    public static class ConnectionConfig {

        /** Redis URL，如 redis://localhost:6379/0，设置后覆盖 host/port/database */
        private String url;

        /** Redis 主机 */
        private String host = "127.0.0.1";

        /** Redis 端口 */
        private int port = 6379;

        /** 用户名（Redis 6+ ACL），空表示不使用 */
        private String username = "";

        /** 密码，空表示无密码 */
        private String password = "";

        /** 数据库编号，0-15 */
        private int database = 0;

        /** 连接超时毫秒，默认 2000 */
        private int timeoutMs = 2000;

        /** 哨兵模式主节点名称，仅 sentinel 模式使用 */
        private String sentinelMaster = "";

        /** 哨兵节点列表，仅 sentinel 模式使用，格式 host:port,host:port */
        private String sentinels = "";

        /** 集群节点列表，仅 cluster 模式使用，格式 host:port,host:port */
        private String clusterNodes = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getSentinelMaster() {
            return sentinelMaster;
        }

        public void setSentinelMaster(String sentinelMaster) {
            this.sentinelMaster = sentinelMaster;
        }

        public String getSentinels() {
            return sentinels;
        }

        public void setSentinels(String sentinels) {
            this.sentinels = sentinels;
        }

        public String getClusterNodes() {
            return clusterNodes;
        }

        public void setClusterNodes(String clusterNodes) {
            this.clusterNodes = clusterNodes;
        }
    }
}
