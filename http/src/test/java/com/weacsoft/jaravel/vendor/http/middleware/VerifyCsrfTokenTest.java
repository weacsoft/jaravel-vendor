package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSRF 令牌来源与比较方式回归测试（审计 M1）。
 * <p>
 * 修复前：令牌可以从 query string 与同名 Cookie 读取（{@code request.get()} 会合并 query；
 * Cookie 兜底让「双提交」退化为「浏览器自动携带即通过」），且比较用 {@code String.equals}（短路）。
 * 修复后：只接受请求头与请求体字段，比较改为常量时间；query 仅在显式开启兼容开关时才接受。
 * <p>
 * 本仓库此前<b>没有任何 CSRF 测试</b>（验收/安全两位专家都点出了这一缺口）。
 */
class VerifyCsrfTokenTest {

    private static final String SESSION_TOKEN = "session-token-123";
    private static final String WRONG_TOKEN = "session-token-124";

    private final VerifyCsrfToken middleware = new VerifyCsrfToken();

    private static Request requestWithSession() {
        Request request = new Request();
        request.addSession(VerifyCsrfToken.CSRF_SESSION_KEY, SESSION_TOKEN);
        return request;
    }

    @Test
    void headerTokenPasses() {
        Request request = requestWithSession();
        request.addHeader(VerifyCsrfToken.CSRF_TOKEN_HEADER_NAME, SESSION_TOKEN);
        assertTrue(middleware.verifyCsrfToken(request), "请求头令牌应通过");
    }

    @Test
    void bodyFieldTokenPasses() {
        Request request = requestWithSession();
        request.addInput(VerifyCsrfToken.CSRF_TOKEN_INPUT_NAME, SESSION_TOKEN);
        assertTrue(middleware.verifyCsrfToken(request), "表单字段（body）令牌应通过");
    }

    @Test
    void queryTokenIsRejectedByDefault() {
        Request request = requestWithSession();
        request.addQuery(VerifyCsrfToken.CSRF_TOKEN_INPUT_NAME, SESSION_TOKEN);

        assertNull(middleware.getRequestToken(request),
                "默认不得从 query string 读取 CSRF 令牌（令牌会泄漏到日志/Referer/历史）");
        assertFalse(middleware.verifyCsrfToken(request), "query 中的令牌必须被拒绝");
    }

    @Test
    void queryTokenAllowedOnlyWhenExplicitlyOptedIn() {
        VerifyCsrfToken lenient = new VerifyCsrfToken() {
            @Override
            protected boolean allowTokenInQuery() {
                return true;
            }
        };
        Request request = requestWithSession();
        request.addQuery(VerifyCsrfToken.CSRF_TOKEN_INPUT_NAME, SESSION_TOKEN);

        assertTrue(lenient.verifyCsrfToken(request),
                "显式开启兼容开关后，query 令牌应放行（迁移期逃生口）");
    }

    @Test
    void cookieOnlyRequestIsRejected() {
        Request request = requestWithSession();
        request.addCookie(VerifyCsrfToken.CSRF_TOKEN_COOKIE_NAME, SESSION_TOKEN);

        assertNull(middleware.getRequestToken(request),
                "不得接受同名 Cookie 作为令牌（否则校验退化为「浏览器自动携带即通过」）");
        assertFalse(middleware.verifyCsrfToken(request), "仅带 Cookie 的请求必须被拒绝");
    }

    @Test
    void wrongTokenIsRejected() {
        Request request = requestWithSession();
        request.addHeader(VerifyCsrfToken.CSRF_TOKEN_HEADER_NAME, WRONG_TOKEN);
        assertFalse(middleware.verifyCsrfToken(request), "错误令牌必须被拒绝");
    }

    @Test
    void missingSessionTokenIsRejected() {
        Request request = new Request();
        request.addHeader(VerifyCsrfToken.CSRF_TOKEN_HEADER_NAME, SESSION_TOKEN);
        assertFalse(middleware.verifyCsrfToken(request), "会话中无令牌时不得通过");
    }

    @Test
    void nullAndEmptyTokensAreRejected() {
        Request request = requestWithSession();
        assertFalse(middleware.verifyCsrfToken(request), "无令牌请求必须被拒绝");
        request.addHeader(VerifyCsrfToken.CSRF_TOKEN_HEADER_NAME, "");
        assertFalse(middleware.verifyCsrfToken(request), "空令牌必须被拒绝");
    }
}