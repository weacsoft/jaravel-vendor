package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验证码模块的静态前端资源发布声明。
 * <p>
 * captcha 模块自带一份<b>完整且零外部依赖</b>的前端资源：
 * {@code jaravel-captcha.js} 内联了全部 CSS（{@code Captcha._injectSharedStyles()}）与加解密实现
 * （基于浏览器原生 Web Crypto API），运行时<b>不依赖 mdui.js、jQuery、wire.js 或任何第三方库</b>。
 * <p>
 * 通过 {@code artisan vendor:publish:static --tag=captcha} 可把这份副本发布到业务工程
 * {@code src/main/resources/static/} 下，之后即可以纯静态方式引用：
 * <pre>
 * &lt;script src="/jaravel-captcha.js"&gt;&lt;/script&gt;
 * </pre>
 * <p>
 * 注意：本声明<b>不会</b>被 {@code vendor:publish} 触发——那条命令只处理
 * {@code PublishableConfig}（Java 配置类源码）。
 */
public class CaptchaStaticPublishable implements PublishableStatic {

    /** 前端库：OOP 验证码组件 + 加解密工具（内联 CSS，无第三方依赖） */
    public static final String JS_RESOURCE = "static/jaravel-captcha.js";

    /** 独立演示页：纯静态，可直接用浏览器打开 */
    public static final String DEMO_RESOURCE = "static/captcha-demo.html";

    @Override
    public String tag() {
        return "captcha";
    }

    @Override
    public Map<String, String> resources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(JS_RESOURCE, "static/jaravel-captcha.js");
        map.put(DEMO_RESOURCE, "static/captcha-demo.html");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String description() {
        return "验证码前端库（自包含，内联 CSS，无 mdui/jQuery 等外部依赖）与独立演示页";
    }
}
