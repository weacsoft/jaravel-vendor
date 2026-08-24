package com.weacsoft.jaravel.vendor.auth.contract;

import java.util.Map;

/**
 * 用户提供者驱动契约，采用工厂模式 + support 方法匹配，对齐 {@link AuthGuardDriver} 的设计。
 * <p>
 * 每个驱动实现自行声明 {@link #support(String)} 方法，当传入的 driver 名称匹配时返回 {@code true}，
 * 由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 在创建提供者时遍历所有已注册的驱动，
 * 找到第一个匹配的驱动并调用 {@link #create} 创建提供者实例。
 *
 * <h3>内置驱动</h3>
 * <ul>
 *   <li>{@code eloquent} — 由 database 模块的 {@code EloquentUserProviderDriver} 提供，
 *       基于 gaarason/database-all 的 Eloquent Model 查询用户</li>
 * </ul>
 *
 * <h3>扩展驱动</h3>
 * 第三方模块只需实现本接口并注册为 Spring Bean，{@code AuthAutoConfiguration} 会自动收集所有
 * {@code UserProviderDriver} Bean 并注册到 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}，
 * 无需手动调用注册方法。
 *
 * <pre>
 * &#64;Component
 * public class MyProviderDriver implements UserProviderDriver {
 *     &#64;Override
 *     public boolean support(String driver) {
 *         return "my-driver".equalsIgnoreCase(driver);
 *     }
 *
 *     &#64;Override
 *     public UserProvider create(Map&lt;String, Object&gt; config) {
 *         return new MyUserProvider(config.get("source"));
 *     }
 * }
 * </pre>
 *
 * <h3>配置式注册（对齐 Laravel {@code config/auth.php} 的 providers 数组）</h3>
 * <pre>
 * jaravel:
 *   auth:
 *     providers:
 *       users:
 *         driver: eloquent
 *         model: com.weacsoft.jaravel.app.model.User
 *         credential-field: number
 *       admins:
 *         driver: eloquent
 *         model: com.weacsoft.jaravel.app.model.admin.Admin
 *         credential-field: username
 * </pre>
 *
 * <h3>注解声明式注册（推荐）</h3>
 * 若需要完全控制提供者的创建过程（如自定义构造参数），可直接在 Config 类中用
 * {@link com.weacsoft.jaravel.vendor.auth.RegisterProvider @RegisterProvider} 注解声明：
 * <pre>
 * &#64;RegisterProvider("users")
 * public UserProvider usersProvider(User userModel) {
 *     return new EloquentUserProvider&lt;&gt;(userModel, "number");
 * }
 * </pre>
 * {@code @RegisterProvider("users")} 的 value 即 provider name，{@link com.weacsoft.jaravel.vendor.auth.autoconfigure.AuthRegistrar}
 * 扫描注解方法并注册到 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}。
 * 注解声明优先于配置式注册（同名时覆盖），且不会注册为 Spring Bean，避免 bean name 冲突。
 */
public interface UserProviderDriver {

    /**
     * 判断本驱动是否支持指定的 driver 名称。
     *
     * @param driver 驱动名称（如 {@code "eloquent"}），不区分大小写
     * @return 支持返回 {@code true}，不支持返回 {@code false}
     */
    boolean support(String driver);

    /**
     * 创建用户提供者实例。
     *
     * @param config 配置参数（来自 {@code AuthProperties} 的 providers 配置段），
     *               含 {@code model}（Model 类全名）、{@code credential-field}（凭证字段名）等
     * @return 提供者实例
     */
    UserProvider create(Map<String, Object> config);
}
