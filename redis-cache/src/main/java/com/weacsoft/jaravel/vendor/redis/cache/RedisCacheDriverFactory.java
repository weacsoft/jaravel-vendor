package com.weacsoft.jaravel.vendor.redis.cache;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;
import com.weacsoft.jaravel.vendor.redis.RedisManager;

import java.util.Map;

/**
 * Redis 缓存驱动工厂，支持 {@code "redis"} 驱动名。
 * <p>
 * 需要注入 {@link RedisManager}，从 store 配置中读取 {@code connection}（Redis 连接名），
 * 若 store 配置未指定则回退到构造时传入的默认连接名（通常来自 {@code jaravel.cache.redis.connection}），
 * 创建 {@link RedisCacheDriver}。
 * <p>
 * 由 {@code RedisCacheAutoConfiguration} 在 RedisManager 存在时注册为 Bean，
 * {@code CacheManager} 通过 {@code CacheDriverFactory} 自动收集并按需创建 redis store。
 */
public class RedisCacheDriverFactory implements CacheDriverFactory {

    private final RedisManager redisManager;

    /** 默认 Redis 连接名（store 配置未指定 connection 时回退到此值） */
    private final String defaultConnection;

    /**
     * 构造 Redis 缓存驱动工厂，默认连接名为 {@code cache}。
     *
     * @param redisManager Redis 管理器
     */
    public RedisCacheDriverFactory(RedisManager redisManager) {
        this(redisManager, "cache");
    }

    /**
     * 构造 Redis 缓存驱动工厂。
     *
     * @param redisManager      Redis 管理器
     * @param defaultConnection 默认 Redis 连接名（store 配置未指定 connection 时回退到此值）
     */
    public RedisCacheDriverFactory(RedisManager redisManager, String defaultConnection) {
        this.redisManager = redisManager;
        this.defaultConnection = defaultConnection != null ? defaultConnection : "cache";
    }

    @Override
    public boolean support(String driver) {
        return "redis".equalsIgnoreCase(driver);
    }

    @Override
    public CacheDriver create(Map<String, Object> config) {
        Object connection = config.get("connection");
        String connectionName = (connection != null && !connection.toString().isEmpty())
                ? connection.toString()
                : defaultConnection;
        return new RedisCacheDriver(redisManager, connectionName);
    }
}
