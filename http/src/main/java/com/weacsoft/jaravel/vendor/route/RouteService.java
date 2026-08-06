package com.weacsoft.jaravel.vendor.route;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 路由字符串规范化工具，兼路由结构版本号的持有者。
 *
 * <h3>为什么预编译正则</h3>
 * {@code String.replaceAll} 每次调用都会重新 {@code Pattern.compile}。这三个
 * normalize 方法处在 {@code getFullUri()} / {@code getFullName()} 的递归链上，
 * 一次 {@code route('name')} 反查会沿「路由条数 × 嵌套层数」放大成数千次编译。
 * 改为 {@code static final Pattern} 后编译只发生一次。
 *
 * <h3>路由结构版本号</h3>
 * 路由的完整 URI / 名称 / 中间件链都是「沿父级 Router 递归合并」的纯函数结果，
 * 在注册完成后不再变化，因此适合缓存。但缓存必须能感知注册期的任何改动——包括
 * <b>父级</b> Router 的 prefix / 中间件变更，这对逐对象失效来说很难追踪。
 * 这里改用一个全局单调递增的版本号：任何一处结构性改动都调用
 * {@link #invalidateStructure()}，各缓存只需比对自己记录的版本号即可整体失效。
 * 判断成本是一次 volatile 读，实现简单且不会漏失效。
 */
public class RouteService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");
    private static final Pattern NO_LEADING_SLASH = Pattern.compile("^(?!/)");
    private static final Pattern MULTI_DOT = Pattern.compile("\\.+");
    private static final Pattern NO_LEADING_DOT = Pattern.compile("^(?!\\.)");
    private static final Pattern EDGE_DOT = Pattern.compile("^\\.|\\.$");

    /** 路由结构版本号：任何影响完整 URI / 名称 / 中间件链的改动都会使其自增 */
    private static final AtomicInteger STRUCTURE_VERSION = new AtomicInteger();

    /**
     * 取得当前路由结构版本号。缓存持有者用它判断自身缓存是否仍然有效。
     */
    public static int structureVersion() {
        return STRUCTURE_VERSION.get();
    }

    /**
     * 声明路由结构已发生改动，使所有派生缓存失效。
     * <p>由 {@link Router} 与 {@link RouteDefinition} 的写操作调用。</p>
     */
    public static void invalidateStructure() {
        STRUCTURE_VERSION.incrementAndGet();
    }

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
