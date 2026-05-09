package com.weacsoft.jaravel.contract.auth;

import java.util.Map;

/**
 * 有状态认证守卫接口，定义包含登录/登出等状态变更的认证契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\StatefulGuard}，
 * 本接口在 {@link Guard} 基础上扩展了状态变更能力，
 * 适用于基于 Session 或 Token 的认证场景。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #login(Authenticatable)} 应同时持久化认证状态</li>
 *   <li>{@link #logout()} 应清除所有认证状态</li>
 * </ul>
 *
 * @see Guard
 * @see Authenticatable
 */
public interface StatefulGuard<T extends Authenticatable> extends Guard<T> {

    /**
     * 通过可认证用户执行登录。
     *
     * @param user 已认证的用户实体
     * @return 登录成功返回 {@code true}
     */
    boolean login(T user);

    /**
     * 登出，清除当前认证状态。
     */
    void logout();
}
