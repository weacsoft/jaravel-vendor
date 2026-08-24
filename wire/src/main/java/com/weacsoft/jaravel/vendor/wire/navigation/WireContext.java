package com.weacsoft.jaravel.vendor.wire.navigation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 导航请求的 ThreadLocal 上下文。
 *
 * <p>WireMiddleware 在请求进入时写入，ResponseBuilder 或 WireRenderer 在处理响应时读取。
 */
public class WireContext {

    private static final ThreadLocal<WireContext> CURRENT = new ThreadLocal<>();

    /** 服务端已知的 section hash（来自请求头 X-Wire-Hashes），key=section名, value=hash */
    private final Map<String, String> incomingHashes;

    /** 当前请求是否由 Wire 导航触发 */
    private final boolean active;

    /** 当前页面的 URL */
    private String url;

    /** HTML title */
    private String title;

    private WireContext(Map<String, String> hashes) {
        this.incomingHashes = hashes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(hashes)) : Collections.emptyMap();
        this.active = !this.incomingHashes.isEmpty();
    }

    /**
     * 启动 Wire 导航上下文。
     *
     * @param hashes 客户端上报的 section hash（section名 → hash值），为空表示非 Wire 导航
     */
    public static void begin(Map<String, String> hashes) {
        WireContext ctx = new WireContext(hashes);
        CURRENT.set(ctx);
    }

    /** 当前请求是否由 Wire 导航触发。 */
    public static boolean isActive() {
        WireContext ctx = CURRENT.get();
        return ctx != null && ctx.active;
    }

    /** 获取客户端上报的 section hash。 */
    public static Map<String, String> getIncomingHashes() {
        WireContext ctx = CURRENT.get();
        return ctx != null ? ctx.incomingHashes : Collections.emptyMap();
    }

    /** 设置当前页面 URL（由 WireRenderer 填充）。 */
    public static void setUrl(String url) {
        WireContext ctx = CURRENT.get();
        if (ctx != null) ctx.url = url;
    }

    public static String getUrl() {
        WireContext ctx = CURRENT.get();
        return ctx != null ? ctx.url : null;
    }

    /** 设置页面标题（由 WireRenderer 填充）。 */
    public static void setTitle(String title) {
        WireContext ctx = CURRENT.get();
        if (ctx != null) ctx.title = title;
    }

    public static String getTitle() {
        WireContext ctx = CURRENT.get();
        return ctx != null ? ctx.title : null;
    }

    /** 清理 ThreadLocal（由中间件在 finally 中调用）。 */
    public static void clear() {
        CURRENT.remove();
    }
}
