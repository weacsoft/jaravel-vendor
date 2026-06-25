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
        log.info("远程插件服务端已启动: port={}, auth={}", port, authToken != null);
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

    private String handleExecuteRequest(String body) {
        try {
            ExecuteRequest request = objectMapper.readValue(body, ExecuteRequest.class);
            ExecuteResponse response = executeLocally(request);
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
     * 在本地执行插件方法。
     *
     * @param request 执行请求
     * @return 执行响应
     */
    private ExecuteResponse executeLocally(ExecuteRequest request) {
        try {
            Object bean = managerRef.getServiceFromPlugin(request.getPluginId(), request.getBeanName());
            if (bean == null) {
                return ExecuteResponse.error(request.getRequestId(),
                        "Bean 未找到: pluginId=" + request.getPluginId() + ", beanName=" + request.getBeanName());
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
            log.error("远程执行失败: pluginId={}, beanName={}, method={}",
                    request.getPluginId(), request.getBeanName(), request.getMethodName(), cause);
            return ExecuteResponse.error(request.getRequestId(), cause.getMessage());
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
