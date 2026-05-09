package com.weacsoft.jaravel.contract.http;

import java.util.List;
import java.util.Map;

/**
 * HTTP 响应接口，定义框架无关的响应构建契约。
 *
 * <p>本接口从 Servlet API 中解耦，使用 {@link Cookie} 替代
 * {@code jakarta.servlet.http.Cookie}，使 interface 模块零外部依赖。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类应为可变构建器模式（链式调用）</li>
 *   <li>{@link #getContent()} 和 {@link #getBytes()} 互斥：文本响应返回 content，二进制响应返回 bytes</li>
 *   <li>文本响应时 {@link #getBytes()} 应返回 {@code null}；二进制响应时 {@link #getContent()} 应返回 {@code null}</li>
 * </ul>
 *
 * @see Request
 * @see Cookie
 */
public interface Response {

    /**
     * 获取 HTTP 状态码。
     */
    int getStatus();

    /**
     * 获取所有响应头。
     *
     * @return 响应头映射（名称 → 值）
     */
    Map<String, String> getHeaders();

    /**
     * 添加响应头。
     *
     * @param name  头名称
     * @param value 头值
     * @return 当前响应实例（链式调用）
     */
    Response addHeader(String name, String value);

    /**
     * 替换响应头。
     *
     * @param key      头名称
     * @param newValue 新的头值
     * @return 当前响应实例（链式调用）
     */
    Response replaceHeader(String key, String newValue);

    /**
     * 获取所有 Cookie。
     */
    List<Cookie> getCookies();

    /**
     * 添加 Cookie。
     *
     * @param cookie Cookie 实例
     * @return 当前响应实例（链式调用）
     */
    Response addCookie(Cookie cookie);

    /**
     * 替换指定名称的 Cookie 值。
     *
     * @param key      Cookie 名称
     * @param newValue 新的 Cookie 值
     * @return 当前响应实例（链式调用）
     */
    Response replaceCookie(String key, String newValue);

    /**
     * 替换所有 Cookie。
     *
     * @param cookies 新的 Cookie 列表
     * @return 当前响应实例（链式调用）
     */
    Response replaceCookieAll(List<Cookie> cookies);

    /**
     * 获取文本响应内容。
     *
     * @return 文本内容，二进制响应时返回 {@code null}
     */
    String getContent();

    /**
     * 获取二进制响应内容。
     *
     * @return 二进制数据，文本响应时返回 {@code null}
     */
    default byte[] getBytes() {
        return getContent().getBytes();
    }
}
