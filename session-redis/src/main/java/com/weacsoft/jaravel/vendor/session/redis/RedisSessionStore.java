package com.weacsoft.jaravel.vendor.session.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Session 存储，对齐 Laravel {@code RedisSessionHandler}。
 * <p>
 * 将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 {@code <prefix>:<sessionId>}，
 * TTL 为 Session 生命周期（分钟级）。所有应用实例共享同一 Redis，天然实现多机 Session 同步。
 *
 * <h3>Session ID 管理</h3>
 * <ul>
 *   <li>Session ID 通过 Cookie 传递（Cookie 名由配置指定，如 {@code manage_session}）</li>
 *   <li>首次访问时生成新的 UUID 作为 Session ID</li>
 *   <li>每次读写都会刷新 TTL，实现滑动过期</li>
 * </ul>
 *
 * <h3>存储格式</h3>
 * Session 数据以 Redis Hash 存储，每个属性为一个 Hash field：
 * <pre>
 * HSET <prefix>:<sessionId> login_web_id "12345" login_wechat_id "67890"
 * EXPIRE <prefix>:<sessionId> 1800
 * </pre>
 *
 * <h3>线程安全</h3>
 * Redis 命令本身是原子的，多线程并发读写同一 Session 时通过 Redis 保证一致性。
 * 本类的 {@link #getSessionId()} 在请求级别缓存 Session ID，避免重复生成。
 */
public class RedisSessionStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionStore.class);

    /** Redis 管理器 */
    private final RedisManager redisManager;

    /** Redis 连接名（如 session），对应 jaravel.redis.connections 中的配置 */
    private final String connectionName;

    /** Session 键前缀，对齐 Laravel Session 前缀 */
    private final String prefix;

    /** Session 生命周期（秒），默认 30 分钟 */
    private final long lifetimeSeconds;

    /** Cookie 名称，用于传递 Session ID */
    private final String cookieName;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造 Redis Session 存储。
     *
     * @param redisManager    Redis 管理器
     * @param connectionName  Redis 连接名，null 使用默认连接
     * @param prefix          Session 键前缀
     * @param lifetimeMinutes Session 生命周期（分钟）
     * @param cookieName      Cookie 名称
     */
    public RedisSessionStore(RedisManager redisManager, String connectionName,
                             String prefix, long lifetimeMinutes, String cookieName) {
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        this.prefix = prefix;
        this.lifetimeSeconds = lifetimeMinutes * 60;
        this.cookieName = cookieName;
        this.objectMapper = new ObjectMapper();
    }

    /** 获取 Redis 同步命令接口 */
    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    /** 构建 Redis 中的 Session 键 */
    private String sessionKey(String sessionId) {
        return prefix + ":" + sessionId;
    }

    /**
     * 生成新的 Session ID。
     *
     * @return 新的 UUID Session ID
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 读取 Session 中的所有属性。
     *
     * @param sessionId Session ID，null 返回空 Map
     * @return 属性名 -> 属性值（反序列化后的 Java 对象）
     */
    public Map<String, Object> getAll(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return new HashMap<>();
        }
        try {
            RedisCommands<String, String> cmd = commands();
            Map<String, String> raw = cmd.hgetall(sessionKey(sessionId));
            if (raw == null || raw.isEmpty()) {
                return new HashMap<>();
            }
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                result.put(entry.getKey(), deserialize(entry.getValue()));
            }
            // 刷新 TTL（滑动过期）
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
            return result;
        } catch (Exception e) {
            logger.error("[session-redis] 读取 Session 失败 sessionId={}: {}", sessionId, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 读取 Session 中的单个属性。
     *
     * @param sessionId Session ID
     * @param key       属性名
     * @return 属性值，不存在返回 null
     */
    public Object get(String sessionId, String key) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        try {
            RedisCommands<String, String> cmd = commands();
            String raw = cmd.hget(sessionKey(sessionId), key);
            if (raw == null) {
                return null;
            }
            // 刷新 TTL
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
            return deserialize(raw);
        } catch (Exception e) {
            logger.error("[session-redis] 读取 Session 属性失败 sessionId={} key={}: {}", sessionId, key, e.getMessage());
            return null;
        }
    }

    /**
     * 写入 Session 属性。
     *
     * @param sessionId Session ID
     * @param key       属性名
     * @param value     属性值
     */
    public void put(String sessionId, String key, Object value) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            RedisCommands<String, String> cmd = commands();
            cmd.hset(sessionKey(sessionId), key, serialize(value));
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
        } catch (Exception e) {
            logger.error("[session-redis] 写入 Session 属性失败 sessionId={} key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /**
     * 移除 Session 属性。
     *
     * @param sessionId Session ID
     * @param key       属性名
     */
    public void remove(String sessionId, String key) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            RedisCommands<String, String> cmd = commands();
            cmd.hdel(sessionKey(sessionId), key);
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
        } catch (Exception e) {
            logger.error("[session-redis] 移除 Session 属性失败 sessionId={} key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /**
     * 销毁整个 Session。
     *
     * @param sessionId Session ID
     */
    public void destroy(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            commands().del(sessionKey(sessionId));
        } catch (Exception e) {
            logger.error("[session-redis] 销毁 Session 失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 检查 Session 是否存在且未过期。
     *
     * @param sessionId Session ID
     * @return 是否存在
     */
    public boolean exists(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        try {
            return commands().exists(sessionKey(sessionId)) > 0;
        } catch (Exception e) {
            logger.error("[session-redis] 检查 Session 存在失败 sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * @return Cookie 名称
     */
    public String getCookieName() {
        return cookieName;
    }

    /**
     * @return Session 生命周期（秒）
     */
    public long getLifetimeSeconds() {
        return lifetimeSeconds;
    }

    /** 序列化对象为 JSON 字符串 */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value != null ? value.toString() : "null";
        }
    }

    /** 反序列化 JSON 字符串为 Java 对象 */
    private Object deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return json;
        }
    }
}
