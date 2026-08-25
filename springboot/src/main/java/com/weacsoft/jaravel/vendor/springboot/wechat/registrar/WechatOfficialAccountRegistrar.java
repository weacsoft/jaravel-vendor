package com.weacsoft.jaravel.vendor.springboot.wechat.registrar;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.core.registrar.RegistrarException;
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatOfficialAccount;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterWechatOfficialAccount} 注解方法，调用并把返回的
 * {@link WechatProperties.OfficialAccountConfig} 回填到共享的 {@link WechatProperties}
 * （声明 &gt; yml &gt; 兜底默认 的最高层）。
 * <p>
 * 产物不注册为 {@code @Bean}，仅回填配置对象，规避 BeanDefinitionOverrideException。
 *
 * @author weacsoft
 */
public class WechatOfficialAccountRegistrar extends AnnotationDrivenRegistrar<RegisterWechatOfficialAccount> {

    private final WechatProperties properties;

    public WechatOfficialAccountRegistrar(ApplicationContext context, WechatProperties properties) {
        super(context, RegisterWechatOfficialAccount.class);
        this.properties = properties;
    }

    @Override
    protected void register(Object result, Method method, RegisterWechatOfficialAccount annotation) {
        WechatProperties.OfficialAccountConfig config =
                requireType(result, WechatProperties.OfficialAccountConfig.class, method);

        String name = annotation.value().isEmpty() ? "default" : annotation.value();
        validateCredentials(config, name, method);

        // 主名字覆盖 yml（声明 > yml）
        properties.getOfficialAccounts().put(name, config);
        // 别名共享同一份配置对象
        for (String alias : annotation.alias()) {
            if (alias != null && !alias.isEmpty() && !alias.equals(name)) {
                properties.getOfficialAccounts().put(alias, config);
            }
        }

        log.info("[wechat] @RegisterWechatOfficialAccount 注册公众号: name={}, appId={}{}",
                name, config.getAppId(),
                annotation.alias().length > 0 ? ", alias=" + String.join(",", annotation.alias()) : "");
    }

    private void validateCredentials(WechatProperties.OfficialAccountConfig config, String name, Method method) {
        if (config.getAppId() == null || config.getAppId().isEmpty()
                || config.getSecret() == null || config.getSecret().isEmpty()) {
            throw new RegistrarException("公众号配置 \"" + name + "\" 缺少 appId 或 secret："
                    + describe(method) + "（声明的命名配置必须同时提供 appId 与 secret）");
        }
    }
}
