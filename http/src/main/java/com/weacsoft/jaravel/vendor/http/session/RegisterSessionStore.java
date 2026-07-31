package com.weacsoft.jaravel.vendor.http.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册 Session 存储，替代 {@code @Bean} 方式。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回 {@link SessionStore}。
 * {@link SessionStoreRegistrar} 会在所有 Bean 初始化完成后扫描此注解，
 * 调用方法并把返回的实例注册为全局 Session 存储。
 *
 * <h3>为什么只允许注册一个？</h3>
 * 与 cache store / storage disk 这类「命名多实例」组件不同，
 * <b>Session 存储是全局唯一的</b>：一个应用只会有一种登录态持久化方式。
 * 因此本注解不带名称参数，且框架强制唯一性——
 * 若扫描到多个 {@code @RegisterSessionStore}，启动时直接报错，
 * 避免出现「到底哪个生效」的隐式歧义。
 *
 * <h3>使用示例</h3>
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
 * <h3>覆盖已有注册</h3>
 * 若某个模块已经注册了 Session 存储，而业务工程想替换它，
 * 可设置 {@link #override()} 为 {@code true}：
 * <pre>
 * &#64;RegisterSessionStore(override = true)
 * public SessionStore mySessionStore() { ... }
 * </pre>
 *
 * <h3>回退默认</h3>
 * 若未注册任何 {@code @RegisterSessionStore}，也没有 {@link SessionStore} Bean，
 * http 模块回退到 {@code CookieSessionStore}（基于 Servlet HttpSession），保证开箱即用。
 *
 * @see SessionStore
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterSessionStore {

    /**
     * 是否覆盖已有的 Session 存储注册。
     * <p>
     * 默认 {@code false}：此时若存在多个注册项，启动报错。
     * 设为 {@code true} 时，本注册项优先生效，用于「框架提供默认、业务工程显式替换」的场景。
     * 但若同时存在多个 {@code override = true} 的注册项，仍会报错。
     *
     * @return 是否覆盖，默认 {@code false}
     */
    boolean override() default false;
}
