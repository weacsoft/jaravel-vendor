package com.weacsoft.jaravel.vendor.route;

import java.util.regex.Pattern;

/**
 * 路由字符串规范化工具。
 *
 * <h3>为什么预编译正则</h3>
 * {@code String.replaceAll} 每次调用都会重新 {@code Pattern.compile}。这三个
 * normalize 方法处在 {@code getFullUri()} / {@code getFullName()} 的递归链上，
 * 一次 {@code route('name')} 反查会沿「路由条数 × 嵌套层数」放大成数千次编译。
 * 改为 {@code static final Pattern} 后编译只发生一次。
 *
 * <h3>派生结果的缓存在哪里</h3>
 * 完整 URI / 名称 / 中间件链等递归合并结果统一由 {@link RouteCache} 这一份内存缓存持有，
 * 结构性写操作调用 {@link RouteCache#clear()} 整体失效。本类只负责字符串规范化，不持有状态。
 */
public class RouteService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");
    private static final Pattern NO_LEADING_SLASH = Pattern.compile("^(?!/)");
    private static final Pattern MULTI_DOT = Pattern.compile("\\.+");
    private static final Pattern NO_LEADING_DOT = Pattern.compile("^(?!\\.)");
    private static final Pattern EDGE_DOT = Pattern.compile("^\\.|\\.$");

    public static String normalizeUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "/";
        }
        uri = NO_LEADING_SLASH.matcher(
                MULTI_SLASH.matcher(
                        WHITESPACE.matcher(uri).replaceAll("")
                ).replaceAll("/")
        ).replaceAll("/");
        // 仅当 URI 长度 > 1 时才去除尾部斜杠，保留根路径 "/"
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    public static String normalizeNamesapce(String namespace) {
        if (namespace == null || namespace.trim().isEmpty()) {
            return "";
        }
        namespace = NO_LEADING_DOT.matcher(
                MULTI_DOT.matcher(
                        WHITESPACE.matcher(namespace).replaceAll("")
                ).replaceAll(".")
        ).replaceAll(".");
        return EDGE_DOT.matcher(namespace.trim()).replaceAll("");
    }

    public static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        return NO_LEADING_DOT.matcher(
                MULTI_DOT.matcher(
                        WHITESPACE.matcher(name).replaceAll("")
                ).replaceAll(".")
        ).replaceAll(".");
    }
}
