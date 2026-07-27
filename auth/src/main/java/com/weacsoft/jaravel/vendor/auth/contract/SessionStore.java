package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * Session 存储契约，采用工厂模式 + support 方法匹配，对齐 database-all 的多数据库支持。
 * <p>
 * 认证驱动（{@link AuthGuardDriver}）中 {@code session} 驱动使用本接口的实现作为登录态存储后端。
 * 每个存储实现自行声明 {@link #support(String)} 方法，当传入的 store 名称匹配时返回 {@code true}。
 *
 * <h3>内置存储</h3>
 * <ul>
 *   <li>{@code cookie} — 由 auth 模块的 {@code CookieSessionStore} 提供，使用 Servlet 容器的 HttpSession（默认）</li>
 *   <li>{@code redis} — 由 session-redis 模块的 {@code RedisSessionStore} 提供，Session 数据存储于 Redis，支持多机同步</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <p>
 * 本接口通过 {@link com.weacsoft.jaravel.vendor.auth.AuthContext} 获取当前请求上下文，
 * 因此实现类可以是线程安全的单例，无需在方法参数中传递 sessionId 或 Request 对象。
 * <ul>
 *   <li><b>cookie 存储</b>：直接使用 {@code HttpServletRequest.getSession()}，Session ID 由 Servlet 容器通过 Cookie 管理</li>
 *   <li><b>redis 存储</b>：从请求 Cookie 中读取 Session ID，数据存储于 Redis，登录时生成新 Session ID 并写入 Cookie</li>
 * </ul>
 *
 * <h3>扩展存储</h3>
 * 第三方模块只需实现本接口并注册为 Spring Bean，{@code SessionGuardDriver} 会自动收集所有
 * {@code SessionStore} Bean，在创建 SessionGuard 时按配置的 store 名称匹配。
 *
 * <pre>
 * &#64;Component
 * public class FileSessionStore implements SessionStore {
 *     &#64;Override
 *     public boolean support(String store) {
 *         return "file".equalsIgnoreCase(store);
 *     }
 *     // ... 实现 get/put/remove/destroy
 * }
 * </pre>
 */
public interface SessionStore {

    /**
     * 判断本存储是否支持指定的 store 名称。
     *
     * @param store 存储名称（如 {@code "cookie"}、{@code "redis"}），不区分大小写
     * @return 支持返回 {@code true}，不支持返回 {@code false}
     */
    boolean support(String store);

    /**
     * 从当前 Session 中读取指定 key 的值。
     * <p>
     * 通过 {@link com.weacsoft.jaravel.vendor.auth.AuthContext} 获取当前请求上下文，
     * 无需传入 sessionId。
     *
     * @param key 属性名（如 {@code "login_web_id"}）
     * @return 属性值，不存在或无 Session 时返回 {@code null}
     */
    Object get(String key);

    /**
     * 向当前 Session 写入指定 key-value。
     * <p>
     * 如果 Session 尚未启动，实现应自动创建（如生成新 Session ID 并设置 Cookie）。
     *
     * @param key   属性名
     * @param value 属性值
     */
    void put(String key, Object value);

    /**
     * 从当前 Session 中移除指定 key。
     * <p>
     * 如果 Session 不存在，此方法为空操作。
     *
     * @param key 属性名
     */
    void remove(String key);

    /**
     * 销毁当前 Session 的所有数据。
     * <p>
     * 用于 logout 场景，清除整个 Session 而非单个属性。
     */
    void destroy();
}
