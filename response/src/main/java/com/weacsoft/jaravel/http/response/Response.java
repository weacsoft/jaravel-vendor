package com.weacsoft.jaravel.http.response;

import jakarta.servlet.http.Cookie;

import java.util.List;
import java.util.Map;

public interface Response {
    int getStatus();

    Map<String, String> getHeaders();

    Response replaceHeader(String key, String newValue);

    Response addHeader(String name, String value);

    List<Cookie> getCookies();

    Response addCookie(Cookie cookie);

    default Response addCookie(String name, String value) {
        return addCookie(new Cookie(name, value));
    }

    Response replaceCookie(String key, String newValue);

    String getContent();

    default byte[] getBytes() {
        return null;
    }


    default Response replaceCookie(Cookie cookie) {
        return replaceCookie(cookie.getName(), cookie.getValue());
    }
    Response replaceCookieAll(List<Cookie> cookie);
}
