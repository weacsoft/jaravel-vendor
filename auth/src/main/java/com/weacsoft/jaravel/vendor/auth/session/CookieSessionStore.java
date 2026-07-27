package com.weacsoft.jaravel.vendor.auth.session;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Cookie Session 存储（默认），使用 Servlet 容器的 HttpSession。
 * <p>
 * 这是 SpringBoot 默认的 Session 实现方式：Servlet 容器（如 Tomcat）通过 Cookie（默认名 {@code JSESSIONID}）
 * 管理 Session ID，Session 数据存储在服务器内存中。
 * <p>
 * 对齐 Laravel 的 {@code cookie} session driver。
 * <p>
 * 本类由 auth 模块的 {@code AuthAutoConfiguration} 注册为 Bean。
 *
 * <h3>线程安全</h3>
 * 本类为无状态单例，通过 {@link AuthContext} 获取当前请求的 {@link HttpServletRequest}，
 * 每个请求线程持有独立的 Servlet Session，天然隔离。
 */
public class CookieSessionStore implements SessionStore {

    @Override
    public boolean support(String store) {
        return "cookie".equalsIgnoreCase(store);
    }

    /** 获取当前请求的 HttpSession（不自动创建） */
    private HttpSession session(boolean create) {
        Request req = AuthContext.get();
        if (req == null) return null;
        HttpServletRequest servlet = req.getRequest();
        if (servlet == null) return null;
        return servlet.getSession(create);
    }

    @Override
    public Object get(String key) {
        HttpSession session = session(false);
        if (session == null) return null;
        return session.getAttribute(key);
    }

    @Override
    public void put(String key, Object value) {
        HttpSession session = session(true);
        if (session != null) {
            session.setAttribute(key, value);
        }
    }

    @Override
    public void remove(String key) {
        HttpSession session = session(false);
        if (session != null) {
            session.removeAttribute(key);
        }
    }

    @Override
    public void destroy() {
        HttpSession session = session(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
