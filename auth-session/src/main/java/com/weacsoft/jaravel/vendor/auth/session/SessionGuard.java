package com.weacsoft.jaravel.vendor.auth.session;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.session.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;

/**
 * Session 守卫，对齐 Laravel 的 {@code SessionGuard}。
 * <p>
 * 登录态通过 {@link SessionStore} 存储后端读写，支持 cookie（Servlet HttpSession）、redis 等存储。
 * 用户信息按需通过 {@link UserProvider} 取出并缓存于当前线程。
 * <p>
 * <b>线程安全</b>：本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal
 * 按请求隔离，{@code cachedUser}、{@code resolved} 为请求级状态，不跨请求共享。
 * {@link SessionStore} 为无状态单例，通过 {@link com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory} 获取当前请求上下文。
 */
public class SessionGuard implements AuthGuard {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(SessionGuard.class);

    /** 「实现未覆盖 rotate」的告警去重（按实现类名，只告警一次） */
    private static final java.util.Set<String> ROTATE_UNSUPPORTED_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final String name;
    private final UserProvider provider;
    private final SessionStore sessionStore;

    private Authenticatable cachedUser;
    private boolean resolved = false;

    /**
     * 便捷构造器：name 默认 {@code "web"}。
     *
     * @param provider     用户提供者
     * @param sessionStore Session 存储后端（cookie / redis 等）
     */
    public SessionGuard(UserProvider provider, SessionStore sessionStore) {
        this("web", provider, sessionStore);
    }

    /**
     * @param name         守卫名称（如 web / admin）
     * @param provider     用户提供者
     * @param sessionStore Session 存储后端（cookie / redis 等）
     */
    public SessionGuard(String name, UserProvider provider, SessionStore sessionStore) {
        this.name = name;
        this.provider = provider;
        this.sessionStore = sessionStore;
    }

    /** Session 属性键，对齐 Laravel {@code login_<guard>_id} */
    private String sessionKey() {
        return "login_" + name + "_id";
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
    @SuppressWarnings("unchecked")
    public <T extends Authenticatable> T user() {
        if (resolved) return (T) cachedUser;
        resolved = true;

        Object id = sessionStore.get(sessionKey());
        if (id == null) return null;

        cachedUser = provider.retrieveById(id);
        return (T) cachedUser;
    }

    @Override
    public void login(Authenticatable user) {
        // 会话固定防护（CWE-384）：先把 Session ID 轮换掉，再把登录态写入新会话。
        // 顺序不可颠倒 —— 反了会把登录态写进马上要被删除的旧会话，登录随即失效。
        warnIfRotationUnsupported();
        sessionStore.rotate();
        cachedUser = user;
        resolved = true;
        sessionStore.put(sessionKey(), user.getAuthIdentifier());
    }

    /**
     * 一次性告警：若某个 {@code SessionStore} 实现没有覆盖 {@code rotate()}（接口默认空实现），
     * 登录时的会话固定防护会被静默跳过，必须让运维看得见。
     */
    private void warnIfRotationUnsupported() {
        try {
            Class<?> declaring = sessionStore.getClass().getMethod("rotate").getDeclaringClass();
            if (declaring == SessionStore.class
                    && ROTATE_UNSUPPORTED_WARNED.add(sessionStore.getClass().getName())) {
                logger.warn("[auth] SessionStore 实现 {} 未覆盖 rotate()：登录时无法轮换 Session ID，"
                        + "存在会话固定风险（CWE-384），请为该实现补上 rotate()",
                        sessionStore.getClass().getName());
            }
        } catch (NoSuchMethodException ignored) {
            // rotate 是接口默认方法，正常不会走到这里
        }
    }

    @Override
    public void logout() {
        cachedUser = null;
        resolved = true;
        sessionStore.remove(sessionKey());
    }
}
