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
 * <b>继承式配置</b>：通过覆盖 {@link #except()} 方法指定排除 URI，而非通过构造器传参。
 * 预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppVerifyCsrfToken extends VerifyCsrfToken {
 *     @Override
 *     protected String[] except() {
 *         return new String[]{"/api/webhook/*"};
 *     }
 * }
 * }</pre>
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
}
