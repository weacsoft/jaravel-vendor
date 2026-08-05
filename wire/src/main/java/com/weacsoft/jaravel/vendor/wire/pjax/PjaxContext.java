package com.weacsoft.jaravel.vendor.wire.pjax;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PJAX 请求上下文（线程绑定）。
 *
 * <p>由 {@link PjaxMiddleware} 在请求进入时写入、请求结束时清理。
 * {@code ResponseBuilder.view()} 通过 {@link PjaxViewRenderer} 读取本上下文，
 * 从而在<b>控制器完全不感知</b>的情况下把整页渲染切换为局部渲染。</p>
 *
 * <h3>两种状态</h3>
 * <ul>
 *   <li>{@code partial = false}：首次直接访问页面。输出完整 HTML，
 *       内含区域注释锚点与 pjax.js，为后续切换建立基础。</li>
 *   <li>{@code partial = true}：由已加载页面发起的切换（带 {@code X-Pjax: true}）。
 *       只输出发生变化的区域。</li>
 * </ul>
 *
 * <h3>为什么用 ThreadLocal 而非方法参数</h3>
 * <p>{@code ResponseBuilder.view(template, data)} 是框架既有的静态 API，
 * 现有控制器全部按此签名书写。要做到「开发者无需修改任何现有 get/post 写法」，
 * 唯一可行的路径就是把请求级信息通过线程上下文旁路传递。</p>
 */
public final class PjaxContext {

    /** PJAX 局部请求标记头：值为 true 时只返回变化区域 */
    public static final String HEADER_PJAX = "X-Pjax";

    /** 客户端当前页面所用布局模板名 */
    public static final String HEADER_LAYOUT = "X-Pjax-Layout";

    /** 客户端当前各区域的内容指纹，格式：{@code name:hash,name:hash} */
    public static final String HEADER_REGIONS = "X-Pjax-Regions";

    private static final ThreadLocal<State> HOLDER = new ThreadLocal<>();

    private PjaxContext() {
    }

    /**
     * 线程内保存的 PJAX 请求状态。
     */
    public static final class State {
        /** 请求 URL（路径 + 查询串），用于回填浏览器地址栏 */
        public final String url;
        /** 是否为局部切换请求；false 表示首次直接访问，需输出完整页面 */
        public final boolean partial;
        /** 客户端当前布局模板名，可能为 null（首屏或客户端未上报） */
        public final String layout;
        /** 客户端已持有的区域指纹：区域名 → hash */
        public final Map<String, String> regionHashes;

        State(String url, boolean partial, String layout, Map<String, String> regionHashes) {
            this.url = url;
            this.partial = partial;
            this.layout = layout;
            this.regionHashes = regionHashes == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(regionHashes);
        }
    }

    /**
     * 标记当前线程进入 PJAX 管辖范围。
     *
     * @param url           请求 URL（路径 + 查询串）
     * @param partial       是否为局部切换请求
     * @param layout        客户端上报的布局模板名
     * @param regionsHeader 客户端上报的区域指纹原始头值
     */
    public static void begin(String url, boolean partial, String layout, String regionsHeader) {
        HOLDER.set(new State(url, partial, normalize(layout), parseRegions(regionsHeader)));
    }

    /**
     * 清理当前线程的 PJAX 状态。必须在 finally 中调用，避免线程池串味。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前请求是否在 PJAX 管辖范围内（首屏或切换均为 true）。
     */
    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    /**
     * 当前请求是否为局部切换请求。
     */
    public static boolean isPartial() {
        State state = HOLDER.get();
        return state != null && state.partial;
    }

    /**
     * 取得当前线程的 PJAX 状态。
     *
     * @return 状态；不在管辖范围时返回 {@code null}
     */
    public static State current() {
        return HOLDER.get();
    }

    /**
     * 解析区域指纹头：{@code content:a1b2,scripts:c3d4} → Map。
     * <p>容错：忽略空片段与缺失冒号的片段，绝不抛异常。</p>
     */
    static Map<String, String> parseRegions(String header) {
        Map<String, String> map = new LinkedHashMap<>();
        if (header == null || header.isEmpty()) {
            return map;
        }
        for (String part : header.split(",")) {
            String seg = part.trim();
            if (seg.isEmpty()) {
                continue;
            }
            int idx = seg.lastIndexOf(':');
            if (idx <= 0 || idx == seg.length() - 1) {
                continue;
            }
            map.put(seg.substring(0, idx).trim(), seg.substring(idx + 1).trim());
        }
        return map;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
