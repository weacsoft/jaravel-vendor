package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 子服务器信息模型。
 * <p>
 * 描述一个远程子运算服务器的连接信息和状态。
 * <p>
 * <h3>树形拓扑支持</h3>
 * 支持父子层级关系，用于构建树形 P2SP 网络：
 * <ul>
 *   <li>{@link #parentId}：父节点 ID（null 表示根节点）</li>
 *   <li>{@link #childrenIds}：子节点 ID 列表</li>
 *   <li>{@link #depth}：节点深度（根节点=0）</li>
 *   <li>{@link #relayEnabled}：是否启用中继转发（默认 true）</li>
 * </ul>
 * 扁平模式下 parentId=null、childrenIds 为空，行为与原来完全一致。
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

    // ==================== 树形拓扑字段 ====================

    /** 父节点 ID（null 表示根节点，无父节点） */
    private String parentId;

    /** 子节点 ID 列表 */
    private List<String> childrenIds = new ArrayList<>();

    /** 节点深度（根节点=0，每往下一层 +1） */
    private int depth = 0;

    /** 是否启用中继转发（true=本地执行不了时转发给子节点） */
    private boolean relayEnabled = true;

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

    /** 树形拓扑构造方法 */
    public SubServerInfo(String id, String host, int port, String authToken,
                         String parentId, int depth) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.authToken = authToken;
        this.parentId = parentId;
        this.depth = depth;
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

    // ==================== 树形拓扑 Getter/Setter ====================

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public List<String> getChildrenIds() {
        return childrenIds != null ? childrenIds : new ArrayList<>();
    }
    public void setChildrenIds(List<String> childrenIds) { this.childrenIds = childrenIds; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public boolean isRelayEnabled() { return relayEnabled; }
    public void setRelayEnabled(boolean relayEnabled) { this.relayEnabled = relayEnabled; }

    /** 是否为根节点（无父节点） */
    public boolean isRoot() { return parentId == null || parentId.isEmpty(); }

    /** 是否为叶子节点（无子节点） */
    public boolean isLeaf() { return getChildrenIds().isEmpty(); }

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
                ", online=" + online + ", parentId=" + parentId + ", depth=" + depth +
                ", children=" + getChildrenIds().size() + "}";
    }
}
