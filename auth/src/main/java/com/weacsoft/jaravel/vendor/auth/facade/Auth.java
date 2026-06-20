package com.weacsoft.jaravel.vendor.auth.facade;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.core.Facade;

/**
 * Auth 门面，对齐 Laravel {@code Auth::}。
 * <pre>
 * if (Auth::check()) { ... }
 * Authenticatable user = Auth::user();
 * Auth::login(user);
 * Auth::guard("admin").user();
 * String token = Auth::token();   // JWT 登录后获取 token
 *
 * // 多 guard 用法
 * Auth::guard("api").login(user);   // 通过 api guard（JWT）登录
 * Auth::guard("web").login(user);   // 通过 web guard（Session）登录
 * Auth::guard("api").check();       // 检查 api guard 登录态
 * Auth::guard("web").check();       // 检查 web guard 登录态
 * Auth::logout("api");              // 登出指定 guard
 * </pre>
 */
public final class Auth {

    private Auth() {
    }

    private static AuthManager inst() {
        return Facade.resolve(AuthManager.class);
    }

    public static boolean check() {
        return inst().check();
    }

    public static boolean guest() {
        return inst().guest();
    }

    public static Authenticatable user() {
        return inst().user();
    }

    public static Object id() {
        return inst().id();
    }

    public static AuthGuard guard() {
        return inst().guard();
    }

    public static AuthGuard guard(String name) {
        return inst().guard(name);
    }

    public static void login(Authenticatable user) {
        inst().login(user);
    }

    public static void login(Authenticatable user, String guardName) {
        inst().login(user, guardName);
    }

    public static void logout() {
        inst().logout();
    }

    /** 登出指定守卫 */
    public static void logout(String guardName) {
        inst().logout(guardName);
    }

    /** JWT 登录后获取签发的 token（默认守卫） */
    public static String token() {
        return inst().token();
    }

    /** 获取指定守卫最近一次签发的 token */
    public static String token(String guardName) {
        return inst().token(guardName);
    }
}
