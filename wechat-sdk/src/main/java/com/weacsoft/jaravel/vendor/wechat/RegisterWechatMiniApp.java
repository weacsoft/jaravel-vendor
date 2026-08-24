package com.weacsoft.jaravel.vendor.wechat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个微信<b>小程序</b>命名配置（{@code mini-apps} 段的声明式来源）。
 * <p>
 * 标注在业务工程任意 {@code @Configuration} 类的方法上，方法返回
 * {@link WechatProperties.MiniAppConfig}：
 * <pre>
 * &#64;RegisterWechatMiniApp("default")
 * public WechatProperties.MiniAppConfig defaultMiniApp() {
 *     WechatProperties.MiniAppConfig m = new WechatProperties.MiniAppConfig();
 *     m.setAppId("wx7051c4a2a779d651");
 *     m.setSecret("your-mini-secret");
 *     m.setType(2);   // 2=客服小程序，3=管理端小程序
 *     return m;
 * }
 * </pre>
 *
 * 与 {@link RegisterWechatOfficialAccount} 同层优先级：声明 &gt; yml &gt; 兜底默认。
 *
 * @author weacsoft
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RegisterWechatMiniApp {

    /**
     * 配置名（如 {@code default} / {@code kf_mini}）。
     *
     * @return 配置名，空串表示 default
     */
    String value() default "";

    /**
     * 额外别名（同一份配置在多个名字下可被命中）。
     *
     * @return 别名数组
     */
    String[] alias() default {};
}
