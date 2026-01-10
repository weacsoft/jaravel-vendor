package com.weacsoft.jaravel.auth;

public class Auth {

    private static AuthManager manager;

    public static void setManager(AuthManager manager) {
        Auth.manager = manager;
    }

    public static AuthManager manager() {
        return manager;
    }

    public static Authenticatable user() {
        return manager().guard().user();
    }

    public static Authenticatable user(String guard) {
        return manager().guard(guard).user();
    }

    public static boolean check() {
        return manager().guard().check();
    }

    public static boolean check(String guard) {
        return manager().guard(guard).check();
    }

    public static boolean guest() {
        return manager().guard().guest();
    }

    public static boolean guest(String guard) {
        return manager().guard(guard).guest();
    }

    public static Authenticatable login(Authenticatable user) {
        return manager().guard().login(user);
    }

    public static Authenticatable login(Authenticatable user, String guard) {
        return manager().guard(guard).login(user);
    }

    public static void logout() {
        manager().guard().logout();
    }

    public static void logout(String guard) {
        manager().guard(guard).logout();
    }

    public static boolean attempt(Object[] credentials) {
        return manager().guard().attempt(credentials);
    }

    public static boolean attempt(Object[] credentials, String guard) {
        return manager().guard(guard).attempt(credentials);
    }

    public static boolean once(Object[] credentials) {
        return manager().guard().once(credentials);
    }

    public static boolean once(Object[] credentials, String guard) {
        return manager().guard(guard).once(credentials);
    }

    public static boolean validate(Authenticatable user) {
        return manager().guard().validate(user);
    }

    public static boolean validate(Authenticatable user, String guard) {
        return manager().guard(guard).validate(user);
    }

    public static Guard guard(String name) {
        return manager().guard(name);
    }

    public static Guard guard() {
        return manager().guard();
    }

    public static void extend(String name, java.util.function.Function<String, Guard> callback) {
        manager().extend(name, callback);
    }

    public static void setUserProvider(UserProvider provider) {
        manager().setUserProvider(provider);
    }

    public static UserProvider getUserProvider() {
        return manager().getUserProvider();
    }

    public static void setDefaultGuard(String guard) {
        manager().setDefaultGuard(guard);
    }

    public static String getDefaultGuard() {
        return manager().getDefaultGuard();
    }
}
