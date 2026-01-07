package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.middleware.Middleware;
import jakarta.servlet.http.Cookie;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class VerifyCsrfToken implements Middleware {

    protected static final String CSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    protected static final String CSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    protected static final String CSRF_TOKEN_INPUT_NAME = "_token";
    protected static final String CSRF_SESSION_KEY = "csrf_token";

    protected static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE");

    protected String[] except = new String[0];

    public VerifyCsrfToken() {
    }

    @Override
    public Response handle(Request request, NextFunction next) {
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

    protected boolean isSafeMethod(String method) {
        return SAFE_METHODS.contains(method);
    }

    protected boolean isExcluded(Request request) {
        String uri = request.getRequest().getRequestURI();
        return Arrays.asList(except).contains(uri);
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

    public void setExcept(String[] except) {
        this.except = except;
    }
}
