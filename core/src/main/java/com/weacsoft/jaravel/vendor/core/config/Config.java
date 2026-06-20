package com.weacsoft.jaravel.vendor.core.config;

import com.weacsoft.jaravel.vendor.core.Facade;

/**
 * Config 门面，对齐 Laravel {@code config('app.name')}。
 * <pre>
 * String name = Config.get("app.name", "Jaravel");
 * int port = Config.getInt("server.port", 8080);
 * Config.set("app.debug", true);
 * </pre>
 */
public final class Config {

    private Config() {
    }

    private static ConfigRepository inst() {
        return Facade.resolve(ConfigRepository.class);
    }

    public static <T> T get(String key, T defaultValue) {
        return inst().get(key, defaultValue);
    }

    public static <T> T get(String key) {
        return inst().get(key);
    }

    public static String string(String key, String defaultValue) {
        return inst().string(key, defaultValue);
    }

    public static String string(String key) {
        return inst().string(key);
    }

    public static int getInt(String key, int defaultValue) {
        return inst().getInt(key, defaultValue);
    }

    public static boolean getBool(String key, boolean defaultValue) {
        return inst().getBool(key, defaultValue);
    }

    public static void set(String key, Object value) {
        inst().set(key, value);
    }

    public static boolean has(String key) {
        return inst().has(key);
    }
}
