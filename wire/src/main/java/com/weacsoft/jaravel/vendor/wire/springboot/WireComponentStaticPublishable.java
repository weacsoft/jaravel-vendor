package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 命名组件前端运行时（wire-component.js）的静态资源发布声明。
 * <p>
 * 通过 {@code artisan vendor:publish:static --tag=wire} 把 {@code wire-component.js}
 * 发布到业务工程 {@code src/main/resources/static/} 下，与 {@code wire.js} 共用同一个 tag。
 */
public class WireComponentStaticPublishable implements PublishableStatic {

    /** wire-component.js 在模块 jar 中的 classpath 路径 */
    public static final String JS_RESOURCE = "static/wire-component.js";

    @Override
    public String tag() {
        return "wire";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/wire-component.js");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "Wire 命名组件前端运行时 wire-component.js";
    }
}
