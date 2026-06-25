package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
 * <h3>协调器模式</h3>
 * 若 {@code HotPluginManager} Bean 存在，自动注入为本地执行引用，
 * 使 {@link RemoteExecutionDispatcher#execute} 支持本地优先执行。
 * <p>
 * <h3>安全设计</h3>
 * 不暴露任何 HTTP 接口，所有操作均通过 Java 方法调用。
 */
@AutoConfiguration
@ConditionalOnClass(RemoteExecutionDispatcher.class)
@EnableConfigurationProperties(RemoteClientProperties.class)
public class RemoteClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientAutoConfiguration.class);

    /**
     * 创建远程执行调度器 Bean。
     * <p>
     * 根据配置选择 TCP 或 HTTP 传输，并注入本地插件管理器引用（若存在）。
     *
     * @param properties       客户端配置
     * @param managerProvider  插件管理器（可选，存在时启用协调器本地执行）
     * @return 远程执行调度器
     */
    @Bean
    public RemoteExecutionDispatcher remoteExecutionDispatcher(
            RemoteClientProperties properties,
            ObjectProvider<HotPluginManager> managerProvider) {
        // 选择传输层
        RemoteTransport transport;
        if ("http".equalsIgnoreCase(properties.getTransport())) {
            transport = new HttpTransport(properties.getHttpEndpoint());
            log.info("远程插件客户端使用 HTTP 传输模式: endpoint={}", properties.getHttpEndpoint());
        } else {
            transport = new TcpTransport();
            log.info("远程插件客户端使用 TCP 传输模式");
        }
        // 注入本地插件管理器（若存在）
        HotPluginManager manager = managerProvider.getIfAvailable();
        Application.HotPluginManagerRef ref = manager;
        RemoteExecutionDispatcher dispatcher = new RemoteExecutionDispatcher(transport, ref);
        log.info("远程插件执行客户端已初始化: transport={}, localExecute={}",
                transport.getType(), ref != null);
        return dispatcher;
    }
}
