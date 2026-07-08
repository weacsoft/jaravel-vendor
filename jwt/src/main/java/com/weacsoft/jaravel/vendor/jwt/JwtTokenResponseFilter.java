package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT token 响应过滤器。
 * <p>
 * 在请求处理完成后，检查当前 JWT 守卫是否签发了新 token（自动续期或宽限期续期）。
 * 如果有，将新 token 写入响应 header（默认 {@code X-New-Token}），客户端可在响应头中获取新 token。
 * <p>
 * 典型场景：
 * <ul>
 *   <li><b>自动续期</b>：token 已过半 TTL，请求正常处理，响应头携带新 token；</li>
 *   <li><b>宽限期续期</b>：token 已过期但在宽限期内，请求正常处理，响应头携带新 token，旧 token 被黑名单。</li>
 * </ul>
 */
public class JwtTokenResponseFilter extends OncePerRequestFilter {

    private final JwtConfig jwtConfig;
    private final AuthManager authManager;

    public JwtTokenResponseFilter(JwtConfig jwtConfig, AuthManager authManager) {
        this.jwtConfig = jwtConfig;
        this.authManager = authManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        // 请求处理完成后，检查是否有新 token 需要返回
        try {
            // 使用默认守卫检查是否有新签发的 token（自动续期 / 宽限期续期）
            AuthGuard guard = authManager.guard();
            if (guard instanceof JwtGuard jwtGuard) {
                String newToken = jwtGuard.token();
                if (newToken != null && !newToken.isEmpty()) {
                    response.setHeader(jwtConfig.getGraceHeader(), newToken);
                }
            }
        } catch (Exception e) {
            // 忽略异常，不影响响应
            logger.debug("Failed to set new token in response header", e);
        }
    }
}
