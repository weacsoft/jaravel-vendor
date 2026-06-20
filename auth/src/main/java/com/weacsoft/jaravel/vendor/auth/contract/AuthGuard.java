package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * 认证守卫契约，对齐 Laravel {@code Guard}。
 */
public interface AuthGuard {

    /** 是否已登录 */
    boolean check();

    /** 是否访客 */
    boolean guest();

    /** 当前用户，未登录返回 null */
    Authenticatable user();

    /** 当前用户 id，未登录返回 null */
    default Object id() {
        Authenticatable u = user();
        return u == null ? null : u.getAuthIdentifier();
    }

    /** 登录指定用户 */
    void login(Authenticatable user);

    /** 登出 */
    void logout();

    /**
     * 登录后获取签发的 token（仅对支持 token 的守卫有效，如 JWT 守卫）。
     * 默认返回 {@code null}，由具体守卫按需覆盖。
     *
     * @return token 字符串，或不支持时返回 {@code null}
     */
    default String token() {
        return null;
    }
}
