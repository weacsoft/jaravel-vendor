package com.weacsoft.jaravel.vendor.http;

/**
 * HTTP 模块配置属性，前缀 {@code jaravel.http}。
 * <pre>
 * jaravel:
 *   http:
 *     url-decode-auto: true       # 是否自动 URL 解码请求参数（默认 true）
 *     base-url: https://example.com  # 应用对外基础地址（默认空）
 * </pre>
 */
public class HttpProperties {

    /** 是否自动 URL 解码请求参数 */
    private boolean urlDecodeAuto = true;

    /**
     * 应用对外基础地址（如 {@code https://example.com}），默认空。
     * <p>
     * 配置后，框架生成的绝对地址（如 OAuth {@code redirect_uri}、授权完成后的回跳地址）
     * 以该地址为准，不再取自请求的 {@code Host} 头。
     * <p>
     * 未配置时框架回退为请求自身的协议与主机，此时绝对地址随请求头变化：
     * 未经受信任代理声明的 {@code Host} 头会被原样采信。
     */
    private String baseUrl = "";

    public boolean isUrlDecodeAuto() { return urlDecodeAuto; }
    public void setUrlDecodeAuto(boolean urlDecodeAuto) { this.urlDecodeAuto = urlDecodeAuto; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
