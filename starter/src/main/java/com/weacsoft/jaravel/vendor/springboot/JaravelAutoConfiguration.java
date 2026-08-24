package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.config.ConfigDefinitionRegistrar;
import com.weacsoft.jaravel.vendor.core.config.ConfigRepository;
import com.weacsoft.jaravel.vendor.core.provider.ProviderRegistry;
import com.weacsoft.jaravel.vendor.core.SpringContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Jaravel 核心自动装配：注册配置仓库与上下文持有器，聚合各模块的自动装配。
 * <p>
 * 引入 {@code jaravel-starter} 即可获得中间件、Auth、Validation、Config、Eloquent ORM 全套能力。
 * <p>
 * 配置来源优先级：运行时覆盖 > 代码级配置({@link com.weacsoft.jaravel.vendor.core.config.ConfigDefinition})
 * > Spring Environment(application.yml)。
 */
@AutoConfiguration
@ConditionalOnClass(ConfigRepository.class)
public class JaravelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigRepository configRepository(Environment environment) {
        return new ConfigRepository(environment);
    }

    /**
     * 代码级配置注册器：收集容器中所有 {@link com.weacsoft.jaravel.vendor.core.config.ConfigDefinition}
     * Bean，在单例初始化完成后注册到 {@link ConfigRepository}。
     * <p>
     * 这里显式声明为 Bean，确保即使用户应用未扫描到 core 包也能生效
     * （与 {@link SpringContext}、{@link ProviderRegistry} 保持一致的双重注册策略）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigDefinitionRegistrar configDefinitionRegistrar() {
        return new ConfigDefinitionRegistrar();
    }

    /**
     * 确保 {@link SpringContext}（ApplicationContextAware）被注册，门面才能解析 Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderRegistry providerRegistry(java.util.List<com.weacsoft.jaravel.vendor.core.provider.ServiceProvider> providers) {
        return new ProviderRegistry(providers);
    }
}
