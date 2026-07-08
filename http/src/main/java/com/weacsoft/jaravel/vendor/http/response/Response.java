package com.weacsoft.jaravel.vendor.http.response;

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

    /**
     * 从响应头中提取 Content-Type。
     * 如果未设置，返回默认值 {@code text/plain;charset=utf-8}。
     */
    default String getContentType() {
        List<String> values = getHeaders().get("Content-Type");
        return (values != null && !values.isEmpty()) ? values.get(0) : "text/plain;charset=utf-8";
    }

    /** 响应体，默认返回 getContent() */
    default Object getBody() {
        return getContent();
    }
}