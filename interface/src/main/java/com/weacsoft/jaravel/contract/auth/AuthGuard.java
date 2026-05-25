package com.weacsoft.jaravel.contract.auth;

/**
 * 认证守卫接口，定义认证检查契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\Guard}，
 * 本接口定义基本的用户身份检查能力，不包含登录/登出等状态变更操作。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类为单例，通过 {@link #initialize()} / {@link #destroy()} 管理请求级状态</li>
 *   <li>{@link #user()} 在未认证时应返回 {@code null}</li>
 *   <li>{@link #check()} 和 {@link #guest()} 互为反义</li>
 * </ul>
 *
 * @see Authenticatable
 */
public interface AuthGuard<T extends Authenticatable> {

    AuthDriver getDriver();

    AuthProvider<T, ?> getProvider();

    /**
     * 请求开始前初始化守卫状态。
     *
     * <p>将请求上下文存入线程本地变量，后续所有方法调用自动使用该上下文。
     * 同时从驱动中恢复当前请求的认证状态。</p>
     */
    default void init() {
        getDriver().init();
        getProvider().init();
    }

    /**
     * 请求结束后销毁守卫状态。
     *
     * <p>清理线程本地变量中的请求上下文和用户缓存。
     * 注意：此方法不会清除驱动中的持久化状态，仅清理请求级临时数据。</p>
     */
    default void destroy() {
        getDriver().destroy();
        getProvider().destroy();
    }


    /**
     * 通过可认证用户执行登录。
     *
     * <p>将用户ID写入驱动进行持久化，并缓存用户实体到当前请求。</p>
     *
     * @param user 已认证的用户实体
     * @return 登录成功返回 {@code true}
     */
    boolean login(T user);

    /**
     * 登出，清除当前认证状态。
     *
     * <p>从驱动中移除用户ID，并清除当前请求的用户缓存。</p>
     */
    void logout();

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
        return check() ? user().getAuthIdentifier() : null;
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
