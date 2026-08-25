package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.config.ConfigDefinition;
import com.weacsoft.jaravel.vendor.core.config.ConfigDefinitionRegistrar;
import com.weacsoft.jaravel.vendor.core.config.ConfigRepository;
import com.weacsoft.jaravel.vendor.core.provider.ProviderRegistry;
import com.weacsoft.jaravel.vendor.core.provider.ServiceProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;

/**
 * Jaravel 核心自动装配：注册配置仓库与引导组件，聚合各模块的自动装配。
 * <p>
 * 引入 {@code jaravel-starter} 即可获得中间件、Auth、Validation、Config、Eloquent ORM 全套能力。
 * <p>
 * 配置来源优先级：运行时覆盖 &gt; 代码级配置({@link ConfigDefinition})
 * &gt; Spring Environment(application.yml)。
 * <p>
 * <h3>P3 解耦说明（Spring 解耦终章）</h3>
 * core 模块已零 Spring 依赖：
 * <ul>
 *   <li>{@code ConfigRepository} 改为纯类，外部配置层经 {@code environment::getProperty}
 *       函数注入（本 Bean 语义不变）；</li>
 *   <li>{@code ConfigDefinitionRegistrar} / {@code ProviderRegistry} 改为纯类，
 *       原「所有单例 Bean 初始化完成后」的 SmartInitializingSingleton 时序，
 *       现由本类的 SmartInitializingSingleton Bean 显式触发 {@code boot()}（行为不变）；</li>
 *   <li>P3 前的 {@code SpringContext}（ApplicationContextAware 持有器）Bean 已移除：
 *       core 静态门面改由 jaravel-springboot 的 {@code CoreSpringConfiguration} 安装的
 *       {@code GlobalBeanProvider}（{@code ContextBeanProvider}）驱动，
 *       对外静态 API（{@code SpringContext.bean/beanOrNull/registerSingleton}）与行为完全一致。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(ConfigRepository.class)
public class JaravelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigRepository configRepository(Environment environment) {
        return new ConfigRepository(environment::getProperty);
    }

    /**
     * 代码级配置注册器（纯类）：收集容器中所有 {@link ConfigDefinition} Bean，
     * 在单例初始化完成后注册到 {@link ConfigRepository}。
     * <p>
     * 这里显式声明为 Bean，确保即使用户应用未扫描到 core 包也能生效。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigDefinitionRegistrar configDefinitionRegistrar(
            ConfigRepository configRepository,
            ObjectProvider<List<ConfigDefinition>> definitions) {
        ConfigDefinitionRegistrar registrar = new ConfigDefinitionRegistrar(configRepository);
        registrar.setDefinitions(definitions.getIfAvailable(Collections::emptyList));
        return registrar;
    }

    /**
     * 代码级配置加载触发：保持 P3 前「所有单例 Bean 初始化完成后」的原始时序。
     */
    @Bean
    public SmartInitializingSingleton configDefinitionBoot(ConfigDefinitionRegistrar registrar) {
        return registrar::boot;
    }

    /**
     * 服务提供者两阶段引导注册器（纯类）：收集容器中所有 {@link ServiceProvider}。
     */
    @Bean
    @ConditionalOnMissingBean
    public ProviderRegistry providerRegistry(ObjectProvider<List<ServiceProvider>> providers) {
        return new ProviderRegistry(providers.getIfAvailable(Collections::emptyList));
    }

    /**
     * 服务提供者引导触发：保持 P3 前「所有单例 Bean 初始化完成后」的原始时序。
     */
    @Bean
    public SmartInitializingSingleton providerRegistryBoot(ProviderRegistry registry) {
        return registry::boot;
    }
}
