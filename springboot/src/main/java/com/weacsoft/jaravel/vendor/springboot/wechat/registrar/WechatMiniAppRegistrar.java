package com.weacsoft.jaravel.vendor.springboot.wechat.registrar;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.core.registrar.RegistrarException;
import com.weacsoft.jaravel.vendor.wechat.RegisterWechatMiniApp;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterWechatMiniApp} 注解方法，调用并把返回的
 * {@link WechatProperties.MiniAppConfig} 回填到共享的 {@link WechatProperties}
 * （声明 &gt; yml &gt; 兜底默认 的最高层）。
 * <p>
 * 产物不注册为 {@code @Bean}，仅回填配置对象。
 *
 * @author weacsoft
 */
public class WechatMiniAppRegistrar extends AnnotationDrivenRegistrar<RegisterWechatMiniApp> {

    private final WechatProperties properties;

    public WechatMiniAppRegistrar(WechatProperties properties) {
        super(RegisterWechatMiniApp.class);
        this.properties = properties;
    }

    @Override
    protected void register(Object result, Method method, RegisterWechatMiniApp annotation) {
        WechatProperties.MiniAppConfig config =
                requireType(result, WechatProperties.MiniAppConfig.class, method);

        String name = annotation.value().isEmpty() ? "default" : annotation.value();
        validateCredentials(config, name, method);

        properties.getMiniApps().put(name, config);
        for (String alias : annotation.alias()) {
            if (alias != null && !alias.isEmpty() && !alias.equals(name)) {
                properties.getMiniApps().put(alias, config);
            }
        }

        log.info("[wechat] @RegisterWechatMiniApp 注册小程序: name={}, appId={}, type={}{}",
                name, config.getAppId(), config.getType(),
                annotation.alias().length > 0 ? ", alias=" + String.join(",", annotation.alias()) : "");
    }

    private void validateCredentials(WechatProperties.MiniAppConfig config, String name, Method method) {
        if (config.getAppId() == null || config.getAppId().isEmpty()
                || config.getSecret() == null || config.getSecret().isEmpty()) {
            throw new RegistrarException("小程序配置 \"" + name + "\" 缺少 appId 或 secret："
                    + describe(method) + "（声明的命名配置必须同时提供 appId 与 secret）");
        }
    }
}
