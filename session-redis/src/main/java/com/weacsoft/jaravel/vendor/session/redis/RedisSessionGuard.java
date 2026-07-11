package com.weacsoft.jaravel.vendor.session.redis;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.http.Cookie;

/**
 * Redis Session 守卫，对齐 Laravel 的 {@code SessionGuard}，但 Session 后端为 Redis。
 * <p>
 * 登录态写入 Redis Session（通过 {@link RedisSessionStore}），用户信息按需通过
 * {@link UserProvider} 取出并缓存于当前线程。
 * <p>
 * <b>多机同步</b>：由于 Session 数据存储在共享的 Redis 中，用户在任一节点登录后，
 * 其他节点可通过同一 Session ID（从 Cookie 获取）读取登录态，实现多机 Session 同步。
 *
 * <h3>Session ID 流转</h3>
 * <ol>
 *   <li>请求到达时，从 Cookie 中读取 Session ID（Cookie 名由 {@link RedisSessionStore#getCookieName()} 指定）</li>
 *   <li>若 Cookie 中无 Session ID，则不创建新 Session（惰性创建，仅在 login 时生成）</li>
 *   <li>login 时生成新 Session ID，写入 Redis，并通过 Cookie 返回给客户端</li>
 *   <li>logout 时销毁 Redis 中的 Session 数据</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * 本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal
 * 按请求隔离，{@code cachedUser}、{@code resolved}、{@code sessionId} 为请求级状态，不跨请求共享。
 */
public class RedisSessionGuard implements AuthGuard {

    private final String name;
    private final UserProvider provider;
    private final RedisSessionStore sessionStore;

    /** 请求级缓存的用户 */
    private Authenticatable cachedUser;
    /** 是否已解析过用户 */
    private boolean resolved = false;
    /** 请求级缓存的 Session ID */
    private String sessionId;

    /**
     * @param name         守卫名称（如 web / wechat / admin）
     * @param provider     用户提供者
     * @param sessionStore Redis Session 存储
     */
    public RedisSessionGuard(String name, UserProvider provider, RedisSessionStore sessionStore) {
        this.name = name;
        this.provider = provider;
        this.sessionStore = sessionStore;
    }

    /** Session 属性键，对齐 Laravel {@code login_<guard>_id} */
    private String sessionKey() {
        return "login_" + name + "_id";
    }

    /** 从当前请求的 Cookie 中获取 Session ID */
    private String getSessionId() {
        if (sessionId != null) {
            return sessionId;
        }
        Request req = AuthContext.get();
        if (req == null) {
            return null;
        }
        String cookieValue = req.cookie(sessionStore.getCookieName());
        if (cookieValue != null && !cookieValue.isEmpty()) {
            sessionId = cookieValue;
        }
        return sessionId;
    }

    @Override
    public boolean check() {
        return user() != null;
    }

    @Override
    public boolean guest() {
        return !check();
    }

    @Override
    public Authenticatable user() {
        if (resolved) return cachedUser;
        resolved = true;

        String sid = getSessionId();
        if (sid == null) return null;

        Object id = sessionStore.get(sid, sessionKey());
        if (id == null) return null;

        cachedUser = provider.retrieveById(id);
        return cachedUser;
    }

    @Override
    public void login(Authenticatable user) {
        cachedUser = user;
        resolved = true;

        // 生成新的 Session ID（或复用已有的）
        String sid = getSessionId();
        if (sid == null || !sessionStore.exists(sid)) {
            sid = sessionStore.generateSessionId();
            sessionId = sid;
        }

        // 写入登录态到 Redis Session
        sessionStore.put(sid, sessionKey(), user.getAuthIdentifier());

        // 通过 Cookie 返回 Session ID 给客户端
        Request req = AuthContext.get();
        if (req != null) {
            Cookie cookie = new Cookie(sessionStore.getCookieName(), sid);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge((int) sessionStore.getLifetimeSeconds());
            req.addCookie(cookie);
        }
    }

    @Override
    public void logout() {
        cachedUser = null;
        resolved = true;

        String sid = getSessionId();
        if (sid != null) {
            sessionStore.remove(sid, sessionKey());
        }
    }

    @Override
    public String token() {
        // Session 守卫不产生 token，仅 JWT 守卫支持
        return null;
    }
}
