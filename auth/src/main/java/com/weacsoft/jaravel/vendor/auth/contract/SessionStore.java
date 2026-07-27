package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * Session 存储契约，抽象登录态的持久化方式。
 * <p>
 * 认证驱动（{@link AuthGuardDriver}）中 {@code session} 驱动使用本接口的实现作为登录态存储后端。
 * <p>
 * <b>Session 存储是全局配置，不与 Guard 绑定</b>。具体使用哪个实现由应用的
 * {@code config/SessionConfig.java} 决定（注册为 Spring Bean）。
 * 如果应用未注册任何 {@code SessionStore} Bean，auth 模块默认提供
 * {@link com.weacsoft.jaravel.vendor.auth.session.CookieSessionStore}（Servlet HttpSession）。
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@code CookieSessionStore}（auth 模块，默认）— 使用 Servlet 容器的 HttpSession</li>
 *   <li>{@code RedisSessionStore}（session-redis 模块）— Session 数据存储于 Redis，支持多机同步</li>
 * </ul>
 *
 * <h3>切换存储</h3>
 * 在应用的 {@code config/SessionConfig.java} 中注册 {@code SessionStore} Bean 即可覆盖默认实现：
 * <pre>
 * &#64;Configuration
 * public class SessionConfig {
 *     &#64;Bean
 *     public SessionStore sessionStore(RedisManager redisManager, SessionRedisProperties props) {
 *         return new RedisSessionStore(redisManager, props.getConnection(), props.getPrefix(),
 *                 props.getLifetime(), props.getCookie());
 *     }
 * }
 * </pre>
 *
 * <h3>设计说明</h3>
 * 本接口通过 {@link com.weacsoft.jaravel.vendor.auth.AuthContext} 获取当前请求上下文，
 * 因此实现类可以是线程安全的单例，无需在方法参数中传递 sessionId 或 Request 对象。
 */
public interface SessionStore {

    /**
     * 从当前 Session 中读取指定 key 的值。
     *
     * @param key 属性名（如 {@code "login_web_id"}）
     * @return 属性值，不存在或无 Session 时返回 {@code null}
     */
    Object get(String key);

    /**
     * 向当前 Session 写入指定 key-value。
     * 如果 Session 尚未启动，实现应自动创建。
     *
     * @param key   属性名
     * @param value 属性值
     */
    void put(String key, Object value);

    /** 从当前 Session 中移除指定 key */
    void remove(String key);

    /** 销毁当前 Session 的所有数据（用于 logout） */
    void destroy();
}
