package com.weacsoft.jaravel.vendor.http.controller.response;

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
     * <p>
     * HTTP 头名不区分大小写，故此处按忽略大小写匹配 {@code Content-Type}。
     */
    default String getContentType() {
        Map<String, List<String>> headers = getHeaders();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Content-Type")) {
                    List<String> values = entry.getValue();
                    if (values != null && !values.isEmpty()) {
                        return values.get(0);
                    }
                }
            }
        }
        return "text/plain;charset=utf-8";
    }

    /** 响应体，默认返回 getContent() */
    default Object getBody() {
        return getContent();
    }
}