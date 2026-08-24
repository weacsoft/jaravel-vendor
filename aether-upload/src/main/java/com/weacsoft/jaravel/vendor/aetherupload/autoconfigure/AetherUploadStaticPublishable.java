package com.weacsoft.jaravel.vendor.aetherupload.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * aether-upload 模块的静态前端资源发布声明。
 * <p>
 * 把模块 jar 内的大文件上传前端运行时 {@code aetherupload/aether-upload.js}
 * 发布到业务工程 {@code src/main/resources/static/aether-upload.js}。
 * <p>
 * 由 {@code artisan vendor:publish --tag=aether-upload} 或
 * {@code artisan vendor:publish --tag=resources} 一并发布（与配置类同一条命令）。
 */
public class AetherUploadStaticPublishable implements PublishableStatic {

    /** classpath 资源路径（模块 jar 内，不以 / 开头）。 */
    public static final String JS_RESOURCE = "aetherupload/aether-upload.js";

    @Override
    public String tag() {
        return "aether-upload";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/aether-upload.js");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "大文件分片上传前端运行时 aether-upload.js";
    }
}
