package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Session 守卫，对齐 Laravel 的 {@code SessionGuard}。
 * <p>
 * 登录态写入 HTTP Session，用户信息按需通过 {@link UserProvider} 取出并缓存于当前线程。
 * <p>
 * <b>线程安全</b>：本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal
 * 按请求隔离，{@code cachedUser}、{@code resolved} 为请求级状态，不跨请求共享。
 */
public class SessionGuard implements AuthGuard {

    private final String name;
    private final UserProvider provider;
    private Authenticatable cachedUser;
    private boolean resolved = false;

    public SessionGuard(String name, UserProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    private String sessionKey() {
        return "login_" + name + "_id";
    }

    private HttpSession session() {
        Request req = AuthContext.get();
        if (req == null) return null;
        HttpServletRequest servlet = req.getRequest();
        if (servlet == null) return null;
        return servlet.getSession(false);
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
        HttpSession session = session();
        if (session == null) return null;
        Object id = session.getAttribute(sessionKey());
        if (id == null) return null;
        cachedUser = provider.retrieveById(id);
        return cachedUser;
    }

    @Override
    public void login(Authenticatable user) {
        cachedUser = user;
        resolved = true;
        Request req = AuthContext.get();
        if (req != null) {
            HttpServletRequest servlet = req.getRequest();
            if (servlet != null) {
                HttpSession session = servlet.getSession(true);
                session.setAttribute(sessionKey(), user.getAuthIdentifier());
            }
        }
    }

    @Override
    public void logout() {
        cachedUser = null;
        resolved = true;
        HttpSession session = session();
        if (session != null) {
            session.removeAttribute(sessionKey());
        }
    }
}
