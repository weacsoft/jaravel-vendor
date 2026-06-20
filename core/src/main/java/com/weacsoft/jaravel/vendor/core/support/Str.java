package com.weacsoft.jaravel.vendor.core.support;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 字符串工具，对齐 Laravel {@code Str::} 常用方法。
 */
public final class Str {

    private Str() {
    }

    public static boolean startsWith(String s, String prefix) {
        return s != null && s.startsWith(prefix);
    }

    public static boolean startsWith(String s, String... prefixes) {
        if (s == null) return false;
        for (String p : prefixes) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }

    public static boolean endsWith(String s, String suffix) {
        return s != null && s.endsWith(suffix);
    }

    public static boolean contains(String s, CharSequence needle) {
        return s != null && s.contains(needle);
    }

    public static boolean contains(String s, String... needles) {
        if (s == null) return false;
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }

    /**
     * Laravel {@code Str::is()} 通配符匹配，支持 {@code *}。
     */
    public static boolean is(String pattern, String value) {
        if (pattern == null || value == null) return false;
        if (pattern.equals(value)) return true;
        String regex = "^" + java.util.regex.Pattern.quote(pattern).replace("\\*", "\\E.*\\Q") + "$";
        return value.matches(regex);
    }

    public static String camel(String value) {
        String studly = ucwords(snake(value), "");
        if (studly.isEmpty()) return studly;
        return Character.toLowerCase(studly.charAt(0)) + studly.substring(1);
    }

    public static String studly(String value) {
        return ucwords(snake(value), "");
    }

    public static String snake(String value) {
        return snake(value, "_");
    }

    public static String snake(String value, String delimiter) {
        if (value == null || value.isEmpty()) return value;
        // 处理驼峰 -> 下划线
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append(delimiter);
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll(delimiter + "+", delimiter);
    }

    public static String ucwords(String value, String delimiter) {
        if (value == null || value.isEmpty()) return value;
        String[] parts = value.split(java.util.regex.Pattern.quote(delimiter));
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static String random(int length) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String replaceFirst(String s, String regex, java.util.function.Function<String, String> replacer) {
        if (s == null) return s;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
        if (m.find()) {
            return m.replaceFirst(java.util.regex.Matcher.quoteReplacement(replacer.apply(m.group())));
        }
        return s;
    }
}
