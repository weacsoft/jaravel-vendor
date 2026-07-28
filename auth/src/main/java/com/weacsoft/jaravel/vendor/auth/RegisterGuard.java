package com.weacsoft.jaravel.vendor.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册认证守卫，替代 {@code @Bean} 方式（避免 bean name 冲突）。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回 {@link com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition}。
 * {@link com.weacsoft.jaravel.vendor.auth.autoconfigure.AuthRegistrar AuthRegistrar}
 * 会在所有 Bean 初始化完成后扫描此注解，调用方法并按 {@link #value()} 指定的名称注册到
 * {@link AuthManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean("admin")} 的 bean name 在整个 Spring 容器内必须唯一。如果另一处也有
 * {@code @Bean("admin")}（返回不同类型），Spring Boot 会抛出
 * {@code BeanDefinitionOverrideException}。使用本注解后，guard 名称与 bean name 解耦，
 * 不会注册为 Spring Bean，因此不会与同名 bean 冲突。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class AuthConfig {
 *
 *     // 默认守卫：标记 defaultGuard = true
 *     &#64;RegisterGuard(value = "web", defaultGuard = true)
 *     public GuardDefinition webGuard() {
 *         return GuardDefinition.of("session", "users");
 *     }
 *
 *     &#64;RegisterGuard("api")
 *     public GuardDefinition apiGuard() {
 *         return GuardDefinition.of("jwt", "users");
 *     }
 *
 *     &#64;RegisterGuard("admin")
 *     public GuardDefinition adminGuard() {
 *         return GuardDefinition.of("jwt", "admins");
 *     }
 * }
 * </pre>
 *
 * <h3>方法参数注入</h3>
 * 方法可声明任意参数，{@code AuthRegistrar} 会从 Spring 容器中按类型自动解析注入，
 * 行为与 {@code @Bean} 方法的参数注入一致。
 *
 * <h3>与配置式的关系</h3>
 * 本注解注册的 guard 优先级与 {@code @Bean} 方式相同：覆盖同名配置式 guard
 * （在配置式注册之后执行）。
 *
 * @see AuthManager#registerGuard(String, String, String, java.util.Map)
 * @see com.weacsoft.jaravel.vendor.auth.contract.GuardDefinition
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterGuard {

    /**
     * 守卫名称，用于 {@code Auth.guard(name)} 或 {@code AuthManager.guard(name)} 解析。
     *
     * @return 守卫名称
     */
    String value();

    /**
     * 是否设为默认守卫。
     * <p>
     * 设为 {@code true} 时，等效于调用 {@link AuthManager#setDefaultGuard(String)}，
     * 会覆盖 {@code jaravel.auth.default-guard} 配置值。
     * <p>
     * 若多个 {@code @RegisterGuard} 同时标记 {@code defaultGuard = true}，
     * 最后注册的生效。
     *
     * @return 是否为默认守卫，默认 {@code false}
     */
    boolean defaultGuard() default false;
}
