package com.weacsoft.jaravel.contract.auth;

/**
 * 认证驱动接口，定义认证标识的持久化契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\StatefulGuard} 中的驱动抽象，
 * 本接口专注于认证标识（如用户ID）的存取与移除，不涉及用户实体的解析逻辑。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@code id} 的存储介质由实现决定（Session、Redis、JWT 等）</li>
 *   <li>当未设置 id 时，{@link #getId()} 应返回 {@code null}</li>
 * </ul>
 *
 * @see Authenticatable
 * @see AuthenticatableProvider
 */
public interface AuthDriver {

    /**
     * 设置当前认证标识。
     *
     * @param id 认证标识，通常为用户主键
     */
    void setId(String id);

    /**
     * 获取当前认证标识。
     *
     * @return 当前认证标识，未设置时返回 {@code null}
     */
    String getId();

    /**
     * 移除当前认证标识（登出时调用）。
     */
    void removeId();
}
