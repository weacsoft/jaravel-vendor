package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 模块的静态前端资源发布声明。
 * <p>
 * 通过 {@code artisan vendor:publish:static --tag=wire} 把模块自带的 {@code wire.js}
 * 发布到业务工程 {@code src/main/resources/static/} 下，便于以纯静态方式托管、
 * 加 CDN 或自行做指纹化处理。
 * <p>
 * 本声明<b>不会</b>被 {@code vendor:publish} 触发——那条命令只处理
 * {@code PublishableConfig}（Java 配置类源码）。
 */
public class WireStaticPublishable implements PublishableStatic {

    /** wire.js 在模块 jar 中的 classpath 路径 */
    public static final String JS_RESOURCE = "static/wire.js";

    @Override
    public String tag() {
        return "wire";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/wire.js");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "Wire 前端运行时 wire.js";
    }
}
