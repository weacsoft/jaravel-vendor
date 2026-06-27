package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子服务器注册表。
 * <p>
 * 管理所有已注册的远程子运算服务器。提供注册、注销、查询等方法。
 * <p>
 * <h3>树形拓扑支持</h3>
 * 支持树形层级关系管理，每个节点可以有父节点和子节点：
 * <ul>
 *   <li>{@link #registerChild}：注册子节点并自动维护父子关系</li>
 *   <li>{@link #getChildren}：获取直接子节点列表</li>
 *   <li>{@link #getSubtree}：递归获取整个子树</li>
 *   <li>{@link #getAncestors}：获取祖先链（从父节点到根节点）</li>
 *   <li>{@link #getRoots}：获取所有根节点</li>
 *   <li>{@link #getOnlineDescendants}：获取所有在线的后代节点</li>
 * </ul>
 * 扁平模式下（parentId=null），这些方法仍可工作，只是返回空列表。
 * <p>
 * <b>安全设计</b>：本类仅提供 Java 方法，不暴露 HTTP 接口。
 * 调用方（如管理后台）通过方法调用操作子服务器列表，
 * 可自行决定是否包装为 HTTP 接口（不推荐预先暴露）。
 * <p>
 * 线程安全：使用 {@link ConcurrentHashMap}。
 */
public class SubServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(SubServerRegistry.class);

    /** 默认最大树深度 */
    private static final int DEFAULT_MAX_DEPTH = 5;

    private final Map<String, SubServerInfo> servers = new ConcurrentHashMap<>();
    private volatile int maxDepth = DEFAULT_MAX_DEPTH;

    /**
     * 注册子服务器。
     * <p>
     * 若 ID 已存在，更新连接信息。
     *
     * @param id        子服务器唯一标识
     * @param host      主机地址
     * @param port      TCP 端口
     * @param authToken 认证令牌（可选，null 表示不认证）
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerSubServer(String id, String host, int port, String authToken) {
        SubServerInfo info = new SubServerInfo(id, host, port, authToken);
        servers.put(id, info);
        log.info("注册子服务器: id={}, host={}, port={}", id, host, port);
        return info;
    }

    /**
     * 注册子服务器（无认证）。
     *
     * @param id   子服务器唯一标识
     * @param host 主机地址
     * @param port TCP 端口
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerSubServer(String id, String host, int port) {
        return registerSubServer(id, host, port, null);
    }

    /**
     * 注销子服务器。
     *
     * @param id 子服务器唯一标识
     * @return 注销成功返回 true，不存在返回 false
     */
    public boolean unregisterSubServer(String id) {
        SubServerInfo removed = servers.remove(id);
        if (removed != null) {
            log.info("注销子服务器: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 获取子服务器信息。
     *
     * @param id 子服务器唯一标识
     * @return 子服务器信息，不存在返回 null
     */
    public SubServerInfo getSubServer(String id) {
        return servers.get(id);
    }

    /**
     * 获取所有已注册的子服务器。
     *
     * @return 子服务器列表
     */
    public List<SubServerInfo> getSubServers() {
        return new ArrayList<>(servers.values());
    }

    /**
     * 获取所有在线的子服务器。
     *
     * @return 在线子服务器列表
     */
    public List<SubServerInfo> getOnlineSubServers() {
        List<SubServerInfo> result = new ArrayList<>();
        for (SubServerInfo info : servers.values()) {
            if (info.isOnline()) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 更新子服务器在线状态。
     *
     * @param id     子服务器唯一标识
     * @param online 是否在线
     */
    public void updateOnlineStatus(String id, boolean online) {
        SubServerInfo info = servers.get(id);
        if (info != null) {
            info.setOnline(online);
            if (online) {
                info.setLastHeartbeat(System.currentTimeMillis());
            }
        }
    }

    /**
     * 返回已注册的子服务器数量。
     */
    public int size() {
        return servers.size();
    }

    /**
     * 清空所有子服务器。
     */
    public void clear() {
        servers.clear();
        log.info("清空子服务器注册表");
    }

    // ==================== 树形拓扑方法 ====================

    /**
     * 注册子节点（树形模式）。
     * <p>
     * 自动维护父子关系：将子节点 ID 添加到父节点的 childrenIds 列表中。
     * 若父节点不存在，仅注册子节点但不建立父子关系。
     *
     * @param id         子节点唯一标识
     * @param host       主机地址
     * @param port       端口
     * @param authToken  认证令牌
     * @param parentId   父节点 ID（null 表示根节点）
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerChild(String id, String host, int port, String authToken,
                                        String parentId) {
        int depth = 0;
        if (parentId != null && !parentId.isEmpty()) {
            SubServerInfo parent = servers.get(parentId);
            if (parent != null) {
                depth = parent.getDepth() + 1;
                if (depth > maxDepth) {
                    log.warn("子节点深度 {} 超过最大深度 {}，拒绝注册: id={}, parentId={}",
                            depth, maxDepth, id, parentId);
                    throw new IllegalStateException("节点深度超过最大限制: " + depth + " > " + maxDepth);
                }
                parent.getChildrenIds().add(id);
            } else {
                log.warn("父节点不存在: parentId={}，将作为根节点注册: id={}", parentId, id);
                parentId = null;
            }
        }
        SubServerInfo info = new SubServerInfo(id, host, port, authToken, parentId, depth);
        servers.put(id, info);
        log.info("注册子节点: id={}, host={}, port={}, parentId={}, depth={}", id, host, port, parentId, depth);
        return info;
    }

    /**
     * 注册子节点（无认证）。
     */
    public SubServerInfo registerChild(String id, String host, int port, String parentId) {
        return registerChild(id, host, port, null, parentId);
    }

    /**
     * 获取指定节点的直接子节点列表。
     *
     * @param parentId 父节点 ID
     * @return 子节点列表（不含孙节点），父节点不存在返回空列表
     */
    public List<SubServerInfo> getChildren(String parentId) {
        SubServerInfo parent = servers.get(parentId);
        if (parent == null) {
            return new ArrayList<>();
        }
        List<SubServerInfo> children = new ArrayList<>();
        for (String childId : parent.getChildrenIds()) {
            SubServerInfo child = servers.get(childId);
            if (child != null) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * 获取指定节点的在线直接子节点。
     *
     * @param parentId 父节点 ID
     * @return 在线子节点列表
     */
    public List<SubServerInfo> getOnlineChildren(String parentId) {
        List<SubServerInfo> children = getChildren(parentId);
        List<SubServerInfo> online = new ArrayList<>();
        for (SubServerInfo child : children) {
            if (child.isOnline()) {
                online.add(child);
            }
        }
        return online;
    }

    /**
     * 递归获取整个子树（包含自身和所有后代）。
     *
     * @param rootId 子树根节点 ID
     * @return 子树所有节点列表（含根节点）
     */
    public List<SubServerInfo> getSubtree(String rootId) {
        List<SubServerInfo> result = new ArrayList<>();
        collectSubtree(rootId, result);
        return result;
    }

    private void collectSubtree(String nodeId, List<SubServerInfo> result) {
        SubServerInfo node = servers.get(nodeId);
        if (node == null) {
            return;
        }
        result.add(node);
        for (String childId : node.getChildrenIds()) {
            collectSubtree(childId, result);
        }
    }

    /**
     * 获取祖先链（从父节点到根节点）。
     *
     * @param nodeId 起始节点 ID
     * @return 祖先列表（从近到远），节点不存在返回空列表
     */
    public List<SubServerInfo> getAncestors(String nodeId) {
        List<SubServerInfo> ancestors = new ArrayList<>();
        SubServerInfo current = servers.get(nodeId);
        if (current == null) {
            return ancestors;
        }
        String parentId = current.getParentId();
        while (parentId != null && !parentId.isEmpty()) {
            SubServerInfo parent = servers.get(parentId);
            if (parent == null) {
                break;
            }
            ancestors.add(parent);
            parentId = parent.getParentId();
        }
        return ancestors;
    }

    /**
     * 获取所有根节点（parentId 为空的节点）。
     *
     * @return 根节点列表
     */
    public List<SubServerInfo> getRoots() {
        List<SubServerInfo> roots = new ArrayList<>();
        for (SubServerInfo info : servers.values()) {
            if (info.isRoot()) {
                roots.add(info);
            }
        }
        return roots;
    }

    /**
     * 获取指定节点的所有在线后代（递归）。
     *
     * @param nodeId 起始节点 ID
     * @return 在线后代列表（不含自身）
     */
    public List<SubServerInfo> getOnlineDescendants(String nodeId) {
        List<SubServerInfo> result = new ArrayList<>();
        collectOnlineDescendants(nodeId, result);
        return result;
    }

    private void collectOnlineDescendants(String nodeId, List<SubServerInfo> result) {
        SubServerInfo node = servers.get(nodeId);
        if (node == null) {
            return;
        }
        for (String childId : node.getChildrenIds()) {
            SubServerInfo child = servers.get(childId);
            if (child != null) {
                if (child.isOnline()) {
                    result.add(child);
                }
                collectOnlineDescendants(childId, result);
            }
        }
    }

    /**
     * 设置最大树深度。
     *
     * @param maxDepth 最大深度
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * 返回最大树深度。
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 打印树形结构（用于调试）。
     *
     * @return 树形结构字符串
     */
    public String dumpTree() {
        StringBuilder sb = new StringBuilder();
        for (SubServerInfo root : getRoots()) {
            dumpTreeNode(root, 0, sb);
        }
        return sb.toString();
    }

    private void dumpTreeNode(SubServerInfo node, int indent, StringBuilder sb) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        sb.append(node.isOnline() ? "[+]" : "[-]");
        sb.append(" ").append(node.getId());
        sb.append(" (").append(node.getHost()).append(":").append(node.getPort()).append(")");
        if (node.isLeaf()) {
            sb.append(" [leaf]");
        }
        sb.append("\n");
        for (String childId : node.getChildrenIds()) {
            SubServerInfo child = servers.get(childId);
            if (child != null) {
                dumpTreeNode(child, indent + 1, sb);
            }
        }
    }
}
