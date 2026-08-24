package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ProtocolCodec;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteProtocol;
import com.weacsoft.jaravel.vendor.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程插件执行客户端。
 * <p>
 * 连接到远程子服务器，发送方法执行请求并接收响应。
 * <p>
 * <h3>连接管理</h3>
 * 每个子服务器维护一个 TCP 长连接，首次执行时建立，复用到后续请求。
 * 连接断开时自动重连。
 * <p>
 * <h3>同步语义</h3>
 * 当前实现为同步阻塞：发送请求 → 等待响应 → 返回。
 * 每个请求使用唯一 requestId，与响应匹配。
 * <p>
 * <h3>使用方式</h3>
 * <pre>
 * // 启动远程模式：连接到子服务器
 * client.startRemoteMode("192.168.1.100", 9700, "auth-token");
 *
 * // 远程执行插件方法
 * Object result = client.executeRemotely("192.168.1.100", 9700,
 *     "blog", "blogController", "list", args, argTypes);
 *
 * // 停止远程模式
 * client.stopRemoteMode("192.168.1.100", 9700);
 * </pre>
 */
public class RemotePluginClient {

    private static final Logger log = LoggerFactory.getLogger(RemotePluginClient.class);

    /** 连接缓存：host:port -> Socket */
    private final ConcurrentHashMap<String, Socket> connections = new ConcurrentHashMap<>();

    /** 默认超时（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 启动远程模式：连接到指定子服务器。
     * <p>
     * 建立 TCP 连接并完成握手。连接成功后可调用 {@link #executeRemotely}。
     *
     * @param host      子服务器主机地址
     * @param port      子服务器 TCP 端口
     * @param authToken 认证令牌（null 表示不认证）
     * @return 连接成功返回 true
     */
    public boolean startRemoteMode(String host, int port, String authToken) {
        String key = connKey(host, port);
        try {
            Socket socket = createAndHandshake(host, port, authToken);
            Socket old = connections.put(key, socket);
            if (old != null) {
                closeQuietly(old);
            }
            log.info("远程模式已启动: {} -> {}:{}", key, host, port);
            return true;
        } catch (Exception e) {
            log.error("连接子服务器失败: {}:{}", host, port, e);
            return false;
        }
    }

    /**
     * 启动远程模式（无认证）。
     */
    public boolean startRemoteMode(String host, int port) {
        return startRemoteMode(host, port, null);
    }

    /**
     * 停止远程模式：断开与指定子服务器的连接。
     *
     * @param host 子服务器主机地址
     * @param port 子服务器 TCP 端口
     */
    public void stopRemoteMode(String host, int port) {
        String key = connKey(host, port);
        Socket socket = connections.remove(key);
        if (socket != null) {
            closeQuietly(socket);
            log.info("远程模式已停止: {}", key);
        }
    }

    /**
     * 停止所有远程连接。
     */
    public void stopAll() {
        for (Map.Entry<String, Socket> entry : connections.entrySet()) {
            closeQuietly(entry.getValue());
            log.info("远程模式已停止: {}", entry.getKey());
        }
        connections.clear();
    }

    /**
     * 检查与指定子服务器的连接是否活跃。
     */
    public boolean isConnected(String host, int port) {
        String key = connKey(host, port);
        Socket socket = connections.get(key);
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    /**
     * 远程执行插件方法。
     * <p>
     * 向指定子服务器发送执行请求，同步等待响应。
     * 若连接未建立，会自动尝试连接（无认证）。
     *
     * @param host       子服务器主机地址
     * @param port       子服务器 TCP 端口
     * @param pluginId   插件 ID
     * @param beanName   Bean 名称
     * @param methodName 方法名
     * @param args       参数值列表（每个参数 JSON 序列化为字符串）
     * @param argTypes   参数类型列表（全限定类名）
     * @return 执行响应
     */
    public ExecuteResponse executeRemotely(String host, int port,
                                           String pluginId, String beanName, String methodName,
                                           List<String> args, List<String> argTypes) {
        String key = connKey(host, port);
        Socket socket = connections.get(key);
        // 自动连接（无认证）
        if (socket == null || socket.isClosed()) {
            try {
                socket = createAndHandshake(host, port, null);
                connections.put(key, socket);
            } catch (Exception e) {
                return ExecuteResponse.error(null, "连接子服务器失败: " + e.getMessage());
            }
        }
        String requestId = UUID.randomUUID().toString();
        ExecuteRequest request = new ExecuteRequest(requestId, pluginId, beanName, methodName, args, argTypes);
        try {
            return sendRequest(socket, request);
        } catch (IOException e) {
            // 连接可能断开，尝试重连一次
            log.warn("请求失败，尝试重连: {} - {}", key, e.getMessage());
            connections.remove(key);
            closeQuietly(socket);
            try {
                socket = createAndHandshake(host, port, null);
                connections.put(key, socket);
                return sendRequest(socket, request);
            } catch (Exception e2) {
                return ExecuteResponse.error(requestId, "重连后执行失败: " + e2.getMessage());
            }
        }
    }

    /**
     * 远程执行插件方法（无参数）。
     */
    public ExecuteResponse executeRemotely(String host, int port,
                                           String pluginId, String beanName, String methodName) {
        return executeRemotely(host, port, pluginId, beanName, methodName, null, null);
    }

    private ExecuteResponse sendRequest(Socket socket, ExecuteRequest request) throws IOException {
        String body = Json.stringify(request);
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_EXECUTE_REQUEST, body));
        out.flush();
        // 等待响应
        InputStream in = socket.getInputStream();
        socket.setSoTimeout((int) DEFAULT_TIMEOUT_MS);
        Object[] frame = ProtocolCodec.readFrameWithType(in);
        int msgType = (int) frame[0];
        String respBody = (String) frame[1];
        if (msgType == RemoteProtocol.MSG_EXECUTE_RESPONSE) {
            return Json.parse(respBody, ExecuteResponse.class);
        } else if (msgType == RemoteProtocol.MSG_ERROR) {
            Map<String, Object> node = Json.parseToMap(respBody);
            String error = node.containsKey("error") ? String.valueOf(node.get("error")) : "未知错误";
            return ExecuteResponse.error(request.getRequestId(), error);
        }
        return ExecuteResponse.error(request.getRequestId(), "未知响应类型: " + msgType);
    }

    private Socket createAndHandshake(String host, int port, String authToken) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout((int) DEFAULT_TIMEOUT_MS);
        socket.setKeepAlive(true);
        // 握手
        String handshakeBody = authToken != null
                ? "{\"authToken\":\"" + authToken + "\"}"
                : "{}";
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HANDSHAKE, handshakeBody));
        out.flush();
        // 等待握手确认
        InputStream in = socket.getInputStream();
        Object[] frame = ProtocolCodec.readFrameWithType(in);
        int msgType = (int) frame[0];
        if (msgType == RemoteProtocol.MSG_ERROR) {
            closeQuietly(socket);
            throw new IOException("握手失败: 认证失败");
        }
        if (msgType != RemoteProtocol.MSG_HANDSHAKE_ACK) {
            closeQuietly(socket);
            throw new IOException("握手失败: 未知响应类型 " + msgType);
        }
        // 恢复无超时（长连接）
        socket.setSoTimeout(0);
        return socket;
    }

    private String connKey(String host, int port) {
        return host + ":" + port;
    }

    private void closeQuietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
