package com.weacsoft.jaravel.contract.auth;

import java.io.Serializable;

/**
 * 可认证实体接口，定义可被认证系统识别的用户实体契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\Authenticatable}，
 * 任何需要被认证系统管理的用户模型都应实现此接口。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类应为不可变或线程安全的值对象</li>
 *   <li>{@link #getAuthIdentifier()} 返回的标识在认证上下文中必须唯一</li>
 *   <li>实现类应正确覆写 {@code equals()} 和 {@code hashCode()}</li>
 * </ul>
 *
 * @see AuthenticatableProvider
 * @see Guard
 */
public interface Authenticatable extends Serializable {

    /**
     * 获取认证标识字段名称。
     *
     * @return 标识字段名称，默认为 {@code "id"}
     */
    default String getAuthIdentifierName() {
        return "id";
    }

    /**
     * 获取认证标识值。
     *
     * @return 用户的唯一标识（如主键ID）
     */
    String getAuthIdentifier();
}
