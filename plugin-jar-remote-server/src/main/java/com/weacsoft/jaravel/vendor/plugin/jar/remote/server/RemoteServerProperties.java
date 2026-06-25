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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
}
