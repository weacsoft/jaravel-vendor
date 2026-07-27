package com.weacsoft.jaravel.vendor.auth.contract;

import java.util.Map;

/**
 * 认证守卫驱动契约，采用工厂模式 + support 方法匹配，对齐 database-all 的多数据库支持。
 * <p>
 * 每个驱动实现自行声明 {@link #support(String)} 方法，当传入的 driver 名称匹配时返回 {@code true}，
 * 由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 在创建守卫时遍历所有已注册的驱动，
 * 找到第一个匹配的驱动并调用 {@link #create} 创建守卫实例。
 *
 * <h3>内置驱动</h3>
 * <ul>
 *   <li>{@code session} — 由 auth 模块的 {@code SessionGuardDriver} 提供，登录态存储于 Session（支持 cookie/redis/file 等存储后端）</li>
 *   <li>{@code jwt} — 由 jwt 模块的 {@code JwtGuardDriver} 提供，无状态 token 认证</li>
 * </ul>
 *
 * <h3>扩展驱动</h3>
 * 第三方模块只需实现本接口并注册为 Spring Bean，{@code AuthAutoConfiguration} 会自动收集所有
 * {@code AuthGuardDriver} Bean 并注册到 {@code AuthManager}，无需手动调用注册方法。
 *
 * <pre>
 * &#64;Component
 * public class MyGuardDriver implements AuthGuardDriver {
 *     &#64;Override
 *     public boolean support(String driver) {
 *         return "my-driver".equalsIgnoreCase(driver);
 *     }
 *
 *     &#64;Override
 *     public AuthGuard create(String name, UserProvider provider, Map&lt;String, Object&gt; config) {
 *         return new MyGuard(name, provider);
 *     }
 * }
 * </pre>
 */
public interface AuthGuardDriver {

    /**
     * 判断本驱动是否支持指定的 driver 名称。
     * <p>
     * 由 AuthManager 在创建守卫时遍历调用，第一个返回 {@code true} 的驱动将被选中。
     *
     * @param driver 驱动名称（如 {@code "session"}、{@code "jwt"}），不区分大小写
     * @return 支持返回 {@code true}，不支持返回 {@code false}
     */
    boolean support(String driver);

    /**
     * 创建守卫实例。
     *
     * @param name     守卫名称（如 web / api / admin）
     * @param provider 用户提供者
     * @param config   额外配置（如 session 存储名称等，由具体驱动解释，可为空 Map）
     * @return 守卫实例
     */
    AuthGuard create(String name, UserProvider provider, Map<String, Object> config);
}
