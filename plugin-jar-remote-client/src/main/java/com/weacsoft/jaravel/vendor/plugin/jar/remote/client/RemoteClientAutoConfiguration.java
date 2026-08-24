package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.spi.BeanResolver;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.spi.SpringBeanResolver;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
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
 * 远程插件客户端自动装配。
 * <p>
 * 引入本模块后自动创建 {@link RemoteExecutionDispatcher} Bean。
 * <p>
 * <h3>传输模式选择</h3>
 * 通过配置 {@code jaravel.plugin-jar.remote.client.transport} 选择传输模式：
 * <ul>
 *   <li>{@code tcp}（默认）：使用 TCP 传输，高性能，需额外端口</li>
 *   <li>{@code http}：使用 HTTP JSON-RPC，复用 Web 端口，无需额外端口</li>
 * </ul>
 * <p>
 * <h3>Bean 解析器选择</h3>
 * 与服务端相同，客户端协调器也需要 {@link BeanResolver} 来执行本地优先策略：
 * <ul>
 *   <li>有 plugin-jar-core：使用 HotPluginManager 适配</li>
 *   <li>无 plugin-jar-core：使用 {@link SpringBeanResolver}，从 Spring 容器获取 Bean</li>
 * </ul>
 * 若不配置 BeanResolver（设为 null），协调器仅转发不本地执行。
 * <p>
 * <h3>安全设计</h3>
 * 不暴露任何 HTTP 接口，所有操作均通过 Java 方法调用。
 */
@AutoConfiguration
@ConditionalOnClass(RemoteExecutionDispatcher.class)
@ConditionalOnProperty(prefix = "jaravel.plugin-jar.remote.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RemoteClientProperties.class)
public class RemoteClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientAutoConfiguration.class);

    /**
     * 无热加载插件时的默认配置：使用 SpringBeanResolver。
     */
    @Configuration
    @ConditionalOnMissingClass("com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager")
    static class DefaultBeanResolverConfig {
        @Bean
        @ConditionalOnMissingBean(BeanResolver.class)
        BeanResolver beanResolver(ApplicationContext applicationContext) {
            log.info("客户端使用 SpringBeanResolver（无热加载模式）");
            return new SpringBeanResolver(applicationContext);
        }
    }

    /**
     * 有热加载插件时的配置：使用 HotPluginManager 适配为 BeanResolver。
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
                log.info("客户端使用 HotPluginManager 作为 BeanResolver（热加载模式）");
                final com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager finalManager = manager;
                return (pluginId, beanName) -> finalManager.getServiceFromPlugin(pluginId, beanName);
            }
            log.info("客户端 HotPluginManager 未注册，回退到 SpringBeanResolver");
            return new SpringBeanResolver(applicationContext);
        }
    }

    /**
     * 创建远程执行调度器 Bean。
     *
     * @param properties       客户端配置
     * @param beanResolverProvider Bean 解析器提供者（用于协调器本地执行）
     * @return 远程执行调度器
     */
    @Bean
    public RemoteExecutionDispatcher remoteExecutionDispatcher(
            RemoteClientProperties properties,
            ObjectProvider<BeanResolver> beanResolverProvider) {
        // 选择传输层
        RemoteTransport transport;
        if ("http".equalsIgnoreCase(properties.getTransport())) {
            transport = new HttpTransport(properties.getHttpEndpoint());
            log.info("远程插件客户端使用 HTTP 传输模式: endpoint={}", properties.getHttpEndpoint());
        } else {
            transport = new TcpTransport();
            log.info("远程插件客户端使用 TCP 传输模式");
        }

        // 注入 Bean 解析器（若存在）
        BeanResolver beanResolver = beanResolverProvider.getIfAvailable();

        RemoteExecutionDispatcher dispatcher = new RemoteExecutionDispatcher(transport, beanResolver);

        // 应用树形路由配置
        if (properties.isTreeRoutingEnabled()) {
            dispatcher.getCoordinator().setTreeRoutingEnabled(true);
            dispatcher.getCoordinator().setMaxHops(properties.getMaxHops());
            log.info("树形路由已启用: maxHops={}", properties.getMaxHops());
        }

        log.info("远程插件执行客户端已初始化: transport={}, localExecute={}, treeRouting={}",
                transport.getType(), beanResolver != null, properties.isTreeRoutingEnabled());
        return dispatcher;
    }
}
