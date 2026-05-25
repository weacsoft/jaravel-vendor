package com.weacsoft.jaravel.contract.auth;

/**
 * 可认证实体提供者接口，定义用户实体的检索与验证契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Auth\UserProvider}，
 * 本接口负责从存储介质中检索用户实体并验证凭据，不涉及认证状态的持久化。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>当未找到匹配用户时，所有方法应返回 {@code null} 或 {@code false}</li>
 *   <li>凭据映射的键名应与 {@link Authenticatable} 的字段名对应</li>
 * </ul>
 *
 * @see Authenticatable
 * @see AuthDriver
 */
public interface AuthProvider<T extends Authenticatable, V> {

    /**
     * 请求开始前初始化驱动状态。
     *
     */
    default void init() {

    }

    /**
     * 请求结束后销毁驱动状态。
     *
     */
    default void destroy() {

    }

    /**
     * 通过唯一标识检索用户。
     *
     * @param identifier 用户唯一标识
     * @return 匹配的用户实体，未找到时返回 {@code null}
     */
    T authById(String identifier);

    /**
     * 验证凭据是否与用户匹配。
     *
     * @return 凭据匹配时返回 {@code true}
     */
    default T authByFunction(V data) {
        return getAuthFunction().auth(data);
    }

    AuthFunction<T, V> getAuthFunction();

    @FunctionalInterface
    interface AuthFunction<K extends Authenticatable, V> {
        /**
         * 调用下一个处理器。
         *
         * @return 用户
         */
        K auth(V data);
    }
}
