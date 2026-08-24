package com.weacsoft.jaravel.vendor.wechat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个微信<b>公众号</b>命名配置（{@code official-accounts} 段的声明式来源）。
 * <p>
 * 标注在业务工程任意 {@code @Configuration} 类的<b>方法</b>上，方法返回
 * {@link WechatProperties.OfficialAccountConfig}：
 * <pre>
 * &#64;RegisterWechatOfficialAccount(value = "default", alias = {"snsapi_base"})
 * public WechatProperties.OfficialAccountConfig defaultAccount() {
 *     WechatProperties.OfficialAccountConfig c = new WechatProperties.OfficialAccountConfig();
 *     c.setAppId(System.getenv("WECHAT_OA_APPID"));   // 或用 @Value / 常量
 *     c.setSecret(System.getenv("WECHAT_OA_SECRET"));
 *     c.setToken("your-msg-token");
 *     c.setAesKey("your-43-char-aes-key");
 *     c.setMessageMode("safe");
 *     return c;
 * }
 * </pre>
 *
 * <h3>三层优先级（框架约定：声明 &gt; yml &gt; 兜底默认）</h3>
 * <ol>
 *   <li><b>本注解声明</b>（最高）：容器启动扫描后直接覆盖同名配置</li>
 *   <li><b>yml</b>：{@code jaravel.wechat.official-accounts.<name>.*}（经 vendor:publish 发布后编辑）</li>
 *   <li><b>兜底默认</b>：无声明且 yml 未配置时，依赖运行期环境变量/手动 set</li>
 * </ol>
 *
 * 声明<b>不注册为 Spring Bean</b>（仅回填到共享的 {@link WechatProperties}），
 * 天然规避 BeanDefinitionOverrideException。
 *
 * @author weacsoft
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RegisterWechatOfficialAccount {

    /**
     * 配置名（如 {@code default}）。与 yml 中 {@code jaravel.wechat.official-accounts.<name>} 对齐；
     * 重名时声明覆盖 yml。
     *
     * @return 配置名，空串表示 default
     */
    String value() default "";

    /**
     * 额外别名：同一份配置在多个名字下可被 {@code getService(name)} 命中
     * （如 {@code alias = {"snsapi_base"}} 让 {@code getOfficialAccount("snsapi_base")} 也返回它）。
     *
     * @return 别名数组
     */
    String[] alias() default {};
}
