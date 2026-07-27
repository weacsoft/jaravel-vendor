package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;

/**
 * Session 守卫，对齐 Laravel 的 {@code SessionGuard}。
 * <p>
 * 登录态通过 {@link SessionStore} 存储后端读写，支持 cookie（Servlet HttpSession）、redis 等存储。
 * 用户信息按需通过 {@link UserProvider} 取出并缓存于当前线程。
 * <p>
 * <b>线程安全</b>：本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal
 * 按请求隔离，{@code cachedUser}、{@code resolved} 为请求级状态，不跨请求共享。
 * {@link SessionStore} 为无状态单例，通过 {@link com.weacsoft.jaravel.vendor.auth.AuthContext} 获取当前请求上下文。
 */
public class SessionGuard implements AuthGuard {

    private final String name;
    private final UserProvider provider;
    private final SessionStore sessionStore;

    private Authenticatable cachedUser;
    private boolean resolved = false;

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
    public Authenticatable user() {
        if (resolved) return cachedUser;
        resolved = true;

        Object id = sessionStore.get(sessionKey());
        if (id == null) return null;

        cachedUser = provider.retrieveById(id);
        return cachedUser;
    }

    @Override
    public void login(Authenticatable user) {
        cachedUser = user;
        resolved = true;
        sessionStore.put(sessionKey(), user.getAuthIdentifier());
    }

    @Override
    public void logout() {
        cachedUser = null;
        resolved = true;
        sessionStore.remove(sessionKey());
    }
}
