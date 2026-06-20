package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * 守卫工厂契约，用于插件式注册新的 Guard 驱动。
 * <p>
 * 第三方模块（如 jwt 模块）通过实现此接口并向 {@link com.weacsoft.jaravel.vendor.auth.AuthManager}
 * 注册，即可扩展 AuthManager 支持的 guard driver，无需修改 auth 模块本身。
 *
 * <pre>
 * authManager.registerGuardDriver("jwt", (name, provider, config) -> new JwtGuard(name, provider, jwtService));
 * </pre>
 */
@FunctionalInterface
public interface GuardFactory {

    /**
     * 创建守卫实例。
     *
     * @param name     守卫名称
     * @param provider 用户提供者
     * @param config  额外配置（由具体驱动解释，可为空）
     * @return 守卫实例
     */
    AuthGuard create(String name, UserProvider provider, Object... config);
}
