package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.auth.AuthGuard;
import com.weacsoft.jaravel.contract.auth.Authenticatable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth 门面类，提供全局静态方法访问认证系统。
 *
 * <p>管理多个命名 Guard 实例，通过 initialize/destroy 管理请求级状态，
 * 业务方法（user/check/login/logout）无需传递上下文。</p>
 *
 * <h3>使用流程</h3>
 * <ol>
 *   <li>请求开始前调用 {@link #init()} 初始化所有 Guard</li>
 *   <li>请求处理中使用 user/check/login/logout 等方法（无需传 context）</li>
 *   <li>请求结束后调用 {@link #destroy()} 清理所有 Guard</li>
 * </ol>
 */
public class Auth {

    public static String defaultGuard = "web";

    private static final Map<String, AuthGuard<? extends Authenticatable>> guards = new ConcurrentHashMap<>();

    public static <T extends Authenticatable> void addGuard(String name, AuthGuard<T> guard) {
        guards.put(name, guard);
    }

    // ==================== 生命周期方法 ====================

    /**
     * 初始化所有 Guard（请求开始前调用）。
     *
     */
    public static void init() {
        for (AuthGuard<?> guard : guards.values()) {
            guard.init();
        }
    }

    /**
     * 销毁所有 Guard（请求结束后调用）。
     *
     */
    public static void destroy() {
        for (AuthGuard<?> guard : guards.values()) {
            guard.destroy();
        }
    }


    // ==================== 用户查询方法 ====================

    public static <T extends Authenticatable> T user() {
        return user(defaultGuard);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Authenticatable> T user(String guard) {
        return (T) guard(guard).user();
    }

    public static boolean check() {
        return check(defaultGuard);
    }

    public static boolean check(String guard) {
        return guard(guard).check();
    }

    public static boolean guest() {
        return guest(defaultGuard);
    }

    public static boolean guest(String guard) {
        return guard(guard).guest();
    }

    // ==================== 登录/登出方法 ====================

    public static <T extends Authenticatable> boolean login(T user) {
        return login(user, defaultGuard);
    }

    public static <T extends Authenticatable> boolean login(T user, String guard) {
        return guard(guard).login(user);
    }

    public static void logout() {
        logout(defaultGuard);
    }

    public static void logout(String guard) {
        guard(guard).logout();
    }

    // ==================== Guard 获取方法 ====================

    public static <T extends Authenticatable> AuthGuard<T> guard() {
        return guard(defaultGuard);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Authenticatable> AuthGuard<T> guard(String name) {
        return (AuthGuard<T>) guards.get(name);
    }
}
