package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.session.SessionStore;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.redis.RedisManager;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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

    private RedisCommands<String, String> commands() {
        return redisManager.sync(connectionName);
    }

    private String sessionKey(String sessionId) {
        return prefix + ":" + sessionId;
    }

    /** 从当前请求的 Cookie 中获取 Session ID（先做格式白名单校验） */
    private String getSessionId() {
        Request req = RequestFactory.getCurrentRequest();
        if (req == null) return null;
        String cookieValue = req.cookie(cookieName);
        if (cookieValue != null && !cookieValue.isEmpty()) {
            // 白名单：Cookie 值会被直接拼进 Redis 键。若不校验，攻击者可提交任意串
            // （含空白/路径分隔符/超长内容）来构造奇怪键名或做键空间探测；
            // Laravel 同样要求 ^[a-zA-Z0-9,-]{22,250}$ 形状。
            if (!isValidSessionId(cookieValue)) {
                logger.debug("[session-redis] 忽略格式非法的 Session Cookie（将按无会话处理）");
                return null;
            }
            return cookieValue;
        }
        return null;
    }

    /** Session ID 允许的字符与长度（16~128 位字母/数字/下划线/连字符） */
    private static final String SESSION_ID_PATTERN = "[A-Za-z0-9_-]{16,128}";

    /**
     * Session ID 格式白名单校验。
     *
     * @param sessionId 待校验值
     * @return 合法返回 true
     */
    static boolean isValidSessionId(String sessionId) {
        return sessionId != null && sessionId.matches(SESSION_ID_PATTERN);
    }

    /** 生成新的 Session ID */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 将 Session ID 写入响应 Cookie */
    private void setCookie(String sessionId) {
        Request req = RequestFactory.getCurrentRequest();
        if (req != null) {
            Cookie cookie = new Cookie(cookieName, sessionId);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge((int) lifetimeSeconds);
            // SameSite=Lax：跨站请求不携带会话 Cookie，降低 CSRF 面（Servlet 6 起支持设置属性）
            cookie.setAttribute("SameSite", "Lax");
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

    /**
     * 轮换 Session ID（登录成功后调用），对齐 Laravel {@code migrate(true)}：
     * 把当前会话数据搬到新 ID、删除旧 ID、把新 ID 写回请求与响应 Cookie。
     * <p>
     * 关键细节：必须同时用 {@code replaceCookie} 更新<b>请求</b>里的 Cookie 值，
     * 否则同一请求内后续的 {@code getSessionId()} 仍会读到旧 ID，登录态会被写进刚被删除的旧 key。
     */
    @Override
    public void rotate() {
        Request req = RequestFactory.getCurrentRequest();
        String oldId = getSessionId();
        String newId = generateSessionId();
        try {
            RedisCommands<String, String> cmd = commands();
            if (oldId != null && !oldId.isEmpty()) {
                Map<String, String> data = cmd.hgetall(sessionKey(oldId));
                if (data != null && !data.isEmpty()) {
                    cmd.hset(sessionKey(newId), data);
                    cmd.expire(sessionKey(newId), lifetimeSeconds);
                }
                cmd.del(sessionKey(oldId));
            }
            if (req != null) {
                req.replaceCookie(cookieName, newId);
            }
            setCookie(newId);
            logger.debug("[session-redis] 已轮换 Session ID（会话固定防护）");
        } catch (Exception e) {
            logger.error("[session-redis] 轮换 Session ID 失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        String sessionId = getSessionId();
        // 让浏览器立即丢弃会话 Cookie：只删服务端数据的话，Cookie 仍在客户端，
        // 表现为「退出后请求仍带着同一个 session id」。
        Request req = RequestFactory.getCurrentRequest();
        if (req != null) {
            Cookie expired = new Cookie(cookieName, "");
            expired.setPath("/");
            expired.setMaxAge(0);
            req.replaceCookie(cookieName, "");
            req.addCookie(expired);
        }
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
