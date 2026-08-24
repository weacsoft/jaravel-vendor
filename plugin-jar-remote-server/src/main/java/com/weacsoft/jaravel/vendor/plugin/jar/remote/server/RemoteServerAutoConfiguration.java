package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.spi.BeanResolver;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.spi.SpringBeanResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 远程插件服务端自动装配。
 * <p>
 * 当 {@code jaravel.plugin-jar.remote.server.enabled=true} 时自动启动 TCP 服务端。
 * <p>
 * <h3>Bean 解析器选择</h3>
 * 远程执行系统通过 {@link BeanResolver} 接口获取目标 Bean，与热加载插件系统解耦：
 * <ul>
 *   <li><b>有 plugin-jar-core</b>：使用 HotPluginManager 作为 BeanResolver（通过 lambda 适配），
 *       按 pluginId 从插件 ClassLoader 获取 Bean</li>
 *   <li><b>无 plugin-jar-core</b>：使用 {@link SpringBeanResolver}，直接从 Spring 容器按 beanName 获取 Bean</li>
 * </ul>
 * 引入本模块但未设置 enabled=true 时，不启动服务端，但 {@link RemotePluginServer} Bean 仍会创建，
 * 可在运行时通过方法调用 {@link RemotePluginServer#start()} 手动启动。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "jaravel.plugin-jar.remote.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RemoteServerProperties.class)
public class RemoteServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteServerAutoConfiguration.class);

    /**
     * 无热加载插件时的默认配置：使用 SpringBeanResolver。
     * <p>
     * 当 plugin-jar-core 不在类路径上时激活。
     */
    @Configuration
    @ConditionalOnMissingClass("com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager")
    static class DefaultBeanResolverConfig {
        @Bean
        @ConditionalOnMissingBean(BeanResolver.class)
        BeanResolver beanResolver(ApplicationContext applicationContext) {
            log.info("使用 SpringBeanResolver（无热加载模式，从 Spring 容器获取 Bean）");
            return new SpringBeanResolver(applicationContext);
        }
    }

    /**
     * 有热加载插件时的配置：使用 HotPluginManager 适配为 BeanResolver。
     * <p>
     * 当 plugin-jar-core 在类路径上时激活。
     */
    @Configuration
    @ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager")
    static class HotPluginBeanResolverConfig {

        @Bean
        @ConditionalOnMissingBean(BeanResolver.class)
        BeanResolver beanResolver(ApplicationContext applicationContext,
                                   ObjectProvider<com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager> managerProvider) {
            com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager manager = managerProvider.getIfAvailable();
            if (manager != null) {
                log.info("使用 HotPluginManager 作为 BeanResolver（热加载模式）");
                final com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager finalManager = manager;
                return (pluginId, beanName) -> finalManager.getServiceFromPlugin(pluginId, beanName);
            }
            // HotPluginManager Bean 不存在（可能 plugin-jar-core 在类路径但未启用），回退到 Spring
            log.info("HotPluginManager 未注册，回退到 SpringBeanResolver");
            return new SpringBeanResolver(applicationContext);
        }
    }

    /**
     * 创建远程插件服务端 Bean。
     *
     * @param properties 配置属性
     * @param beanResolver Bean 解析器
     * @return 远程插件服务端
     */
    @Bean
    public RemotePluginServer remotePluginServer(RemoteServerProperties properties,
                                                  BeanResolver beanResolver) {
        RemotePluginServer server = new RemotePluginServer(
                properties.getPort(), properties.getAuthToken(), beanResolver,
                properties.getNodeId(), properties.isRelayEnabled());
        server.setMaxHops(properties.getMaxHops());
        server.start();
        log.info("远程插件服务端已自动启动: port={}, relay={}", properties.getPort(), properties.isRelayEnabled());
        return server;
    }
}
