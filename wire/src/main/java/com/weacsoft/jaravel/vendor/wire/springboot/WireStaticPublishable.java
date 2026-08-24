package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 模块的静态前端资源发布声明。
 * <p>
 * Wire 的全部前端能力（核心部分更新 + 命名组件 + 透明导航）已合并进<b>单一</b>文件
 * {@code wire.js}，业务工程只需引入这一个文件即可，无需再分别引入
 * wire-lib.js / wire-component.js / wire-navigate.js。
 * <p>
 * 通过 {@code artisan vendor:publish --all}（或 {@code --tag=wire} / {@code --tag=resources}）
 * 可把这份副本发布到业务工程 {@code src/main/resources/static/} 下：
 * <pre>
 * &lt;script src="/wire.js"&gt;&lt;/script&gt;
 * </pre>
 * <p>
 * 注意：本声明会被 {@code vendor:publish} 统一扫描，{@code --all} 时与配置类一起发布；
 * 不再需要早期 split 的三个文件各自的发布通道。
 */
public class WireStaticPublishable implements PublishableStatic {

    /** 前端库：Wire 单一运行时（核心 + 命名组件 + 透明导航，已全部内联） */
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
        return "Wire 单一运行时 wire.js（核心部分更新 + 命名组件 + 透明导航，全部内联，零外部依赖）";
    }
}
