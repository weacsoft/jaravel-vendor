package com.weacsoft.jaravel.contract.auth;

import java.util.Map;

/**
 * 令牌驱动接口，定义令牌的生成、验证与管理契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\StatefulGuard} 中的令牌抽象，
 * 本接口专注于无状态令牌（如 JWT、OAuth2 Token）的生命周期管理，
 * 与 {@link AuthDriver} 的有状态会话模型形成互补。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>令牌格式与存储介质由实现决定（JWT、OAuth2、自定义令牌等）</li>
 *   <li>{@link #validate(String)} 在令牌无效或过期时应返回 {@code false}，不抛异常</li>
 *   <li>{@link #invalidate(String)} 应使令牌立即失效</li>
 * </ul>
 *
 * <h3>TTL 约定</h3>
 * <ul>
 *   <li>TTL 单位为毫秒</li>
 *   <li>TTL &lt;= 0 表示使用实现默认的过期时间</li>
 * </ul>
 *
 * @see AuthDriver
 * @see AuthGuard
 */
public interface TokenDriver {

    /**
     * 为指定主体生成令牌。
     *
     * @param subject 主体标识（如用户ID）
     * @return 生成的令牌字符串
     */
    String generate(String subject);

    /**
     * 为指定主体生成令牌并指定 TTL。
     *
     * @param subject 主体标识
     * @param ttl     过期时间（毫秒），&lt;= 0 表示使用默认过期时间
     * @return 生成的令牌字符串
     */
    String generate(String subject, long ttl);

    /**
     * 为指定主体生成令牌，附带自定义声明。
     *
     * @param subject 主体标识
     * @param claims  自定义声明映射
     * @param ttl     过期时间（毫秒）
     * @return 生成的令牌字符串
     */
    String generate(String subject, Map<String, Object> claims, long ttl);

    /**
     * 验证令牌是否有效。
     *
     * @param token 待验证的令牌
     * @return 令牌有效返回 {@code true}，无效或过期返回 {@code false}
     */
    boolean validate(String token);

    /**
     * 从令牌中提取主体标识。
     *
     * @param token 令牌字符串
     * @return 主体标识，令牌无效时返回 {@code null}
     */
    String getSubject(String token);

    /**
     * 从令牌中提取自定义声明。
     *
     * @param token 令牌字符串
     * @return 声明映射，令牌无效时返回空 Map
     */
    Map<String, Object> getClaims(String token);

    /**
     * 检查令牌是否已过期。
     *
     * @param token 令牌字符串
     * @return 已过期返回 {@code true}
     */
    boolean isExpired(String token);

    /**
     * 使令牌失效（加入黑名单或删除）。
     *
     * @param token 待失效的令牌
     */
    void invalidate(String token);

    /**
     * 使用刷新令牌获取新的访问令牌。
     *
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌
     */
    String refresh(String refreshToken);
}
