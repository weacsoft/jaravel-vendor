package com.weacsoft.jaravel.contract.http;

/**
 * Cookie 默认实现，提供框架无关的 Cookie 值对象。
 *
 * <p>本类作为 {@link Cookie} 接口的参考实现，用于在 contract 模块中
 * 提供开箱即用的 Cookie 构建能力，避免各模块重复实现。</p>
 *
 * <h3>与 Servlet Cookie 的转换</h3>
 * <p>在 Spring Boot 集成层，可通过适配器在 {@code SimpleCookie} 与
 * {@code jakarta.servlet.http.Cookie} 之间转换。</p>
 */
public class SimpleCookie implements Cookie {

    private final String name;
    private String value;
    private String path = "/";
    private String domain;
    private int maxAge = -1;
    private boolean secure;
    private boolean httpOnly;
    private String comment;
    private final java.util.Map<String, String> attributes = new java.util.HashMap<>();

    public SimpleCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setPath(String uri) {
        this.path = uri;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public int getMaxAge() {
        return maxAge;
    }

    @Override
    public void setMaxAge(int expiry) {
        this.maxAge = expiry;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean flag) {
        this.secure = flag;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public void setHttpOnly(boolean flag) {
        this.httpOnly = flag;
    }

    @Override
    public String getAttribute(String name) {
        if ("Comment".equalsIgnoreCase(name)) {
            return comment;
        }
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, String value) {
        if ("Comment".equalsIgnoreCase(name)) {
            this.comment = value;
            return;
        }
        attributes.put(name, value);
    }
}
