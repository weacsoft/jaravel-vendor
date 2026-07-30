package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * CSRF 令牌校验中间件，对齐 Laravel 的 {@code VerifyCsrfToken}。
 * <p>
 * 对非安全方法（非 GET/HEAD/OPTIONS/TRACE）的请求校验 CSRF 令牌。
 * <p>
 * <b>开箱即用</b>：框架启动（SpringBoot 自动配置）会自动将其以别名
 * {@code "VerifyCsrfToken"} 注册到中间件别名表，并在模板引擎注册
 * {@code csrf_token()}/{@code csrf_field()} 辅助函数，无需应用层自定义子类或额外注册。
 * 应用只需在路由组中引用该别名即可启用校验（如 {@code Route.group(Map.of(MIDDLEWARE, new
 * String[]{"VerifyCsrfToken"}), ...)}）。
 * <p>
 * <b>扩展（可选）</b>：若需自定义排除 URI，仍可在应用层继承并覆盖 {@link #except()}，
 * 但绝大多数场景直接使用内置别名即可，无需任何额外代码。
 */
public class VerifyCsrfToken implements Middleware {

    protected static final String CSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    protected static final String CSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    protected static final String CSRF_TOKEN_INPUT_NAME = "_token";
    protected static final String CSRF_SESSION_KEY = "csrf_token";

    protected static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        String method = request.getRequest().getMethod();

        if (isSafeMethod(method) || isExcluded(request)) {
            Response response = next.apply(request);
            addCsrfTokenCookie(request, response);
            return response;
        }

        if (!verifyCsrfToken(request)) {
            throw new RuntimeException("CSRF token validation failed");
        }

        Response response = next.apply(request);
        addCsrfTokenCookie(request, response);
        return response;
    }

    /**
     * 不校验 CSRF 的 URI 数组，子类可覆盖以自定义排除列表。
     *
     * @return 排除 URI 数组，默认为空
     */
    protected String[] except() {
        return new String[0];
    }

    protected boolean isSafeMethod(String method) {
        return SAFE_METHODS.contains(method);
    }

    protected boolean isExcluded(Request request) {
        String uri = request.getRequest().getRequestURI();
        return Arrays.asList(except()).contains(uri);
    }

    protected boolean verifyCsrfToken(Request request) {
        String sessionToken = getSessionToken(request);
        if (sessionToken == null) {
            return false;
        }

        String requestToken = getRequestToken(request);
        if (requestToken == null) {
            return false;
        }

        return sessionToken.equals(requestToken);
    }

    protected String getSessionToken(Request request) {
        Object token = request.getRequest().getSession().getAttribute(CSRF_SESSION_KEY);
        if (token == null) {
            token = generateToken();
            request.getRequest().getSession().setAttribute(CSRF_SESSION_KEY, token);
        }
        return (String) token;
    }

    protected String getRequestToken(Request request) {
        String token = request.getRequest().getHeader(CSRF_TOKEN_HEADER_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        token = request.get(CSRF_TOKEN_INPUT_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        jakarta.servlet.http.Cookie[] cookies = request.getCookieObjects();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (CSRF_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    protected void addCsrfTokenCookie(Request request, Response response) {
        String token = getSessionToken(request);

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(CSRF_TOKEN_COOKIE_NAME, token);
        cookie.setHttpOnly(false);
        cookie.setPath("/");
        cookie.setSecure(request.getRequest().isSecure());
        cookie.setMaxAge(7200);

        response.addCookie(cookie);
    }

    protected String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ========== 框架级（开箱即用）便捷方法 ==========
    // 以下方法供 SpringBoot 自动配置在启动时注册别名与模板辅助函数使用，
    // 使应用层无需自定义 VerifyCsrfToken 子类即可使用 CSRF 能力。

    /**
     * 返回 token 在 HttpSession 中存储的 key（{@value #CSRF_SESSION_KEY}）。
     * 模板辅助函数 {@code csrf_token()} 与中间件校验共用同一 key，保证 token 同源。
     */
    public static String csrfSessionKey() {
        return CSRF_SESSION_KEY;
    }

    /**
     * 读取当前请求 session 中的 CSRF token，若不存在则生成并写回（与 {@link #handle} 同源）。
     * 供模板 {@code csrf_token()}/{@code csrf_field()} 渲染隐藏域 value 使用。
     *
     * @param request 当前请求
     * @return 非空 token 字符串
     */
    public static String currentToken(Request request) {
        if (request == null) {
            return "";
        }
        return new VerifyCsrfToken().getSessionToken(request);
    }

    /**
     * 获取一个开箱即用的 {@link VerifyCsrfToken} 实例，用于框架自动注册别名。
     */
    public static VerifyCsrfToken instance() {
        return new VerifyCsrfToken();
    }
}
