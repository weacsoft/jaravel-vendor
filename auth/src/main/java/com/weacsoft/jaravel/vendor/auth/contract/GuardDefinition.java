package com.weacsoft.jaravel.vendor.auth.contract;

import java.util.Map;

/**
 * 守卫定义，用于通过 {@code @Bean} 声明式注册守卫。
 * <p>
 * 对齐 Laravel {@code config/auth.php} 的 guards 数组，但以 Spring {@code @Bean} 方式声明。
 * bean name 即守卫名称，{@code AuthAutoConfiguration} 通过 {@code Map<String, GuardDefinition>}
 * 自动收集并注册到 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class AuthConfig {
 *
 *     &#64;Bean("web")
 *     public GuardDefinition webGuard() {
 *         return GuardDefinition.of("session", "users");
 *     }
 *
 *     &#64;Bean("api")
 *     public GuardDefinition apiGuard() {
 *         return GuardDefinition.of("jwt", "users");
 *     }
 *
 *     &#64;Bean("admin")
 *     public GuardDefinition adminGuard() {
 *         return GuardDefinition.of("jwt", "admins");
 *     }
 * }
 * </pre>
 *
 * <p>
 * 也可通过配置文件注册守卫（{@code jaravel.auth.guards}），两种方式可共存，
 * 编程式注册（{@code @Bean}）优先于配置式注册（同名时覆盖）。
 *
 * @param driver       驱动名称（如 {@code "session"}、{@code "jwt"}）
 * @param provider     提供者名称（如 {@code "users"}、{@code "admins"}）
 * @param config       额外配置（由具体驱动解释，可为空 Map）
 */
public record GuardDefinition(String driver, String provider, Map<String, Object> config) {

    /**
     * 创建守卫定义（无额外配置）。
     *
     * @param driver   驱动名称
     * @param provider 提供者名称
     * @return 守卫定义
     */
    public static GuardDefinition of(String driver, String provider) {
        return new GuardDefinition(driver, provider, Map.of());
    }

    /**
     * 创建守卫定义（带额外配置）。
     *
     * @param driver   驱动名称
     * @param provider 提供者名称
     * @param config   额外配置
     * @return 守卫定义
     */
    public static GuardDefinition of(String driver, String provider, Map<String, Object> config) {
        return new GuardDefinition(driver, provider, config);
    }
}
