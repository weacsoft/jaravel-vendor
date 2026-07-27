package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Redis Session 存储，实现 {@link SessionStore} 接口，对齐 Laravel {@code RedisSessionHandler}。
 * <p>
 * 将 Session 数据以 Hash 结构存储在 Redis 中，键格式为 {@code <prefix>:<sessionId>}，
 * TTL 为 Session 生命周期（分钟级）。所有应用实例共享同一 Redis，天然实现多机 Session 同步。
 *
 * <h3>Session ID 管理</h3>
 * <ul>
 *   <li>Session ID 通过 Cookie 传递（Cookie 名由配置指定，如 {@code manage_session}）</li>
 *   <li>首次访问时不创建新 Session（惰性创建，仅在 {@link #put} 时生成）</li>
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
 * 本类为无状态单例，通过 {@link AuthContext} 获取当前请求上下文。
 * Redis 命令本身是原子的，多线程并发读写同一 Session 时通过 Redis 保证一致性。
 */
public class RedisSessionStore implements SessionStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionStore.class);

    private final RedisManager redisManager;
    private final String connectionName;
    private final String prefix;
    private final long lifetimeSeconds;
    private final String cookieName;

    public RedisSessionStore(RedisManager redisManager, String connectionName,
                             String prefix, long lifetimeMinutes, String cookieName) {
        this.redisManager = redisManager;
        this.connectionName = connectionName;
        this.prefix = prefix;
        this.lifetimeSeconds = lifetimeMinutes * 60;
        this.cookieName = cookieName;
    }

    @Override
    public boolean support(String store) {
        return "redis".equalsIgnoreCase(store);
    }

    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    private String sessionKey(String sessionId) {
        return prefix + ":" + sessionId;
    }

    /** 从当前请求的 Cookie 中获取 Session ID */
    private String getSessionId() {
        Request req = AuthContext.get();
        if (req == null) return null;
        String cookieValue = req.cookie(cookieName);
        if (cookieValue != null && !cookieValue.isEmpty()) {
            return cookieValue;
        }
        return null;
    }

    /** 生成新的 Session ID */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 将 Session ID 写入响应 Cookie */
    private void setCookie(String sessionId) {
        Request req = AuthContext.get();
        if (req != null) {
            Cookie cookie = new Cookie(cookieName, sessionId);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge((int) lifetimeSeconds);
            req.addCookie(cookie);
        }
    }

    @Override
    public Object get(String key) {
        String sessionId = getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        try {
            RedisCommands<String, String> cmd = commands();
            String raw = cmd.hget(sessionKey(sessionId), key);
            if (raw == null) {
                return null;
            }
            // 刷新 TTL（滑动过期）
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
            return deserialize(raw);
        } catch (Exception e) {
            logger.error("[session-redis] 读取 Session 属性失败 sessionId={} key={}: {}", sessionId, key, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, Object value) {
        String sessionId = getSessionId();
        // 惰性创建：无 Session 时生成新 Session ID 并设置 Cookie
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = generateSessionId();
            setCookie(sessionId);
        }
        try {
            RedisCommands<String, String> cmd = commands();
            cmd.hset(sessionKey(sessionId), key, serialize(value));
            cmd.expire(sessionKey(sessionId), lifetimeSeconds);
        } catch (Exception e) {
            logger.error("[session-redis] 写入 Session 属性失败 sessionId={} key={}: {}", sessionId, key, e.getMessage());
        }
    }

    @Override
    public void remove(String key) {
        String sessionId = getSessionId();
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

    @Override
    public void destroy() {
        String sessionId = getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            commands().del(sessionKey(sessionId));
        } catch (Exception e) {
            logger.error("[session-redis] 销毁 Session 失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /** 序列化对象为 JSON 字符串 */
    private String serialize(Object value) {
        try {
            return Json.stringify(value);
        } catch (Exception e) {
            return value != null ? value.toString() : "null";
        }
    }

    /** 反序列化 JSON 字符串为 Java 对象 */
    private Object deserialize(String json) {
        try {
            return Json.parse(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
