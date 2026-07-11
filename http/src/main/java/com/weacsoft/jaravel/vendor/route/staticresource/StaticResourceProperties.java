package com.weacsoft.jaravel.vendor.route.staticresource;

/**
 * 静态资源配置属性，前缀 {@code jaravel.http.static-resource}。
 * <p>
 * 对齐 Laravel 的 {@code public} 目录和 {@code asset()} 辅助函数。
 * <pre>
 * jaravel:
 *   http:
 *     static-resource:
 *       enabled: true                          # 启用静态资源服务
 *       url-prefix: /static                    # URL 前缀（对齐 Laravel mix）
 *       locations:                             # 资源目录列表（classpath 或文件系统）
 *         - classpath:/static/                 # classpath 下的 static 目录
 *         - file:./public/                     # 文件系统的 public 目录
 *       cache-max-age: 3600                    # 缓存时间（秒，默认 1 小时）
 *       directory-listing: false               # 是否允许目录列表（默认 false）
 * </pre>
 * <p>
 * 在 Blade 模板中使用 {@code @asset('css/app.css')} 生成资源 URL，
 * 实际渲染为 {@code /static/css/app.css}。
 */
public class StaticResourceProperties {

    /** 是否启用静态资源服务 */
    private boolean enabled = true;

    /** URL 前缀（如 /static） */
    private String urlPrefix = "/static";

    /** 默认资源目录（classpath） */
    private String defaultLocation = "classpath:/static/";

    /** 缓存时间（秒） */
    private int cacheMaxAge = 3600;

    /** 是否允许目录列表 */
    private boolean directoryListing = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrlPrefix() { return urlPrefix; }
    public void setUrlPrefix(String urlPrefix) { this.urlPrefix = urlPrefix; }
    public String getDefaultLocation() { return defaultLocation; }
    public void setDefaultLocation(String defaultLocation) { this.defaultLocation = defaultLocation; }
    public int getCacheMaxAge() { return cacheMaxAge; }
    public void setCacheMaxAge(int cacheMaxAge) { this.cacheMaxAge = cacheMaxAge; }
    public boolean isDirectoryListing() { return directoryListing; }
    public void setDirectoryListing(boolean directoryListing) { this.directoryListing = directoryListing; }
}
