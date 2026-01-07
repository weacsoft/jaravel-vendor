package com.weacsoft.jaravel.http.response;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;

public interface Response {
    int getStatus();

    Map<String, List<String>> getHeaders();

    void addHeader(String name, String value);

    Cookie[] getCookies();

    void addCookie(Cookie cookie);

    void addCookie(String name, String value);

    String getContent();

    default byte[] getBytes() {
        return null;
    }
}
