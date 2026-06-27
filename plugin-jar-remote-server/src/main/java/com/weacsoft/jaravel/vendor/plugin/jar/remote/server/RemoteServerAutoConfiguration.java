package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 远程插件服务端自动装配。
 * <p>
 * 当 {@code jaravel.plugin-jar.remote.server.enabled=true} 时自动启动 TCP 服务端。
 * <p>
 * 引入本模块但未设置 enabled=true 时，不启动服务端，但 {@link RemotePluginServer} Bean 仍会创建，
 * 可在运行时通过方法调用 {@link RemotePluginServer#start()} 手动启动。
 */
@AutoConfiguration
@ConditionalOnClass(HotPluginManager.class)
@ConditionalOnProperty(prefix = "jaravel.plugin-jar.remote.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RemoteServerProperties.class)
public class RemoteServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteServerAutoConfiguration.class);

    /**
     * 创建远程插件服务端 Bean。
     * <p>
     * 注意：此 Bean 在 enabled=true 时创建并自动启动。
     * 若需运行时手动启动，可不设置 enabled=true，而是在代码中直接 new RemotePluginServer(...)。
     *
     * @param properties 配置属性
     * @param manager    插件管理器
     * @return 远程插件服务端
     */
    @Bean
    public RemotePluginServer remotePluginServer(RemoteServerProperties properties,
                                                  HotPluginManager manager) {
        Application.HotPluginManagerRef ref = manager;
        RemotePluginServer server = new RemotePluginServer(
                properties.getPort(), properties.getAuthToken(), ref,
                properties.getNodeId(), properties.isRelayEnabled());
        server.setMaxHops(properties.getMaxHops());
        server.start();
        log.info("远程插件服务端已自动启动: port={}, relay={}", properties.getPort(), properties.isRelayEnabled());
        return server;
    }
}
