package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PJAX 模块的静态前端资源发布声明。
 * <p>
 * 通过 {@code artisan vendor:publish:static --tag=pjax} 把模块自带的 {@code pjax.js}
 * 发布到业务工程 {@code src/main/resources/static/} 下，便于以纯静态方式托管、
 * 加 CDN 或自行做指纹化处理。
 */
public class PjaxStaticPublishable implements PublishableStatic {

    /** pjax.js 在模块 jar 中的 classpath 路径 */
    public static final String JS_RESOURCE = "static/pjax.js";

    @Override
    public String tag() {
        return "pjax";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/pjax.js");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "PJAX 无感切换前端运行时 pjax.js";
    }
}
