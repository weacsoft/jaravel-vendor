package com.weacsoft.jaravel.vendor.auth;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 认证管理器，对齐 Laravel {@code AuthManager}。
 * <p>
 * 维护多个守卫（guard）与用户提供者（provider），按名称解析守卫实例（请求级缓存于 ThreadLocal）。
 * <p>
 * 采用工厂模式 + support 方法匹配（对齐 database-all 多数据库支持）：
 * 守卫驱动（{@link AuthGuardDriver}）自行声明 {@code support(String)} 方法，
 * AuthManager 在创建守卫时遍历所有已注册的驱动，找到第一个匹配的驱动并调用 {@code create}。
 * 第三方模块只需将 {@link AuthGuardDriver} 实现注册为 Spring Bean，
 * {@code AuthAutoConfiguration} 会自动收集并注册到 AuthManager。
 *
 * <h3>认证架构</h3>
 * <ul>
 *   <li><b>认证驱动</b>（数据来源）：{@code session}（登录态存储）| {@code jwt}（无状态 token）</li>
 *   <li><b>Session 存储</b>：全局配置，不与 Guard 绑定。由应用的 {@code config/SessionConfig.java}
 *       决定具体实现（默认 {@code cookie}，即 Servlet HttpSession；可切换为 {@code redis} 等）</li>
 * </ul>
 * session 驱动通过 {@link com.weacsoft.jaravel.vendor.auth.contract.SessionStore} 接口抽象存储后端，
 * 具体使用哪个实现由全局 {@code SessionStore} Bean 决定，而非在注册守卫时指定。
 *
 * <h3>线程安全说明</h3>
 * <ul>
 *   <li><b>注册表（guards / providers / guardDrivers）</b>：使用 {@link ConcurrentHashMap} 和
 *       {@link CopyOnWriteArrayList}，支持并发读写。注册阶段（应用启动时）与运行阶段（请求线程调用
 *       {@code guard(name)}）可安全并发。注册表本身是进程级共享的不可变配置
 *       （启动后不再修改），并发集合保证可见性与原子性。</li>
 *   <li><b>请求级守卫实例（current）</b>：使用 {@link ThreadLocal}，每个请求线程持有独立的
 *       {@code Map<String, AuthGuard>}，因此 {@link AuthGuard} 实例中缓存的 {@code cachedUser}、
 *       {@code resolved}、{@code lastToken} 等可变状态天然按请求隔离，<b>不会</b>跨请求共享。
 *       请求结束时由 {@link com.weacsoft.jaravel.vendor.auth.filter.AuthLifecycleFilter} 调用 {@link #clear()}
 *       清理 ThreadLocal，防止线程池复用导致的串态。</li>
 *   <li><b>defaultGuard</b>：启动阶段设置后不再变更，使用 {@code volatile} 保证可见性。</li>
 * </ul>
 * <p>
 * <b>关键约束</b>：{@link AuthGuard} 实例<b>必须</b>通过 {@link #guard(String)} 获取，
 * 不可跨请求缓存或共享，否则其内部的可变状态会串态。
 */
public class AuthManager {

    /** 守卫配置：name -> {driver, providerName, config}，进程级共享，启动后只读 */
    private final Map<String, GuardConfig> guards = new ConcurrentHashMap<>();
    /** 提供者：name -> UserProvider，进程级共享，启动后只读 */
    private final Map<String, UserProvider> providers = new ConcurrentHashMap<>();
    /** 守卫驱动列表（工厂模式），进程级共享，启动后只读 */
    private final List<AuthGuardDriver> guardDrivers = new CopyOnWriteArrayList<>();
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

    /** 注册守卫（应用启动阶段调用），使用默认配置 */
    public void registerGuard(String name, String driver, String providerName) {
        guards.put(name, new GuardConfig(driver, providerName, Map.of()));
    }

    /**
     * 注册守卫驱动（工厂模式）。
     * <p>
     * 通常由 {@code AuthAutoConfiguration} 在启动时自动收集所有 {@link AuthGuardDriver} Bean 并注册，
     * 业务方无需手动调用。如需编程式注册，也可直接调用此方法。
     *
     * @param driver 守卫驱动实例
     */
    public void registerGuardDriver(AuthGuardDriver driver) {
        guardDrivers.add(driver);
    }

    /**
     * 检查是否注册了指定名称的守卫。
     *
     * @param name 守卫名称
     * @return 已注册返回 true，未注册返回 false
     */
    public boolean hasGuard(String name) {
        return name != null && guards.containsKey(name);
    }

    /**
     * 检查是否注册了任何守卫。
     *
     * @return 已注册至少一个守卫返回 true，否则返回 false
     */
    public boolean hasGuards() {
        return !guards.isEmpty();
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
        // 工厂模式：遍历所有驱动，找到第一个匹配的
        for (AuthGuardDriver driver : guardDrivers) {
            if (driver.support(cfg.driver)) {
                return driver.create(name, provider, cfg.config);
            }
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

    /** 守卫配置 */
    private record GuardConfig(String driver, String providerName, Map<String, Object> config) {
    }
}
