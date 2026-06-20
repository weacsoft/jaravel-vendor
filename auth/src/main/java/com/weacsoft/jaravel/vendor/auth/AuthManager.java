package com.weacsoft.jaravel.vendor.auth;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.GuardFactory;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.guard.SessionGuard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证管理器，对齐 Laravel {@code AuthManager}。
 * <p>
 * 维护多个守卫（guard）与用户提供者（provider），按名称解析守卫实例（请求级缓存于 ThreadLocal）。
 * 支持通过 {@link #registerGuardDriver(String, GuardFactory)} 插件式注册新的 Guard 驱动，
 * 第三方模块（如 jwt 模块）可在不修改本类的前提下扩展支持的 driver 类型。
 *
 * <h3>线程安全说明</h3>
 * <ul>
 *   <li><b>注册表（guards / providers / driverFactories）</b>：使用 {@link ConcurrentHashMap}，
 *       支持并发读写。注册阶段（应用启动时由 ServiceProvider 调用 {@code registerProvider} /
 *       {@code registerGuard} / {@code registerGuardDriver}）与运行阶段（请求线程调用
 *       {@code guard(name)}）可安全并发。注册表本身是进程级共享的不可变配置
 *       （启动后不再修改），ConcurrentHashMap 保证可见性与原子性。</li>
 *   <li><b>请求级守卫实例（current）</b>：使用 {@link ThreadLocal}，每个请求线程持有独立的
 *       {@code Map<String, AuthGuard>}，因此 {@link AuthGuard} 实例（如 {@link SessionGuard}、
 *       JwtGuard）中缓存的 {@code cachedUser}、{@code resolved}、{@code lastToken} 等可变状态
 *       天然按请求隔离，<b>不会</b>跨请求共享。请求结束时由
 *       {@link com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter} 调用 {@link #clear()}
 *       清理 ThreadLocal，防止线程池复用导致的串态。</li>
 *   <li><b>defaultGuard</b>：启动阶段设置后不再变更，使用 {@code volatile} 保证可见性。</li>
 * </ul>
 * <p>
 * <b>关键约束</b>：{@link AuthGuard} 实例<b>必须</b>通过 {@link #guard(String)} 获取，
 * 不可跨请求缓存或共享，否则其内部的可变状态会串态。
 */
public class AuthManager {

    /** 守卫配置：name -> {driver, providerName}，进程级共享，启动后只读 */
    private final Map<String, GuardConfig> guards = new ConcurrentHashMap<>();
    /** 提供者：name -> UserProvider，进程级共享，启动后只读 */
    private final Map<String, UserProvider> providers = new ConcurrentHashMap<>();
    /** 插件式驱动工厂：driver(lowercase) -> GuardFactory，进程级共享，启动后只读 */
    private final Map<String, GuardFactory> driverFactories = new ConcurrentHashMap<>();
    /** 请求级守卫实例：name -> AuthGuard，每线程独立，请求结束清理 */
    private final ThreadLocal<Map<String, AuthGuard>> current = ThreadLocal.withInitial(ConcurrentHashMap::new);

    private volatile String defaultGuard = "web";

    public void setDefaultGuard(String name) {
        this.defaultGuard = name;
    }

    public String getDefaultGuard() {
        return defaultGuard;
    }

    /** 注册用户提供者（应用启动阶段调用） */
    public void registerProvider(String name, UserProvider provider) {
        providers.put(name, provider);
    }

    /** 注册守卫（应用启动阶段调用） */
    public void registerGuard(String name, String driver, String providerName) {
        guards.put(name, new GuardConfig(driver, providerName));
    }

    /**
     * 注册 Guard 驱动工厂，允许插件模块扩展支持的 driver 类型。
     * <p>
     * 例如 jwt 模块在自动装配时调用：
     * <pre>
     * authManager.registerGuardDriver("jwt", (name, provider, config) -> new JwtGuard(name, provider, jwtService));
     * </pre>
     *
     * @param driver  驱动名称（不区分大小写）
     * @param factory 守卫工厂
     */
    public void registerGuardDriver(String driver, GuardFactory factory) {
        driverFactories.put(driver.toLowerCase(), factory);
    }

    /** 获取默认守卫 */
    public AuthGuard guard() {
        return guard(defaultGuard);
    }

    /**
     * 按名称获取守卫（请求级缓存）。
     * <p>
     * 守卫实例缓存在当前线程的 ThreadLocal 中，同一请求内重复调用 {@code guard("api")}
     * 返回同一实例，从而保证 {@link AuthGuard} 内部缓存的用户 / token 在单次请求内一致。
     * 不同请求（即使复用同一线程）因 {@link #clear()} 清理而获得全新实例。
     */
    public AuthGuard guard(String name) {
        Map<String, AuthGuard> map = current.get();
        AuthGuard guard = map.get(name);
        if (guard != null) return guard;
        guard = createGuard(name);
        AuthGuard prev = map.putIfAbsent(name, guard);
        return prev != null ? prev : guard;
    }

    private AuthGuard createGuard(String name) {
        GuardConfig cfg = guards.get(name);
        if (cfg == null) {
            throw new IllegalStateException("未注册的守卫: " + name);
        }
        UserProvider provider = providers.get(cfg.providerName);
        if (provider == null) {
            throw new IllegalStateException("未注册的提供者: " + cfg.providerName);
        }
        // 内置 session 驱动
        if ("session".equalsIgnoreCase(cfg.driver)) {
            return new SessionGuard(name, provider);
        }
        // 插件式驱动（如 jwt）
        GuardFactory factory = driverFactories.get(cfg.driver.toLowerCase());
        if (factory != null) {
            return factory.create(name, provider);
        }
        throw new IllegalStateException(
                "未知 guard driver: " + cfg.driver + "，请引入对应插件（如 jwt 模块）");
    }

    // ---- 便捷方法，作用于默认守卫 ----

    public Authenticatable user() {
        return guard().user();
    }

    public Object id() {
        return guard().id();
    }

    public boolean check() {
        return guard().check();
    }

    public boolean guest() {
        return guard().guest();
    }

    public void login(Authenticatable user) {
        guard().login(user);
    }

    public void login(Authenticatable user, String guardName) {
        guard(guardName).login(user);
    }

    public void logout() {
        guard().logout();
    }

    /** 登出指定守卫 */
    public void logout(String guardName) {
        guard(guardName).logout();
    }

    /**
     * 请求结束时清理 ThreadLocal，防止线程池复用导致的串态。
     * <p>
     * 由 {@link com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter} 在 finally 中调用。
     */
    public void clear() {
        current.remove();
    }

    /** 获取最近一次登录签发的 token（仅对支持 token 的守卫有效，如 JWT 守卫） */
    public String token() {
        return guard().token();
    }

    /** 获取指定守卫最近一次签发的 token */
    public String token(String guardName) {
        return guard(guardName).token();
    }

    private record GuardConfig(String driver, String providerName) {
    }
}
