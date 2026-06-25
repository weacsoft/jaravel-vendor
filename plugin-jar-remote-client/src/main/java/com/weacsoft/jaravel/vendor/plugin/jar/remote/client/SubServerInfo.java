package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import java.util.Objects;

/**
 * 子服务器信息模型。
 * <p>
 * 描述一个远程子运算服务器的连接信息和状态。
 */
public class SubServerInfo {

    /** 子服务器唯一标识 */
    private String id;

    /** 主机地址 */
    private String host;

    /** TCP 端口 */
    private int port;

    /** 认证令牌（可选） */
    private String authToken;

    /** 是否在线 */
    private boolean online;

    /** 最后心跳时间戳（毫秒） */
    private long lastHeartbeat;

    public SubServerInfo() {
    }

    public SubServerInfo(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public SubServerInfo(String id, String host, int port, String authToken) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.authToken = authToken;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubServerInfo that = (SubServerInfo) o;
        return port == that.port && Objects.equals(id, that.id) && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, host, port);
    }

    @Override
    public String toString() {
        return "SubServerInfo{id='" + id + "', host='" + host + "', port=" + port +
                ", online=" + online + "}";
    }
}
