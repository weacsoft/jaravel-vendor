package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * 可认证实体契约，对齐 Laravel {@code Illuminate\Contracts\Auth\Authenticatable}。
 * <p>
 * 仅承担「以主键标识用户」的职责，<b>不</b>包含密码相关方法。
 * 认证流程为：应用层通过 query 查出用户 → 在应用代码中校验密码 →
 * {@code Auth.login(user)} 登入 → {@code Auth.check()} 以主键校验登录态。
 * 密码校验是应用层的责任，不应出现在本契约或 UserProvider 中。
 */
public interface Authenticatable {

    /** 主键值，Auth 比对一般只用主键进行比对 */
    Object getAuthIdentifier();

    /** 主键字段名，如 {@code "id"} */
    default String getAuthIdentifierName() {
        return "id";
    }

    /** 记住我令牌字段名 */
    default String getRememberTokenName() {
        return "remember_token";
    }

    /** 记住我令牌，未启用时返回 {@code null} */
    default String getRememberToken() {
        return null;
    }

    /** 设置记住我令牌，未启用时为空实现 */
    default void setRememberToken(String value) {
    }
}
