package com.weacsoft.jaravel.vendor.core.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 数组/集合工具，对齐 Laravel {@code Arr::} 常用方法。
 */
public final class Arr {

    private Arr() {
    }

    /**
     * 点号取值，如 {@code get(map, "user.profile.name")}。
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Map<String, Object> map, String key, T defaultValue) {
        if (map == null || key == null) return defaultValue;
        if (map.containsKey(key)) return (T) map.get(key);
        if (!key.contains(".")) return defaultValue;
        Object current = map;
        for (String segment : key.split("\\.")) {
            if (current instanceof Map<?, ?> m && m.containsKey(segment)) {
                current = m.get(segment);
            } else {
                return defaultValue;
            }
        }
        return (T) current;
    }

    public static <T> T get(Map<String, Object> map, String key) {
        return get(map, key, null);
    }

    /**
     * 点号设值。
     */
    @SuppressWarnings("unchecked")
    public static void set(Map<String, Object> map, String key, Object value) {
        if (map == null || key == null) return;
        if (!key.contains(".")) {
            map.put(key, value);
            return;
        }
        String[] segments = key.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.get(segments[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(segments[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(segments[segments.length - 1], value);
    }

    /**
     * 点号判断键是否存在，如 {@code has(map, "user.profile.name")}。
     * <p>
     * 注意：不能用 {@code get(map, key, new Object()) != null} 的方式判断，
     * 因为当键不存在时 {@code get} 会返回传入的默认值（非 null），导致永远为 true。
     * 这里显式遍历点号路径，仅当每一级都存在时才返回 true。
     */
    public static boolean has(Map<String, Object> map, String key) {
        if (map == null || key == null) return false;
        if (map.containsKey(key)) return true;
        if (!key.contains(".")) return false;
        Object current = map;
        for (String segment : key.split("\\.")) {
            if (current instanceof Map<?, ?> m && m.containsKey(segment)) {
                current = m.get(segment);
            } else {
                return false;
            }
        }
        return true;
    }

    public static <T> List<T> pluck(Collection<Map<String, Object>> list, String key) {
        List<T> result = new ArrayList<>(list.size());
        for (Map<String, Object> m : list) {
            result.add((T) get(m, key));
        }
        return result;
    }

    public static <T, R> List<R> map(Collection<T> list, Function<T, R> mapper) {
        List<R> result = new ArrayList<>(list.size());
        for (T t : list) result.add(mapper.apply(t));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> only(Map<String, Object> map, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String k : keys) {
            if (map.containsKey(k)) result.put(k, map.get(k));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> except(Map<String, Object> map, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>(map);
        for (String k : keys) result.remove(k);
        return result;
    }
}
