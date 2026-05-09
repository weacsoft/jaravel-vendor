package com.weacsoft.jaravel.contract.http;

/**
 * Cookie 契约接口，定义框架无关的 HTTP Cookie 抽象。
 *
 * <p>本接口解耦对 {@code jakarta.servlet.http.Cookie} 的直接依赖，
 * 使 interface 模块不依赖任何 Servlet 规范。
 * 实现类可在此接口与 Servlet Cookie 之间进行适配转换。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类应为可变对象（与 Servlet Cookie 行为一致）</li>
 *   <li>{@link #getName()} 在 Cookie 生命周期内不可变</li>
 *   <li>默认值：path="/", maxAge=-1, secure=false, httpOnly=false</li>
 * </ul>
 */
public interface Cookie {

    /**
     * 获取 Cookie 名称。
     */
    String getName();

    /**
     * 获取 Cookie 值。
     */
    String getValue();

    /**
     * 设置 Cookie 值。
     */
    void setValue(String value);

    /**
     * 获取 Cookie 路径。
     */
    String getPath();

    /**
     * 设置 Cookie 路径。
     */
    void setPath(String uri);

    /**
     * 获取 Cookie 域名。
     */
    String getDomain();

    /**
     * 设置 Cookie 域名。
     */
    void setDomain(String domain);

    /**
     * 获取 Cookie 最大存活时间（秒）。
     *
     * @return 秒数，-1 表示会话 Cookie，0 表示立即删除
     */
    int getMaxAge();

    /**
     * 设置 Cookie 最大存活时间（秒）。
     */
    void setMaxAge(int expiry);

    /**
     * 检查是否仅限 HTTPS 传输。
     */
    boolean isSecure();

    /**
     * 设置是否仅限 HTTPS 传输。
     */
    void setSecure(boolean flag);

    /**
     * 检查是否为 HttpOnly。
     */
    boolean isHttpOnly();

    /**
     * 设置 HttpOnly 标志。
     */
    void setHttpOnly(boolean flag);

    /**
     * 获取 Cookie 属性。
     *
     * @param name 属性名
     * @return 属性值，不存在时返回 {@code null}
     */
    String getAttribute(String name);

    /**
     * 设置 Cookie 属性。
     *
     * @param name  属性名
     * @param value 属性值
     */
    void setAttribute(String name, String value);
}
