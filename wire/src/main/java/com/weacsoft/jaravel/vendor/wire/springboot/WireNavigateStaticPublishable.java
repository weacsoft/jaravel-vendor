package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * wire 模块的「透明导航」静态前端资源发布声明。
 * <p>
 * 把模块 jar 内的 Wire 透明导航前端运行时 {@code static/wire-navigate.js}
 * 发布到业务工程 {@code src/main/resources/static/wire-navigate.js}。
 * <p>
 * 与 {@link WireStaticPublishable}（wire.js）、{@link WireComponentStaticPublishable}
 * （wire-component.js）同属 wire 模块标签，由 {@code artisan vendor:publish --tag=wire}
 * 或 {@code --tag=resources} 一并发布。
 */
public class WireNavigateStaticPublishable implements PublishableStatic {

    /** classpath 资源路径（模块 jar 内，不以 / 开头）。 */
    public static final String JS_RESOURCE = "static/wire-navigate.js";

    @Override
    public String tag() {
        return "wire";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/wire-navigate.js");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "Wire 透明导航前端运行时 wire-navigate.js";
    }
}
