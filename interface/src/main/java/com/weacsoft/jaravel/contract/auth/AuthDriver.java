package com.weacsoft.jaravel.contract.auth;

import java.util.Map;

/**
 * 认证驱动接口，定义认证标识的持久化契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\StatefulGuard} 中的驱动抽象，
 * 本接口专注于认证标识（如用户ID）的存取与移除，不涉及用户实体的解析逻辑。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类为单例，通过 {@link #init()} / {@link #destroy()}} 管理请求级状态</li>
 *   <li>{@code id} 的存储介质由实现决定（内存、文件、Session、Redis 等）</li>
 *   <li>当未设置 id 时，{@link #getId()} 应返回 {@code null}</li>
 *   <li>调用 setId/getId/removeId 前必须先调用 init</li>
 * </ul>
 *
 * @see Authenticatable
 * @see AuthProvider
 */
public interface AuthDriver {

    /**
     * 请求开始前初始化驱动状态。
     *
     * <p>将请求上下文存入线程本地变量，后续所有方法调用自动使用该上下文。</p>
     */
    default void init() {

    }

    /**
     * 请求结束后销毁驱动状态。
     *
     * <p>清理线程本地变量中的请求上下文。注意：不清理持久化存储中的认证状态。</p>
     */
    default void destroy() {

    }

    /**
     * 设置当前请求的认证标识。
     *
     * @param id 认证标识，通常为用户主键
     */
    void setId(String id);

    /**
     * 获取当前请求的认证标识。
     *
     * @return 当前认证标识，未设置时返回 {@code null}
     */
    String getId();

    /**
     * 移除当前请求的认证标识（登出时调用）。
     */
    void removeId();
}
