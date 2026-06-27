package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 远程插件客户端配置属性，前缀 {@code jaravel.plugin-jar.remote.client}。
 * <pre>
 * jaravel:
 *   plugin-jar:
 *     remote:
 *       client:
 *         enabled: true              # 引入模块后默认启用
 *         transport: tcp             # 传输模式：tcp（默认）或 http
 *         http-endpoint: /my-rpc     # HTTP 模式下的 RPC 端点路径（需与服务端一致）
 *         # 树形路由配置
 *         tree-routing-enabled: true # 启用树形路由（优先转发给根子服务器）
 *         max-hops: 5                # 请求最大跳数（防环）
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.plugin-jar.remote.client")
public class RemoteClientProperties {

    private boolean enabled = true;

    /** 传输模式：tcp（默认，高性能，需额外端口）或 http（JSON-RPC，复用 Web 端口） */
    private String transport = "tcp";

    /** HTTP 模式下的 RPC 端点路径（需与服务端控制器中注册的路径一致） */
    private String httpEndpoint = HttpTransport.DEFAULT_ENDPOINT;

    /** 是否启用树形路由（优先转发给根子服务器，默认 false 保持向后兼容） */
    private boolean treeRoutingEnabled = false;

    /** 请求最大跳数（防环，默认 5） */
    private int maxHops = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public String getHttpEndpoint() { return httpEndpoint; }
    public void setHttpEndpoint(String httpEndpoint) { this.httpEndpoint = httpEndpoint; }
    public boolean isTreeRoutingEnabled() { return treeRoutingEnabled; }
    public void setTreeRoutingEnabled(boolean treeRoutingEnabled) { this.treeRoutingEnabled = treeRoutingEnabled; }
    public int getMaxHops() { return maxHops; }
    public void setMaxHops(int maxHops) { this.maxHops = maxHops; }
}
