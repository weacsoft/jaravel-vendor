package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 远程插件服务端配置属性，前缀 {@code jaravel.plugin-jar.remote.server}。
 * <pre>
 * jaravel:
 *   plugin-jar:
 *     remote:
 *       server:
 *         enabled: true              # 启用 TCP 服务端
 *         port: 9700                 # TCP 监听端口
 *         auth-token: "secret-token" # 认证令牌（null 表示不认证）
 *         # 树形中继配置
 *         node-id: "node-root"       # 本节点 ID（树形拓扑中唯一标识）
 *         relay-enabled: true         # 启用中继转发（本地无插件时转发给子节点）
 *         max-hops: 5                # 请求最大跳数（防环）
 * </pre>
 * <p>
 * TCP 模式通过 {@code enabled=true} 自动启动。
 * HTTP 模式不自动注册端点，用户需自行创建控制器并调用
 * {@link HttpRpcHandler#processRequest} 静态方法。
 */
@ConfigurationProperties(prefix = "jaravel.plugin-jar.remote.server")
public class RemoteServerProperties {

    /** 是否启用 TCP 远程服务端 */
    private boolean enabled = false;

    /** TCP 监听端口 */
    private int port = 9700;

    /** 认证令牌（null 或空表示不认证） */
    private String authToken;

    /** 本节点 ID（树形拓扑中唯一标识，默认自动生成） */
    private String nodeId;

    /** 是否启用中继转发（本地无插件时转发给子节点，默认 false 保持向后兼容） */
    private boolean relayEnabled = false;

    /** 请求最大跳数（防环，默认 5） */
    private int maxHops = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public boolean isRelayEnabled() { return relayEnabled; }
    public void setRelayEnabled(boolean relayEnabled) { this.relayEnabled = relayEnabled; }
    public int getMaxHops() { return maxHops; }
    public void setMaxHops(int maxHops) { this.maxHops = maxHops; }
}
