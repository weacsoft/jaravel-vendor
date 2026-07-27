package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import java.util.Map;

/**
 * JWT 守卫驱动，支持 {@code jwt} 认证方式。
 * <p>
 * 采用工厂模式：通过 {@link #support(String)} 声明支持的驱动名称，
 * 通过 {@link #create} 创建 {@link JwtGuard} 实例。
 * <p>
 * 本驱动由 jwt 模块的 {@link JwtAutoConfiguration} 注册为 Bean，
 * 再由 auth 模块的 {@code AuthAutoConfiguration} 自动收集并注册到 {@link AuthManager}。
 * 引入 jwt 模块即自动启用 JWT 认证能力。
 *
 * <pre>
 * jaravel:
 *   auth:
 *     guards:
 *       api:
 *         driver: jwt
 *         provider: users
 * </pre>
 */
public class JwtGuardDriver implements AuthGuardDriver {

    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    public JwtGuardDriver(JwtService jwtService, JwtConfig jwtConfig) {
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
    }

    @Override
    public boolean support(String driver) {
        return "jwt".equalsIgnoreCase(driver);
    }

    @Override
    public AuthGuard create(String name, UserProvider provider, Map<String, Object> config) {
        boolean refreshEnabled = jwtConfig.isRefreshEnabled();
        return new JwtGuard(name, provider, jwtService, refreshEnabled, jwtConfig);
    }
}
