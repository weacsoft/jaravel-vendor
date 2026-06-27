package com.weacsoft.jaravel.vendor.jblade;

/**
 * Blade 模板静态资源辅助类。
 * <p>
 * 在 Blade 模板中使用 {@code @asset('css/app.css')} 指令时，
 * 实际调用此类的 {@link #url} 方法生成资源 URL。
 * <p>
 * <h3>配置方式</h3>
 * 在应用启动时设置 URL 前缀：
 * <pre>
 * BladeAssetHelper.setUrlPrefix("/static");
 * </pre>
 * <p>
 * <h3>使用方式</h3>
 * 在 Blade 模板中：
 * <pre>
 * &lt;link rel="stylesheet" href="@asset('css/app.css')"&gt;
 * &lt;script src="@asset('js/app.js')"&gt;&lt;/script&gt;
 * &lt;img src="@asset('images/logo.png')"&gt;
 * </pre>
 * 渲染结果：
 * <pre>
 * &lt;link rel="stylesheet" href="/static/css/app.css"&gt;
 * &lt;script src="/static/js/app.js"&gt;&lt;/script&gt;
 * &lt;img src="/static/images/logo.png"&gt;
 * </pre>
 */
public class BladeAssetHelper {

    /** 默认 URL 前缀 */
    private static String urlPrefix = "/static";

    /**
     * 设置静态资源 URL 前缀。
     * <p>
     * 应在应用启动时调用，通常由自动装配设置。
     *
     * @param prefix URL 前缀（如 {@code /static}）
     */
    public static void setUrlPrefix(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            // 确保以 / 开头，不以 / 结尾
            if (!prefix.startsWith("/")) {
                prefix = "/" + prefix;
            }
            while (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            urlPrefix = prefix;
        }
    }

    /**
     * 返回当前 URL 前缀。
     */
    public static String getUrlPrefix() {
        return urlPrefix;
    }

    /**
     * 生成静态资源 URL。
     * <p>
     * 将资源相对路径拼接为完整 URL。
     *
     * @param path 资源相对路径（如 {@code css/app.css}）
     * @return 完整 URL（如 {@code /static/css/app.css}）
     */
    public static String url(String path) {
        if (path == null || path.isEmpty()) {
            return urlPrefix;
        }
        // 去除开头的 /
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return urlPrefix + "/" + path;
    }
}
