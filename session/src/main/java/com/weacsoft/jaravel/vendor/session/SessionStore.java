package com.weacsoft.jaravel.vendor.session;

/**
 * Session 存储契约，抽象登录态的持久化方式。
 * <p>
 * 认证驱动（{@link com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver}）中 {@code session} 驱动
 * 使用本接口的实现作为登录态存储后端。
 * <p>
 * <b>定位</b>：auth 是「认证标准/契约」，本接口是「登录态存储契约」，而
 * {@link CookieSessionStore}、{@code RedisSessionStore} 等只是它的实现之一 ——
 * 换成基于 token 的守卫时无需任何 Session 存储参与。
 * <p>
 * <b>Session 存储是全局配置，不与 Guard 绑定</b>。具体使用哪个实现由应用的
 * {@code config/SessionConfig.java} 决定（通过 {@link RegisterSessionStore} 注册或声明为 Spring Bean）。
 * 如果应用未注册任何 {@code SessionStore} 实现，http 模块默认提供
 * {@link CookieSessionStore}（Servlet HttpSession）。
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@code CookieSessionStore}（http 模块，默认）— 使用 Servlet 容器的 HttpSession</li>
 *   <li>{@code RedisSessionStore}（session-redis 模块）— Session 数据存储于 Redis，支持多机同步</li>
 * </ul>
 *
 * <h3>切换存储</h3>
 * 在应用的 {@code config/SessionConfig.java} 中通过 {@link RegisterSessionStore} 注册即可覆盖默认实现：
 * <pre>
 * &#64;Configuration
 * public class SessionConfig {
 *
 *     &#64;RegisterSessionStore
 *     public SessionStore redisSessionStore(RedisManager redisManager, SessionRedisProperties props) {
 *         return new RedisSessionStore(redisManager, props.getConnection(), props.getPrefix(),
 *                 props.getLifetime(), props.getCookie());
 *     }
 * }
 * </pre>
 *
 * <h3>设计说明</h3>
 * 本接口通过 {@link com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory}
 * 获取当前请求上下文（由 http 的过滤器在请求开始时设置），
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

    /**
     * 轮换 Session ID（登录成功后必须调用），对齐 Laravel 的 {@code migrate(true)}。
     * <p>
     * <b>为什么需要</b>：若登录前后 Session ID 不变，攻击者只要预先让受害者使用一个他知道的
     * Session ID（子域写 Cookie、链接注入等），受害者登录后该 ID 就成为<b>已认证会话</b> ——
     * 即会话固定攻击（CWE-384）。
     * <p>
     * <b>语义</b>：把当前会话数据迁移到新的 Session ID，并让旧 ID 立即失效（旧数据被删除），
     * 同时把新 ID 写入响应 Cookie。首次访问（尚无会话）时等价于新建一个会话。
     * <p>
     * <b>默认实现为空</b>：这是为了不破坏第三方 {@code SessionStore} 实现的源码兼容；
     * 内置实现（{@link CookieSessionStore}、{@code RedisSessionStore}）均已覆盖。
     * {@code SessionGuard} 会在登录时调用本方法，并对「未覆盖该方法的实现」打印一次性告警，
     * 以免会话固定防护被静默跳过。
     */
    default void rotate() {
        // 默认不做任何事：需要会话固定防护的实现请覆盖
    }
}
