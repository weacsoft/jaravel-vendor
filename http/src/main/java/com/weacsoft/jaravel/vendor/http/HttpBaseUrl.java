package com.weacsoft.jaravel.vendor.http;

/**
 * 应用对外基础地址的进程级持有者。
 *
 * <h3>用途</h3>
 * <p>
 * 框架在生成绝对地址（OAuth 的 {@code redirect_uri}、授权完成后的回跳地址）时，
 * 默认取自请求自身的协议与 {@code Host} 头。未经受信任代理声明的 {@code Host} 头
 * 由客户端决定，直接采信会使框架生成的地址随请求头变化。
 * <p>
 * 配置 {@code jaravel.http.base-url} 后，{@link com.weacsoft.jaravel.vendor.http.controller.request.Request#fullUrl()}
 * 以该地址为准，不再取自请求头。
 *
 * <h3>为什么不放进 {@link HttpProperties}</h3>
 * <p>
 * {@code Request} 由 {@code RequestFactory} 在请求线程中创建，并不持有配置 Bean；
 * 而 {@code fullUrl()} 是纯静态职责，不适合为此引入依赖注入。
 * 因此这里采用与 {@link com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory}
 * 中 {@code urlDecodeAuto} 相同的做法：由自动装配写入静态字段。
 *
 * @see HttpProperties
 */
public final class HttpBaseUrl {

    private HttpBaseUrl() {
    }

    /**
     * 应用对外基础地址，空串表示未配置。
     */
    private static volatile String baseUrl = "";

    /**
     * @return 当前基础地址；未配置时返回空串
     */
    public static String current() {
        return baseUrl;
    }

    /**
     * 设置应用对外基础地址（由自动装配调用）。
     * <p>
     * 去除首尾空白；为空时视为未配置，{@code fullUrl()} 回退为按请求自身推算。
     *
     * @param url 基础地址，如 {@code https://example.com}
     */
    public static void set(String url) {
        if (url == null) {
            baseUrl = "";
            return;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        baseUrl = trimmed;
    }

    /**
     * 清除基础地址（测试或重新装配时使用）。
     */
    public static void clear() {
        baseUrl = "";
    }
}
