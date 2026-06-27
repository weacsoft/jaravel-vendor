package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程方法执行请求。
 * <p>
 * 由客户端发送给服务端，请求在服务端本地执行指定的插件方法。
 * <p>
 * 隔离度：方法级。每次执行都是独立的，不共享调用间状态。
 * <p>
 * <h3>树形路由元数据</h3>
 * 支持树形拓扑下的防环和跳数控制：
 * <ul>
 *   <li>{@link #sourceNodeId}：发起请求的节点 ID</li>
 *   <li>{@link #visitedNodes}：已经过的节点列表，防止请求环路</li>
 *   <li>{@link #maxHops}：最大跳数限制（默认 5），超过则拒绝转发</li>
 *   <li>{@link #currentHop}：当前跳数（每转发一次 +1）</li>
 * </ul>
 * 扁平模式下这些字段为默认值，不影响原有逻辑。
 */
public class ExecuteRequest {

    /** 请求 ID（用于匹配响应） */
    private String requestId;

    /** 插件 ID */
    private String pluginId;

    /** Bean 名称（在插件 Spring 容器中的名称） */
    private String beanName;

    /** 方法名 */
    private String methodName;

    /** 参数值列表（JSON 序列化） */
    private List<String> args;

    /** 参数类型列表（全限定类名，用于反射查找方法） */
    private List<String> argTypes;

    // ==================== 树形路由元数据 ====================

    /** 发起请求的节点 ID（树形模式下用于追溯） */
    private String sourceNodeId;

    /** 已访问的节点 ID 列表（防环） */
    private List<String> visitedNodes = new ArrayList<>();

    /** 最大跳数限制（默认 5） */
    private int maxHops = 5;

    /** 当前跳数（每转发一次 +1） */
    private int currentHop = 0;

    public ExecuteRequest() {
    }

    public ExecuteRequest(String requestId, String pluginId, String beanName,
                          String methodName, List<String> args, List<String> argTypes) {
        this.requestId = requestId;
        this.pluginId = pluginId;
        this.beanName = beanName;
        this.methodName = methodName;
        this.args = args;
        this.argTypes = argTypes;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getBeanName() { return beanName; }
    public void setBeanName(String beanName) { this.beanName = beanName; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }
    public List<String> getArgTypes() { return argTypes; }
    public void setArgTypes(List<String> argTypes) { this.argTypes = argTypes; }

    // ==================== 树形路由 Getter/Setter ====================

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public List<String> getVisitedNodes() {
        return visitedNodes != null ? visitedNodes : new ArrayList<>();
    }
    public void setVisitedNodes(List<String> visitedNodes) { this.visitedNodes = visitedNodes; }

    public int getMaxHops() { return maxHops; }
    public void setMaxHops(int maxHops) { this.maxHops = maxHops; }

    public int getCurrentHop() { return currentHop; }
    public void setCurrentHop(int currentHop) { this.currentHop = currentHop; }

    /**
     * 检查是否可以继续转发（未超过最大跳数且未形成环路）。
     *
     * @param nodeId 当前节点 ID
     * @return 可以转发返回 true
     */
    public boolean canRelay(String nodeId) {
        if (currentHop >= maxHops) {
            return false;
        }
        if (visitedNodes != null && visitedNodes.contains(nodeId)) {
            return false;
        }
        return true;
    }

    /**
     * 记录当前节点已访问，并增加跳数。返回新的请求副本用于转发。
     *
     * @param nodeId 当前节点 ID
     * @return 用于转发的请求副本
     */
    public ExecuteRequest markVisited(String nodeId) {
        ExecuteRequest copy = new ExecuteRequest(requestId, pluginId, beanName, methodName, args, argTypes);
        copy.setSourceNodeId(sourceNodeId != null ? sourceNodeId : nodeId);
        List<String> newVisited = new ArrayList<>(getVisitedNodes());
        newVisited.add(nodeId);
        copy.setVisitedNodes(newVisited);
        copy.setMaxHops(maxHops);
        copy.setCurrentHop(currentHop + 1);
        return copy;
    }
}
