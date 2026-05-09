package com.weacsoft.jaravel.contract.auth;

import java.util.Map;

/**
 * 认证守卫接口，定义无状态认证检查契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\Guard}，
 * 本接口定义基本的用户身份检查能力，不包含登录/登出等状态变更操作。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #user()} 在未认证时应返回 {@code null}</li>
 *   <li>{@link #check()} 和 {@link #guest()} 互为反义</li>
 * </ul>
 *
 * @see StatefulGuard
 * @see Authenticatable
 */
public interface Guard<T extends Authenticatable> {

    /**
     * 获取当前认证用户。
     *
     * @return 当前认证的用户实体，未认证时返回 {@code null}
     */
    T user();

    /**
     * 获取当前认证用户的标识。
     *
     * @return 用户标识，未认证时返回 {@code null}
     */
    default String id() {
        T user = user();
        return user != null ? user.getAuthIdentifier() : null;
    }

    /**
     * 检查是否已认证。
     *
     * @return 已认证返回 {@code true}
     */
    default boolean check() {
        return user() != null;
    }

    /**
     * 检查是否为游客（未认证）。
     *
     * @return 未认证返回 {@code true}
     */
    default boolean guest() {
        return !check();
    }
}
