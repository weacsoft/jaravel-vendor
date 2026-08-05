package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * wechat-sdk 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link WechatAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code WechatAutoConfiguration} 在<b>类级别</b>叠加了两重与发布无关的条件：
 * <ul>
 *   <li>{@code @ConditionalOnClass(okhttp3.OkHttpClient.class)}——绑定在第三方 HTTP 客户端上，
 *       属于<b>运行期</b>依赖；缺少 OkHttp 只影响能否真正调用微信接口，不影响能否生成配置模板。</li>
 *   <li>{@code @ConditionalOnProperty(name = "jaravel.wechat.enabled", havingValue = "true", matchIfMissing = true)}
 *       ——运行期开关，显式关掉后整个自动配置不再加载。</li>
 * </ul>
 * 两者任一不成立，可发布配置声明都会一并消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：开发者需要先拿到
 * {@code WechatConfig.java} 模板，把 appId / secret 等填好，再补依赖、再打开开关。
 * 把模板反向依赖到运行期条件上，顺序就颠倒了。
 * <p>
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>，
 * 不含任何运行时条件，确保任何情况下都能执行 {@code artisan vendor:publish --tag=wechat-sdk}。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class WechatPublishAutoConfiguration {

    /**
     * 声明 wechat-sdk 模块的可发布配置类，供 {@code artisan vendor:publish --tag=wechat-sdk} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(WechatPublishableConfig.class)
    public WechatPublishableConfig wechatPublishableConfig() {
        return new WechatPublishableConfig();
    }
}
