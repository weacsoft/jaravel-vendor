package com.weacsoft.jaravel.vendor.auth.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.AuthManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * auth 模块「可发布配置」的自动装配。
 * <p>
 * 独立于 {@link AuthAutoConfiguration} 存在，原因是后者带有
 * {@code @ConditionalOnWebApplication(SERVLET)}：在 artisan 命令行模式
 * （{@code WebApplicationType.NONE}）下不会生效，
 * 会导致 {@code vendor:publish} 找不到 auth 的可发布配置。
 * <p>
 * 本类不带 Web 条件，因此命令行模式下同样可用。
 * 它只声明一个纯元数据 Bean，不引入任何运行期开销。
 */
@AutoConfiguration
@ConditionalOnClass(AuthManager.class)
public class AuthPublishAutoConfiguration {

    /**
     * 声明 auth 模块的可发布配置类，供 {@code artisan vendor:publish --tag=auth} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthPublishableConfig authPublishableConfig() {
        return new AuthPublishableConfig();
    }
}
