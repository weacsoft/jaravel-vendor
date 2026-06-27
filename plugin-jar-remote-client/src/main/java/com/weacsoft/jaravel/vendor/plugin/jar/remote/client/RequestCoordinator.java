package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 请求协调器（CS 中心化架构核心）。
 * <p>
 * 当客户端不指定子服务器时，请求发送到协调器，由协调器决定在哪个节点执行：
 * <ol>
 *   <li>优先本地执行：若插件在本地已加载，直接通过 {@link Application#getService} 获取 Bean 并执行</li>
 *   <li>远程转发：若本地无此插件，从 {@link SubServerRegistry} 选择一个在线子服务器转发</li>
 * </ol>
 * <p>
 * <h3>CS 中心化架构</h3>
 * <pre>
 *  客户端 ──→ 协调器（中心服务器） ──→ 子服务器 A
 *                    │                 ──→ 子服务器 B
 *                    │                 ──→ 子服务器 C
 *                    ↓
 *               本地执行（若插件已加载）
 * </pre>
 * 协调器本身也是一个服务器节点，可以本地执行，也可以转发到其他子服务器。
 * <p>
 * <h3>树形路由</h3>
 * 当 {@code treeRoutingEnabled=true} 时，协调器按树形层级路由：
 * <ol>
 *   <li>优先转发给根子服务器（直接子节点）</li>
 *   <li>根子服务器本地执行不了时，由其 {@link RemotePluginServer} 中继转发给孙节点</li>
 *   <li>请求中携带路由元数据（sourceNodeId/visitedNodes/maxHops）防止环路</li>
 * </ol>
 * 扁平模式下 {@code treeRoutingEnabled=false}，行为与原来完全一致。
 * <p>
 * <h3>负载均衡</h3>
 * 远程转发时使用轮询策略选择子服务器。树形模式下优先选择有中继能力的节点。
 */
public class RequestCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RequestCoordinator.class);

    private final SubServerRegistry registry;
    private final RemoteTransport transport;
    private final Application.HotPluginManagerRef localManagerRef;
    private volatile int roundRobinIndex = 0;

    /** 是否启用树形路由 */
    private volatile boolean treeRoutingEnabled = false;

    /** 协调器节点 ID（用于路由元数据） */
    private String coordinatorNodeId = "coordinator";

    /** 默认最大跳数 */
    private volatile int maxHops = 5;

    /**
     * 构造请求协调器。
     *
     * @param registry         子服务器注册表
     * @param transport        传输层（TCP 或 HTTP）
     * @param localManagerRef  本地插件管理器引用（null 表示不本地执行）
     */
    public RequestCoordinator(SubServerRegistry registry, RemoteTransport transport,
                              Application.HotPluginManagerRef localManagerRef) {
        this.registry = registry;
        this.transport = transport;
        this.localManagerRef = localManagerRef;
    }

    /**
     * 调度执行请求。
     * <p>
     * 优先本地执行，本地无此插件时转发到子服务器。
     *
     * @param request 执行请求
     * @return 执行响应
     */
    public ExecuteResponse dispatch(ExecuteRequest request) {
        // 1. 尝试本地执行
        if (localManagerRef != null) {
            ExecuteResponse localResult = tryLocalExecute(request);
            if (localResult != null) {
                return localResult;
            }
        }
        // 2. 转发到子服务器
        return forwardToSubServer(request);
    }

    /**
     * 尝试本地执行。
     *
     * @param request 执行请求
     * @return 执行成功返回响应，本地无此插件返回 null
     */
    private ExecuteResponse tryLocalExecute(ExecuteRequest request) {
        try {
            Object bean = localManagerRef.getServiceFromPlugin(request.getPluginId(), request.getBeanName());
            if (bean == null) {
                return null; // 本地无此插件
            }
            java.lang.reflect.Method targetMethod = findMethod(bean.getClass(),
                    request.getMethodName(), request.getArgTypes());
            if (targetMethod == null) {
                return ExecuteResponse.error(request.getRequestId(),
                        "本地方法未找到: " + request.getMethodName());
            }
            Object[] args = resolveArguments(request.getArgs(), request.getArgTypes(), targetMethod);
            Object result = targetMethod.invoke(bean, args);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String resultJson = result != null ? mapper.writeValueAsString(result) : null;
            String resultType = result != null ? result.getClass().getName() : null;
            return ExecuteResponse.ok(request.getRequestId(), resultJson, resultType);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.debug("本地执行失败，将尝试远程转发: {}", cause.getMessage());
            return null; // 本地执行失败，尝试远程
        }
    }

    /**
     * 转发到子服务器（轮询负载均衡，树形感知）。
     * <p>
     * 树形模式下：优先转发给根子服务器，并在请求中携带路由元数据。
     * 子服务器收到后若本地无此插件，会通过中继转发给孙节点。
     * <p>
     * 扁平模式下：直接轮询所有在线子服务器。
     *
     * @param request 执行请求
     * @return 执行响应
     */
    private synchronized ExecuteResponse forwardToSubServer(ExecuteRequest request) {
        List<SubServerInfo> candidates;

        if (treeRoutingEnabled) {
            // 树形模式：优先根子服务器（直接子节点）
            candidates = registry.getOnlineSubServers();
            // 过滤出根节点（parentId 为空的）
            List<SubServerInfo> roots = new java.util.ArrayList<>();
            for (SubServerInfo s : candidates) {
                if (s.isRoot()) {
                    roots.add(s);
                }
            }
            if (!roots.isEmpty()) {
                candidates = roots;
            }
            // 如果根节点都有中继能力，优先选择
            candidates = sortByRelayCapability(candidates);

            // 设置路由元数据
            if (request.getSourceNodeId() == null) {
                request.setSourceNodeId(coordinatorNodeId);
            }
            if (request.getVisitedNodes() == null || request.getVisitedNodes().isEmpty()) {
                List<String> visited = new java.util.ArrayList<>();
                visited.add(coordinatorNodeId);
                request.setVisitedNodes(visited);
            }
            request.setMaxHops(maxHops);
        } else {
            // 扁平模式：所有在线子服务器
            candidates = registry.getOnlineSubServers();
            if (candidates.isEmpty()) {
                candidates = registry.getSubServers();
            }
        }

        if (candidates.isEmpty()) {
            return ExecuteResponse.error(request.getRequestId(), "无可用子服务器");
        }

        // 轮询选择，最多尝试 3 个
        int maxAttempts = Math.min(3, candidates.size());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int idx = (roundRobinIndex + attempt) % candidates.size();
            SubServerInfo target = candidates.get(idx);
            log.debug("转发请求到子服务器: {} -> {}:{} (relay={}, depth={})",
                    target.getId(), target.getHost(), target.getPort(),
                    target.isRelayEnabled(), target.getDepth());

            ExecuteResponse response = transport.send(
                    target.getHost(), target.getPort(), target.getAuthToken(), request);
            if (response.isSuccess()) {
                registry.updateOnlineStatus(target.getId(), true);
                roundRobinIndex = idx + 1;
                return response;
            }
            // 连接错误才尝试下一个，业务错误直接返回
            if (response.getError() != null &&
                !response.getError().contains("连接") &&
                !response.getError().contains("timeout") &&
                !response.getError().contains("Connection")) {
                registry.updateOnlineStatus(target.getId(), true);
                roundRobinIndex = idx + 1;
                return response;
            }
            // 连接错误，标记离线，尝试下一个
            registry.updateOnlineStatus(target.getId(), false);
            log.warn("子服务器不可用: {}，尝试下一个", target.getId());
        }

        roundRobinIndex++;
        return ExecuteResponse.error(request.getRequestId(), "所有子服务器均不可用");
    }

    /**
     * 按中继能力排序（relayEnabled=true 的排前面）。
     */
    private List<SubServerInfo> sortByRelayCapability(List<SubServerInfo> servers) {
        List<SubServerInfo> sorted = new java.util.ArrayList<>(servers);
        sorted.sort((a, b) -> {
            if (a.isRelayEnabled() == b.isRelayEnabled()) return 0;
            return a.isRelayEnabled() ? -1 : 1;
        });
        return sorted;
    }

    // ==================== 树形路由配置 ====================

    /**
     * 设置是否启用树形路由。
     */
    public void setTreeRoutingEnabled(boolean treeRoutingEnabled) {
        this.treeRoutingEnabled = treeRoutingEnabled;
        log.info("[coordinator] 树形路由: {}", treeRoutingEnabled ? "已启用" : "已禁用");
    }

    /**
     * 返回是否启用树形路由。
     */
    public boolean isTreeRoutingEnabled() {
        return treeRoutingEnabled;
    }

    /**
     * 设置协调器节点 ID。
     */
    public void setCoordinatorNodeId(String coordinatorNodeId) {
        this.coordinatorNodeId = coordinatorNodeId;
    }

    /**
     * 返回协调器节点 ID。
     */
    public String getCoordinatorNodeId() {
        return coordinatorNodeId;
    }

    /**
     * 设置最大跳数。
     */
    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }

    /**
     * 返回最大跳数。
     */
    public int getMaxHops() {
        return maxHops;
    }

    private java.lang.reflect.Method findMethod(Class<?> beanClass, String methodName, List<String> argTypes) {
        for (java.lang.reflect.Method method : beanClass.getMethods()) {
            if (!method.getName().equals(methodName)) continue;
            if (argTypes == null || argTypes.isEmpty()) return method;
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != argTypes.size()) continue;
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].getName().equals(argTypes.get(i))
                        && !paramTypes[i].getSimpleName().equals(argTypes.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return method;
        }
        // 回退：按方法名匹配
        for (java.lang.reflect.Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) return method;
        }
        return null;
    }

    private Object[] resolveArguments(List<String> args, List<String> argTypes,
                                       java.lang.reflect.Method method) {
        if (args == null || args.isEmpty()) return new Object[0];
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] result = new Object[Math.min(args.size(), paramTypes.length)];
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int i = 0; i < result.length; i++) {
            try {
                result[i] = mapper.readValue(args.get(i), paramTypes[i]);
            } catch (Exception e) {
                result[i] = args.get(i);
            }
        }
        return result;
    }
}
