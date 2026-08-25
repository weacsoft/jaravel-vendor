package com.weacsoft.jaravel.vendor.springboot.auth;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.auth.autoconfigure.AuthPublishableConfig;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

/**
 * auth 模块「可发布配置」的自动装配。
 * <p>
 * 独立于 {@link AuthAutoConfiguration} 存在，原因是后者带有
 * {@code @ConditionalOnWebApplication(SERVLET)}：在 artisan 命令行模式
 * （{@code WebApplicationType.NONE}）下不会生效，
 * 会导致 {@code vendor:publish} 找不到 auth 的可发布配置。
 * <p>
 * 本类不带 Web 条件，因此命令行模式下同样可用。
 * 通过 {@link PublishableRegistry} 静态注册（注册的 {@code AuthPublishableConfig} 纯契约载体
 * 位于 auth 模块），不引入任何运行期开销。
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(AuthManager.class)
public class AuthPublishAutoConfiguration {

    static {
        PublishableRegistry.register(new AuthPublishableConfig());
    }
}
