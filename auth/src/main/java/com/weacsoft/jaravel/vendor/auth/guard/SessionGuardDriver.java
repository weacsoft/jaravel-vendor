package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.SessionStore;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import java.util.List;
import java.util.Map;

/**
 * Session 守卫驱动，支持 {@code session} 认证方式。
 * <p>
 * 采用工厂模式：通过 {@link #support(String)} 声明支持的驱动名称，
 * 通过 {@link #create} 创建 {@link SessionGuard} 实例。
 * <p>
 * Session 存储后端通过 {@link SessionStore} 接口抽象，支持 cookie（默认）、redis、file 等。
 * 配置中通过 {@code sessionStore} 参数指定存储后端名称，默认为 {@code cookie}。
 * <p>
 * 本驱动由 auth 模块的 {@code AuthAutoConfiguration} 注册为 Bean，
 * 再自动收集并注册到 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}。
 *
 * <h3>配置示例</h3>
 * <pre>
 * // 编程式注册
 * authManager.registerGuard("web", "session", "users");                    // 默认 cookie 存储
 * authManager.registerGuard("web", "session", "users", "redis");           // 使用 redis 存储
 *
 * // 配置式注册
 * jaravel:
 *   auth:
 *     guards:
 *       web:
 *         driver: session
 *         provider: users
 *         session-store: redis     # 不指定则默认 cookie
 * </pre>
 */
public class SessionGuardDriver implements AuthGuardDriver {

    private static final String DEFAULT_STORE = "cookie";

    /** 所有已注册的 Session 存储（由 AuthAutoConfiguration 注入） */
    private final List<SessionStore> sessionStores;

    public SessionGuardDriver(List<SessionStore> sessionStores) {
        this.sessionStores = sessionStores;
    }

    @Override
    public boolean support(String driver) {
        return "session".equalsIgnoreCase(driver);
    }

    @Override
    public AuthGuard create(String name, UserProvider provider, Map<String, Object> config) {
        String storeName = DEFAULT_STORE;
        if (config != null) {
            Object store = config.get("sessionStore");
            if (store instanceof String s && !s.isEmpty()) {
                storeName = s;
            }
        }

        SessionStore sessionStore = resolveStore(storeName);
        return new SessionGuard(name, provider, sessionStore);
    }

    /**
     * 按名称查找匹配的 SessionStore。
     * <p>
     * 遍历所有已注册的 SessionStore，调用 {@link SessionStore#support(String)} 匹配。
     * 未找到时回退到默认的 cookie 存储（如果存在），否则抛出异常。
     *
     * @param storeName 存储名称（如 cookie / redis）
     * @return 匹配的 SessionStore
     * @throws IllegalStateException 没有匹配的存储时
     */
    private SessionStore resolveStore(String storeName) {
        // 先精确匹配
        for (SessionStore store : sessionStores) {
            if (store.support(storeName)) {
                return store;
            }
        }
        // 回退到默认 cookie
        for (SessionStore store : sessionStores) {
            if (store.support(DEFAULT_STORE)) {
                return store;
            }
        }
        throw new IllegalStateException(
                "未找到匹配的 Session 存储: " + storeName + "，请引入对应插件（如 session-redis 模块）");
    }
}
