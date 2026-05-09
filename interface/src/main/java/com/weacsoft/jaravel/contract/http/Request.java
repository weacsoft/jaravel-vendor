package com.weacsoft.jaravel.contract.http;

import java.util.List;
import java.util.Set;

/**
 * HTTP 请求接口，定义框架无关的请求读取契约。
 *
 * <p>本接口从 Spring/Servlet API 中解耦，仅定义通用的请求参数读取能力。
 * 具体的文件上传、原始 Servlet 请求访问等能力由实现类扩展。</p>
 *
 * <h3>参数查找优先级</h3>
 * <p>{@link #get(String)} 方法按 input → query 的优先级查找参数。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>本接口为只读契约，不定义修改方法</li>
 *   <li>所有方法在键不存在时应返回默认值而非 {@code null}（除非显式声明）</li>
 *   <li>实现类应保证线程安全（请求对象通常为线程局部变量）</li>
 * </ul>
 *
 * @see Response
 * @see Middleware
 */
public interface Request {

    /**
     * 获取参数值（按 input → query 优先级查找）。
     *
     * @param key 参数名
     * @return 参数值，不存在时返回空字符串
     */
    String get(String key);

    /**
     * 获取参数值（按 input → query 优先级查找）。
     *
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 参数值，不存在时返回默认值
     */
    String get(String key, String defaultValue);

    /**
     * 检查参数是否存在（input 或 query）。
     */
    boolean has(String key);

    /**
     * 获取查询参数值。
     *
     * @param key 参数名
     * @return 参数值，不存在时返回空字符串
     */
    String query(String key);

    /**
     * 获取查询参数值。
     *
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 参数值，不存在时返回默认值
     */
    String query(String key, String defaultValue);

    /**
     * 获取所有查询参数名。
     */
    Set<String> queryNames();

    /**
     * 获取请求体参数值。
     *
     * @param key 参数名
     * @return 参数值，不存在时返回空字符串
     */
    String input(String key);

    /**
     * 获取请求体参数值。
     *
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 参数值，不存在时返回默认值
     */
    String input(String key, String defaultValue);

    /**
     * 获取所有请求体参数名。
     */
    Set<String> inputNames();

    /**
     * 获取请求头值。
     *
     * @param key 请求头名称
     * @return 请求头值，不存在时返回空字符串
     */
    String header(String key);

    /**
     * 获取请求头值。
     *
     * @param key          请求头名称
     * @param defaultValue 默认值
     * @return 请求头值，不存在时返回默认值
     */
    String header(String key, String defaultValue);

    /**
     * 获取所有请求头名称。
     */
    Set<String> headerNames();

    /**
     * 获取 Cookie 值。
     *
     * @param key Cookie 名称
     * @return Cookie 值，不存在时返回空字符串
     */
    String cookie(String key);

    /**
     * 获取 Cookie 值。
     *
     * @param key          Cookie 名称
     * @param defaultValue 默认值
     * @return Cookie 值，不存在时返回默认值
     */
    String cookie(String key, String defaultValue);

    /**
     * 获取所有 Cookie 名称。
     */
    Set<String> cookieNames();

    /**
     * 获取 Session 属性值。
     *
     * @param key 属性名
     * @return 属性值，不存在时返回 {@code null}
     */
    Object session(String key);

    /**
     * 获取 Session 属性值。
     *
     * @param key          属性名
     * @param defaultValue 默认值
     * @return 属性值，不存在时返回默认值
     */
    Object session(String key, Object defaultValue);

    /**
     * 获取所有 Session 属性名。
     */
    Set<String> sessionNames();

    /**
     * 检查是否包含文件上传。
     */
    boolean hasFile(String key);

    /**
     * 检查请求头是否存在。
     */
    boolean hasHeader(String key);

    /**
     * 检查 Cookie 是否存在。
     */
    boolean hasCookie(String key);

    /**
     * 检查 Session 属性是否存在。
     */
    boolean hasSession(String key);

    List<String> queries(String queryName);

    List<String> inputs(String inputName);

    Request replaceSession(String key, Object newValue);

    Request removeSession(String sessionKey);
}
