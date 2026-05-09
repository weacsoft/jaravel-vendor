package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.auth.Authenticatable;
import com.weacsoft.jaravel.contract.auth.StatefulGuard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Auth {

    public static String defaultGuard = "web";

    private static Map<String, StatefulGuard<? extends Authenticatable>> guards = new ConcurrentHashMap<>();

    public static <T extends Authenticatable> void addGuard(String name, StatefulGuard<T> guard) {
        guards.put(name, guard);
    }

    public static <T extends Authenticatable> T user() {
        return user(defaultGuard);
    }

    public static <T extends Authenticatable> T user(String guard) {
        return Auth.<T>guard(guard).user();
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

    public static <T extends Authenticatable> StatefulGuard<T> guard() {
        return guard(defaultGuard);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Authenticatable> StatefulGuard<T> guard(String name) {
        return (StatefulGuard<T>) guards.get(name);
    }
}
