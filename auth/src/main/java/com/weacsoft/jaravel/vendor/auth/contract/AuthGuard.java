package com.weacsoft.jaravel.vendor.auth.contract;

/**
 * 认证守卫契约，对齐 Laravel {@code Guard}。
 * <p>
 * {@link #user()} 方法使用泛型方法 + 目标类型推断，使调用方无需强转即可获得具体用户类型：
 * <pre>
 * User user = Auth.guard("web").user();     // 编译器从赋值目标推断 T = User
 * Admin admin = Auth.guard("admin").user(); // 编译器从赋值目标推断 T = Admin
 * </pre>
 * 调用写法完全不变（无需 {@code Auth<T>} 泛型类），类型安全由编译器保证，
 * 运行时转换在守卫内部完成（unchecked but safe，因为 provider 返回的就是正确类型）。
 */
public interface AuthGuard {

    /** 是否已登录 */
    boolean check();

    /** 是否访客 */
    boolean guest();

    /**
     * 当前用户，未登录返回 null。
     * <p>
     * 使用泛型方法实现目标类型推断，调用方可直接赋值给具体用户类型而无需强转：
     * <pre>
     * User user = Auth.guard("web").user();
     * </pre>
     * 类型参数 {@code T} 由赋值上下文推断得出。若赋值目标为 {@code Authenticatable} 或
     * 使用 {@code var}，则 {@code T} 推断为 {@link Authenticatable}（上界）。
     *
     * @param <T> 用户类型，需实现 {@link Authenticatable}
     * @return 当前登录用户，未登录返回 {@code null}
     */
    <T extends Authenticatable> T user();

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
