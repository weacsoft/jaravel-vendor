package com.weacsoft.jaravel.vendor.auth.contract;

import java.util.Map;

/**
 * 用户提供者契约，对齐 Laravel {@code UserProvider}。
 * <p>
 * 仅负责按标识 / 凭证从存储中<b>取出</b>用户，<b>不</b>负责校验密码。
 * <p>
 * 认证流程为：应用层调用 {@link #retrieveByCredentials} 查出用户 →
 * 在应用代码（Controller / Service）中校验密码 → 调用 {@code Auth.login(user)} 登入。
 * 密码校验是应用层的责任，因此本契约不再包含 {@code validateCredentials} 方法。
 */
public interface UserProvider {

    /**
     * 按主键取出用户，Auth.check() / Auth.user() 通过主键比对登录态时使用。
     *
     * @param identifier 主键值
     * @return 用户实体，未找到返回 {@code null}
     */
    Authenticatable retrieveById(Object identifier);

    /**
     * 按凭证（如 number）取出用户，仅用于查询，不校验密码。
     * <p>
     * 典型用法：{@code provider.retrieveByCredentials(Map.of("number", "1001"))}
     * 查出用户后，由应用层自行比对密码。
     *
     * @param credentials 查询凭证（字段名 -> 值）
     * @return 用户实体，未找到返回 {@code null}
     */
    Authenticatable retrieveByCredentials(Map<String, Object> credentials);
}
