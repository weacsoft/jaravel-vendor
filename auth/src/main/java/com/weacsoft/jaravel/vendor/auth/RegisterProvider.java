package com.weacsoft.jaravel.vendor.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册用户提供者，替代 {@code @Bean} 方式（避免 bean name 冲突）。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回
 * {@link com.weacsoft.jaravel.vendor.auth.contract.UserProvider}。
 * {@link com.weacsoft.jaravel.vendor.auth.autoconfigure.AuthRegistrar AuthRegistrar}
 * 会在所有 Bean 初始化完成后扫描此注解，调用方法并按 {@link #value()} 指定的名称注册到
 * {@link AuthManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean("users")} 的 bean name 在整个 Spring 容器内必须唯一。如果另一处也有
 * {@code @Bean("users")}（返回不同类型），Spring Boot 会抛出
 * {@code BeanDefinitionOverrideException}。使用本注解后，provider 名称与 bean name 解耦，
 * 不会注册为 Spring Bean，因此不会与同名 bean 冲突。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class AuthConfig {
 *
 *     &#64;RegisterProvider("users")
 *     public UserProvider usersProvider(User userModel) {
 *         return new EloquentUserProvider&lt;&gt;(userModel, "number");
 *     }
 *
 *     &#64;RegisterProvider("admins")
 *     public UserProvider adminsProvider(Admin adminModel) {
 *         return new EloquentUserProvider&lt;&gt;(adminModel, "username");
 *     }
 * }
 * </pre>
 *
 * <h3>方法参数注入</h3>
 * 方法可声明任意参数，{@code AuthRegistrar} 会从 Spring 容器中按类型自动解析注入，
 * 行为与 {@code @Bean} 方法的参数注入一致。
 *
 * <h3>与配置式的关系</h3>
 * 本注解注册的 provider 优先级与 {@code @Bean} 方式相同：覆盖同名配置式 provider
 * （在配置式注册之后执行）。
 *
 * @see AuthManager#registerProvider(String, com.weacsoft.jaravel.vendor.auth.contract.UserProvider)
 * @see com.weacsoft.jaravel.vendor.auth.contract.UserProvider
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterProvider {

    /**
     * 提供者名称，用于 {@code guard} 定义中引用（如 {@code GuardDefinition.of("jwt", "users")}）。
     *
     * @return 提供者名称
     */
    String value();
}
