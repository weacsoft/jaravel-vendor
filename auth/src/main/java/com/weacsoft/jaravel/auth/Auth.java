package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.auth.guard.Guard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Auth {

    public static String defaultGuard = "web";

    private static Map<String, Guard> guards = new ConcurrentHashMap<>();

    //添加门面
    public static void addGuard(String name, Guard guard) {
        guards.put(name, guard);
    }

    //获得登录用户
    public static Authenticatable user() {
        return user(defaultGuard);
    }

    public static Authenticatable user(String guard) {
        return guard(guard).user();
    }

    //检查是否登录
    public static boolean check() {
        return check(defaultGuard);
    }

    public static boolean check(String guard) {
        return user(guard) != null;
    }

    //检查是否是游客
    public static boolean guest() {
        return guest(defaultGuard);
    }

    public static boolean guest(String guard) {
        return !check(guard);
    }

    //通过一个可认证的用户进行登录
    public static Authenticatable login(Authenticatable user) {
        return login(user, defaultGuard);
    }

    public static Authenticatable login(Authenticatable user, String guard) {
        return guard(guard).login(user);
    }

    //登出
    public static void logout() {
        logout(defaultGuard);
    }

    public static void logout(String guard) {
        guard(guard).logout();
    }

    //通过认证内容（例如账号密码）进行登录
    public static boolean attempt(Object[] credentials) {
        return attempt(credentials, defaultGuard);
    }

    public static boolean attempt(Object[] credentials, String guard) {
        return guard(guard).attempt(credentials);
    }

    //同attemp，获得一次性的登录信息
    public static boolean once(Object[] credentials) {
        return once(credentials, defaultGuard);
    }

    public static boolean once(Object[] credentials, String guard) {
        return guard(guard).once(credentials);
    }

    public static Guard guard() {
        return guard(defaultGuard);
    }

    public static Guard guard(String name) {
        return guards.get(name);
    }

}
