package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import java.util.Map;

/**
 * Session 守卫驱动，支持 {@code session} 认证方式。
 * <p>
 * 采用工厂模式：通过 {@link #support(String)} 声明支持的驱动名称，
 * 通过 {@link #create} 创建 {@link SessionGuard} 实例。
 * <p>
 * <b>Session 存储是全局配置，不与 Guard 绑定</b>。本驱动直接注入唯一的 {@link SessionStore} Bean，
 * 该 Bean 由应用的 {@code config/SessionConfig.java} 决定具体实现。
 * 如果应用未注册任何 {@code SessionStore} Bean，auth 模块默认提供
 * {@link com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore}（Servlet HttpSession）。
 * <p>
 * 本驱动由 auth 模块的 {@code AuthAutoConfiguration} 注册为 Bean，
 * 再自动收集并注册到 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}。
 *
 * <h3>配置示例</h3>
 * <pre>
 * // 编程式注册守卫（不涉及 session 存储，存储由 SessionConfig 全局决定）
 * authManager.registerGuard("web", "session", "users");
 * authManager.registerGuard("api", "jwt", "users");
 * </pre>
 *
 * <h3>切换 Session 存储</h3>
 * 在应用的 {@code config/SessionConfig.java} 中注册 {@code SessionStore} Bean 即可覆盖默认实现：
 * <pre>
 * &#64;Configuration
 * public class SessionConfig {
 *     &#64;Bean
 *     public SessionStore sessionStore(RedisManager redisManager) {
 *         return new RedisSessionStore(redisManager, "default", "session", 30, "manage_session");
 *     }
 * }
 * </pre>
 */
public class SessionGuardDriver implements AuthGuardDriver {

    /** 全局唯一的 Session 存储（由 Spring 注入，由 SessionConfig 决定具体实现） */
    private final SessionStore sessionStore;

    public SessionGuardDriver(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean support(String driver) {
        return "session".equalsIgnoreCase(driver);
    }

    @Override
    public AuthGuard create(String name, UserProvider provider, Map<String, Object> config) {
        return new SessionGuard(name, provider, sessionStore);
    }
}
