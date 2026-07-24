package com.weacsoft.jaravel.vendor.http;

/**
 * HTTP 模块配置属性，前缀 {@code jaravel.http}。
 * <pre>
 * jaravel:
 *   http:
 *     url-decode-auto: true   # 是否自动 URL 解码请求参数（默认 true）
 * </pre>
 */
public class HttpProperties {

    /** 是否自动 URL 解码请求参数 */
    private boolean urlDecodeAuto = true;

    public boolean isUrlDecodeAuto() { return urlDecodeAuto; }
    public void setUrlDecodeAuto(boolean urlDecodeAuto) { this.urlDecodeAuto = urlDecodeAuto; }
}
