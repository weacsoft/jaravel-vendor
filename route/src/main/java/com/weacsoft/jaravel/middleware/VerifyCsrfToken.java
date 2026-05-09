package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.contract.http.Cookie;
import com.weacsoft.jaravel.contract.http.Middleware;
import com.weacsoft.jaravel.contract.http.Request;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.contract.http.SimpleCookie;
import com.weacsoft.jaravel.http.request.HttpRequest;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class VerifyCsrfToken implements Middleware {

    protected static final String CSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    protected static final String CSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    protected static final String CSRF_TOKEN_INPUT_NAME = "_token";
    protected static final String CSRF_SESSION_KEY = "csrf_token";

    public static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE");

    protected String[] except = new String[0];

    public VerifyCsrfToken() {
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        String method = getHttpMethod(request);

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

    protected String getHttpMethod(Request request) {
        if (request instanceof HttpRequest) {
            return ((HttpRequest) request).getRequest().getMethod();
        }
        return "GET";
    }

    protected boolean isSafeMethod(String method) {
        return SAFE_METHODS.contains(method);
    }

    public boolean isExcluded(Request request) {
        if (request instanceof HttpRequest) {
            String uri = ((HttpRequest) request).getRequest().getRequestURI();
            return Arrays.asList(except).contains(uri);
        }
        return false;
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
        String token = (String) request.session(CSRF_SESSION_KEY);
        if (token == null) {
            token = generateToken();
            if (request instanceof HttpRequest) {
                ((HttpRequest) request).replaceSession(CSRF_SESSION_KEY, token);
            }
        }
        return token;
    }

    protected String getRequestToken(Request request) {
        String token = request.header(CSRF_TOKEN_HEADER_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        token = request.get(CSRF_TOKEN_INPUT_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        token = request.cookie(CSRF_TOKEN_COOKIE_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        return null;
    }

    protected void addCsrfTokenCookie(Request request, Response response) {
        String token = getSessionToken(request);

        SimpleCookie cookie = new SimpleCookie(CSRF_TOKEN_COOKIE_NAME, token);
        cookie.setHttpOnly(false);
        cookie.setPath("/");
        cookie.setSecure(isSecureRequest(request));
        cookie.setMaxAge(7200);

        response.addCookie(cookie);
    }

    protected boolean isSecureRequest(Request request) {
        if (request instanceof HttpRequest) {
            return ((HttpRequest) request).getRequest().isSecure();
        }
        return false;
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
