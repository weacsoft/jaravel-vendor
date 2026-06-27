package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ProtocolCodec;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程插件执行服务端。
 * <p>
 * 监听 TCP 端口，接收来自客户端的方法执行请求，在本地通过 {@link Application#getService}
 * 获取插件 Bean 并反射调用目标方法，返回执行结果。
 * <p>
 * <h3>树形中继转发</h3>
 * 当 {@code relayEnabled=true} 时，服务端不仅能在本地执行，还能将请求转发给子节点：
 * <ol>
 *   <li>优先本地执行（插件已加载）</li>
 *   <li>本地无此插件时，轮询转发给已注册的子节点</li>
 *   <li>子节点执行失败时，返回错误</li>
 * </ol>
 * 通过 {@link #registerChildNode} 注册子节点，通过 {@link #setRelayEnabled} 控制中继开关。
 * 防环机制：转发时携带 visitedNodes 和 maxHops，子节点收到后检查是否已访问。
 * <p>
 * <h3>线程模型</h3>
 * 使用独立线程池处理每个客户端连接，支持并发请求。
 * <p>
 * <h3>安全设计</h3>
 * <ul>
 *   <li>不暴露 HTTP 接口，仅通过 TCP 通信</li>
 *   <li>支持 authToken 握手认证（可选）</li>
 *   <li>请求体大小限制 50MB</li>
 * </ul>
 * <p>
 * <h3>方法级隔离</h3>
 * 每次方法调用都是独立的：获取 Bean → 反射调用 → 返回结果。
 * 不保留调用间状态，隔离度由插件 ClassLoader 保证。
 */
public class RemotePluginServer {

    private static final Logger log = LoggerFactory.getLogger(RemotePluginServer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int port;
    private final String authToken;
    private final Application.HotPluginManagerRef managerRef;

    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private final ConcurrentHashMap<String, Socket> activeConnections = new ConcurrentHashMap<>();

    // ==================== 树形中继字段 ====================

    /** 本节点 ID（树形拓扑中唯一标识） */
    private String nodeId = "server-" + UUID.randomUUID().toString().substring(0, 8);

    /** 是否启用中继转发（默认 false，保持向后兼容） */
    private volatile boolean relayEnabled = false;

    /** 最大跳数限制 */
    private volatile int maxHops = 5;

    /** 子节点注册表（id → ChildNodeInfo） */
    private final Map<String, ChildNodeInfo> childNodes = new ConcurrentHashMap<>();

    /** 轮询计数器（子节点负载均衡） */
    private volatile int roundRobinIndex = 0;

    /**
     * 子节点信息（轻量级，不依赖 client 模块的 SubServerInfo）。
     */
    public static class ChildNodeInfo {
        public final String id;
        public final String host;
        public final int port;
        public final String authToken;
        public volatile boolean online = true;

        public ChildNodeInfo(String id, String host, int port, String authToken) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.authToken = authToken;
        }
    }

    /**
     * 构造远程插件执行服务端。
     *
     * @param port      监听端口
     * @param authToken 认证令牌（null 表示不认证）
     * @param managerRef 插件管理器引用
     */
    public RemotePluginServer(int port, String authToken, Application.HotPluginManagerRef managerRef) {
        this.port = port;
        this.authToken = authToken;
        this.managerRef = managerRef;
    }

    /**
     * 构造远程插件执行服务端（树形模式）。
     *
     * @param port         监听端口
     * @param authToken    认证令牌
     * @param managerRef   插件管理器引用
     * @param nodeId       本节点 ID
     * @param relayEnabled 是否启用中继转发
     */
    public RemotePluginServer(int port, String authToken, Application.HotPluginManagerRef managerRef,
                               String nodeId, boolean relayEnabled) {
        this.port = port;
        this.authToken = authToken;
        this.managerRef = managerRef;
        this.nodeId = nodeId != null ? nodeId : this.nodeId;
        this.relayEnabled = relayEnabled;
    }

    // ==================== 树形中继 API ====================

    /**
     * 注册子节点（中继转发目标）。
     *
     * @param id        子节点 ID
     * @param host      子节点主机地址
     * @param port      子节点端口
     * @param authToken 子节点认证令牌（可选）
     */
    public void registerChildNode(String id, String host, int port, String authToken) {
        childNodes.put(id, new ChildNodeInfo(id, host, port, authToken));
        log.info("[relay] 注册子节点: id={}, host={}, port={}", id, host, port);
    }

    /**
     * 注册子节点（无认证）。
     */
    public void registerChildNode(String id, String host, int port) {
        registerChildNode(id, host, port, null);
    }

    /**
     * 注销子节点。
     */
    public boolean unregisterChildNode(String id) {
        ChildNodeInfo removed = childNodes.remove(id);
        if (removed != null) {
            log.info("[relay] 注销子节点: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 获取所有已注册的子节点。
     */
    public List<ChildNodeInfo> getChildNodes() {
        return new ArrayList<>(childNodes.values());
    }

    /**
     * 获取所有在线的子节点。
     */
    public List<ChildNodeInfo> getOnlineChildNodes() {
        List<ChildNodeInfo> online = new ArrayList<>();
        for (ChildNodeInfo child : childNodes.values()) {
            if (child.online) {
                online.add(child);
            }
        }
        return online;
    }

    /**
     * 设置是否启用中继转发。
     */
    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
        log.info("[relay] 中继转发: {}", relayEnabled ? "已启用" : "已禁用");
    }

    /**
     * 返回是否启用中继转发。
     */
    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    /**
     * 设置最大跳数。
     */
    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }

    /**
     * 返回本节点 ID。
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 设置本节点 ID。
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动 TCP 服务端。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("远程服务端已在运行");
            return;
        }
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "remote-plugin-server-worker");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::acceptLoop);
        log.info("远程插件服务端已启动: port={}, auth={}, nodeId={}, relay={}",
                port, authToken != null, nodeId, relayEnabled);
    }

    /**
     * 停止 TCP 服务端。
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("关闭 ServerSocket 失败", e);
        }
        for (Socket socket : activeConnections.values()) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        activeConnections.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("远程插件服务端已停止");
    }

    /**
     * 返回是否正在运行。
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 返回监听端口。
     */
    public int getPort() {
        return port;
    }

    /**
     * 返回当前活跃连接数。
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    // ==================== 内部处理逻辑 ====================

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("远程插件服务端监听: port={}", port);
            while (running.get()) {
                Socket socket = serverSocket.accept();
                String connId = UUID.randomUUID().toString();
                activeConnections.put(connId, socket);
                executor.submit(() -> handleConnection(socket, connId));
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("远程插件服务端异常", e);
            }
        }
    }

    private void handleConnection(Socket socket, String connId) {
        log.debug("客户端连接: {} from {}", connId, socket.getRemoteSocketAddress());
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            // 握手
            if (!handshake(in, out)) {
                log.warn("握手失败: {}", connId);
                return;
            }
            // 消息循环
            while (running.get() && !s.isClosed()) {
                Object[] frame = ProtocolCodec.readFrameWithType(in);
                int msgType = (int) frame[0];
                String body = (String) frame[1];
                switch (msgType) {
                    case RemoteProtocol.MSG_HEARTBEAT -> {
                        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HEARTBEAT, "{}"));
                        out.flush();
                    }
                    case RemoteProtocol.MSG_EXECUTE_REQUEST -> {
                        String response = handleExecuteRequest(body);
                        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_EXECUTE_RESPONSE, response));
                        out.flush();
                    }
                    case RemoteProtocol.MSG_RELAY_REQUEST -> {
                        String response = handleRelayRequest(body);
                        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_RELAY_RESPONSE, response));
                        out.flush();
                    }
                    default -> {
                        log.warn("未知消息类型: {} from {}", msgType, connId);
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.debug("连接断开: {} - {}", connId, e.getMessage());
            }
        } finally {
            activeConnections.remove(connId);
            log.debug("客户端断开: {}", connId);
        }
    }

    private boolean handshake(InputStream in, OutputStream out) throws IOException {
        Object[] frame = ProtocolCodec.readFrameWithType(in);
        int msgType = (int) frame[0];
        if (msgType != RemoteProtocol.MSG_HANDSHAKE) {
            return false;
        }
        String body = (String) frame[1];
        if (authToken != null && !authToken.isEmpty()) {
            try {
                var node = objectMapper.readTree(body);
                String token = node.has("authToken") ? node.get("authToken").asText() : null;
                if (!authToken.equals(token)) {
                    out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_ERROR,
                            "{\"error\":\"认证失败\"}"));
                    out.flush();
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HANDSHAKE_ACK,
                "{\"success\":true}"));
        out.flush();
        return true;
    }

    /**
     * 处理普通执行请求（不携带路由元数据，向后兼容）。
     */
    private String handleExecuteRequest(String body) {
        try {
            ExecuteRequest request = objectMapper.readValue(body, ExecuteRequest.class);
            ExecuteResponse response = executeWithRelay(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("处理执行请求失败", e);
            try {
                ExecuteResponse errorResp = ExecuteResponse.error(null, "服务端内部错误: " + e.getMessage());
                return objectMapper.writeValueAsString(errorResp);
            } catch (Exception e2) {
                return "{\"success\":false,\"error\":\"序列化错误\"}";
            }
        }
    }

    /**
     * 处理中继执行请求（携带路由元数据，树形转发）。
     */
    private String handleRelayRequest(String body) {
        try {
            ExecuteRequest request = objectMapper.readValue(body, ExecuteRequest.class);
            ExecuteResponse response = executeWithRelay(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("处理中继请求失败", e);
            try {
                ExecuteResponse errorResp = ExecuteResponse.error(null, "中继内部错误: " + e.getMessage());
                return objectMapper.writeValueAsString(errorResp);
            } catch (Exception e2) {
                return "{\"success\":false,\"error\":\"序列化错误\"}";
            }
        }
    }

    /**
     * 执行请求（带中继转发）。
     * <p>
     * 执行策略：
     * <ol>
     *   <li>尝试本地执行（插件已加载）</li>
     *   <li>本地无此插件且 relayEnabled=true 时，转发给子节点</li>
     *   <li>防环检查：检查 visitedNodes 是否包含本节点</li>
     * </ol>
     *
     * @param request 执行请求（可能携带路由元数据）
     * @return 执行响应
     */
    private ExecuteResponse executeWithRelay(ExecuteRequest request) {
        // 防环检查：如果本节点已在已访问列表中，拒绝执行
        if (request.getVisitedNodes() != null && request.getVisitedNodes().contains(nodeId)) {
            log.warn("[relay] 检测到环路，拒绝请求: requestId={}, nodeId={}", request.getRequestId(), nodeId);
            return ExecuteResponse.error(request.getRequestId(), "检测到环路: 节点 " + nodeId + " 已被访问");
        }

        // 跳数检查
        if (request.getCurrentHop() > request.getMaxHops()) {
            log.warn("[relay] 超过最大跳数: hop={}, maxHops={}", request.getCurrentHop(), request.getMaxHops());
            return ExecuteResponse.error(request.getRequestId(), "超过最大跳数: " + request.getCurrentHop());
        }

        // 1. 尝试本地执行
        ExecuteResponse localResult = tryLocalExecute(request);
        if (localResult != null) {
            return localResult;
        }

        // 2. 本地无此插件，尝试中继转发
        if (relayEnabled && !childNodes.isEmpty()) {
            return relayToChildren(request);
        }

        // 3. 无中继能力，返回错误
        return ExecuteResponse.error(request.getRequestId(),
                "Bean 未找到且无中继能力: pluginId=" + request.getPluginId() +
                ", beanName=" + request.getBeanName());
    }

    /**
     * 尝试本地执行。返回 null 表示本地无此插件（可继续转发）。
     */
    private ExecuteResponse tryLocalExecute(ExecuteRequest request) {
        try {
            Object bean = managerRef.getServiceFromPlugin(request.getPluginId(), request.getBeanName());
            if (bean == null) {
                return null; // 本地无此插件，可继续转发
            }
            Method targetMethod = findMethod(bean.getClass(), request.getMethodName(), request.getArgTypes());
            if (targetMethod == null) {
                return ExecuteResponse.error(request.getRequestId(),
                        "方法未找到: " + request.getMethodName());
            }
            Object[] args = resolveArguments(request.getArgs(), request.getArgTypes(), targetMethod);
            Object result = targetMethod.invoke(bean, args);
            String resultJson = result != null ? objectMapper.writeValueAsString(result) : null;
            String resultType = result != null ? result.getClass().getName() : null;
            return ExecuteResponse.ok(request.getRequestId(), resultJson, resultType);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.debug("[relay] 本地执行失败，尝试转发: pluginId={}, method={}, error={}",
                    request.getPluginId(), request.getMethodName(), cause.getMessage());
            return null; // 本地执行失败，尝试转发
        }
    }

    /**
     * 中继转发到子节点（轮询负载均衡）。
     * <p>
     * 标记本节点已访问，增加跳数，然后轮询选择子节点转发。
     *
     * @param request 原始请求
     * @return 子节点的执行响应
     */
    private synchronized ExecuteResponse relayToChildren(ExecuteRequest request) {
        List<ChildNodeInfo> online = getOnlineChildNodes();
        if (online.isEmpty()) {
            online = getChildNodes(); // 回退到所有子节点
        }
        if (online.isEmpty()) {
            return ExecuteResponse.error(request.getRequestId(), "无可用子节点");
        }

        // 标记本节点已访问，创建转发请求副本
        ExecuteRequest relayRequest = request.markVisited(nodeId);
        log.debug("[relay] 转发请求: requestId={}, from={}, hop={}/{}",
                request.getRequestId(), nodeId, relayRequest.getCurrentHop(), relayRequest.getMaxHops());

        // 轮询选择子节点
        int idx = roundRobinIndex % online.size();
        roundRobinIndex++;

        // 尝试每个子节点（最多尝试 3 个）
        int maxAttempts = Math.min(3, online.size());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ChildNodeInfo target = online.get((idx + attempt) % online.size());
            try {
                ExecuteResponse response = forwardToChild(target, relayRequest);
                if (response.isSuccess()) {
                    target.online = true;
                    return response;
                }
                // 子节点返回错误（不是连接错误），直接返回
                if (response.getError() != null &&
                    !response.getError().contains("连接失败") &&
                    !response.getError().contains("重连")) {
                    return response;
                }
                // 连接错误，标记离线，尝试下一个
                target.online = false;
                log.warn("[relay] 子节点不可用: id={}, 尝试下一个", target.id);
            } catch (Exception e) {
                target.online = false;
                log.warn("[relay] 转发到子节点失败: id={}, error={}", target.id, e.getMessage());
            }
        }

        return ExecuteResponse.error(request.getRequestId(), "所有子节点均不可用");
    }

    /**
     * 通过 TCP 转发请求到子节点。
     */
    private ExecuteResponse forwardToChild(ChildNodeInfo child, ExecuteRequest request) throws IOException {
        String body = objectMapper.writeValueAsString(request);
        try (Socket socket = new Socket(child.host, child.port)) {
            socket.setSoTimeout(30000);
            socket.setKeepAlive(true);
            OutputStream out = socket.getOutputStream();

            // 握手
            String handshakeBody = child.authToken != null && !child.authToken.isEmpty()
                    ? "{\"authToken\":\"" + child.authToken + "\"}"
                    : "{}";
            out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HANDSHAKE, handshakeBody));
            out.flush();

            InputStream in = socket.getInputStream();
            Object[] hsFrame = ProtocolCodec.readFrameWithType(in);
            int hsType = (int) hsFrame[0];
            if (hsType == RemoteProtocol.MSG_ERROR) {
                throw new IOException("子节点握手失败: 认证失败");
            }
            if (hsType != RemoteProtocol.MSG_HANDSHAKE_ACK) {
                throw new IOException("子节点握手失败: 未知响应 " + hsType);
            }

            // 发送中继请求
            out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_RELAY_REQUEST, body));
            out.flush();

            // 读取响应
            Object[] frame = ProtocolCodec.readFrameWithType(in);
            int msgType = (int) frame[0];
            String respBody = (String) frame[1];

            if (msgType == RemoteProtocol.MSG_RELAY_RESPONSE || msgType == RemoteProtocol.MSG_EXECUTE_RESPONSE) {
                return objectMapper.readValue(respBody, ExecuteResponse.class);
            } else if (msgType == RemoteProtocol.MSG_ERROR) {
                var node = objectMapper.readTree(respBody);
                String error = node.has("error") ? node.get("error").asText() : "未知错误";
                return ExecuteResponse.error(request.getRequestId(), error);
            }
            return ExecuteResponse.error(request.getRequestId(), "未知响应类型: " + msgType);
        }
    }

    private Method findMethod(Class<?> beanClass, String methodName, List<String> argTypes) {
        for (Method method : beanClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (argTypes == null || argTypes.isEmpty()) {
                return method;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != argTypes.size()) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].getName().equals(argTypes.get(i))
                        && !paramTypes[i].getSimpleName().equals(argTypes.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return method;
            }
        }
        // 回退：按方法名匹配（忽略参数类型）
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private Object[] resolveArguments(List<String> args, List<String> argTypes, Method method) {
        if (args == null || args.isEmpty()) {
            return new Object[0];
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] result = new Object[Math.min(args.size(), paramTypes.length)];
        for (int i = 0; i < result.length; i++) {
            String argJson = args.get(i);
            Class<?> targetType = paramTypes[i];
            try {
                result[i] = objectMapper.readValue(argJson, targetType);
            } catch (Exception e) {
                // 回退：直接使用字符串
                result[i] = argJson;
            }
        }
        return result;
    }
}
