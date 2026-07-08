package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ProtocolCodec;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteProtocol;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP 传输实现。
 * <p>
 * 使用 TCP 长连接 + 二进制帧协议传输执行请求。
 * 每个目标服务器维护一个长连接，复用到后续请求。
 * 连接断开时自动重连。
 * <p>
 * 适合高性能场景，但需要额外端口。
 */
public class TcpTransport implements RemoteTransport {

    private static final Logger log = LoggerFactory.getLogger(TcpTransport.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Socket> connections = new ConcurrentHashMap<>();
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    @Override
    public ExecuteResponse send(String host, int port, String authToken, ExecuteRequest request) {
        String key = connKey(host, port);
        Socket socket = connections.get(key);
        if (socket == null || socket.isClosed()) {
            try {
                socket = createAndHandshake(host, port, authToken);
                connections.put(key, socket);
            } catch (Exception e) {
                return ExecuteResponse.error(request.getRequestId(), "连接失败: " + e.getMessage());
            }
        }
        try {
            return sendRequest(socket, request);
        } catch (IOException e) {
            log.warn("请求失败，尝试重连: {} - {}", key, e.getMessage());
            connections.remove(key);
            closeQuietly(socket);
            try {
                socket = createAndHandshake(host, port, authToken);
                connections.put(key, socket);
                return sendRequest(socket, request);
            } catch (Exception e2) {
                return ExecuteResponse.error(request.getRequestId(), "重连后执行失败: " + e2.getMessage());
            }
        }
    }

    @Override
    public boolean connect(String host, int port, String authToken) {
        try {
            Socket socket = createAndHandshake(host, port, authToken);
            Socket old = connections.put(connKey(host, port), socket);
            if (old != null) closeQuietly(old);
            return true;
        } catch (Exception e) {
            log.error("连接失败: {}:{}", host, port, e);
            return false;
        }
    }

    @Override
    public void disconnect(String host, int port) {
        Socket socket = connections.remove(connKey(host, port));
        if (socket != null) closeQuietly(socket);
    }

    @Override
    public boolean isConnected(String host, int port) {
        Socket socket = connections.get(connKey(host, port));
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    @Override
    public String getType() {
        return "TCP";
    }

    /** 断开所有连接 */
    public void disconnectAll() {
        for (Map.Entry<String, Socket> entry : connections.entrySet()) {
            closeQuietly(entry.getValue());
        }
        connections.clear();
    }

    private ExecuteResponse sendRequest(Socket socket, ExecuteRequest request) throws IOException {
        String body = objectMapper.writeValueAsString(request);
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_EXECUTE_REQUEST, body));
        out.flush();
        InputStream in = socket.getInputStream();
        socket.setSoTimeout((int) DEFAULT_TIMEOUT_MS);
        Object[] frame = ProtocolCodec.readFrameWithType(in);
        int msgType = (int) frame[0];
        String respBody = (String) frame[1];
        if (msgType == RemoteProtocol.MSG_EXECUTE_RESPONSE) {
            return objectMapper.readValue(respBody, ExecuteResponse.class);
        } else if (msgType == RemoteProtocol.MSG_ERROR) {
            var node = objectMapper.readTree(respBody);
            String error = node.has("error") ? node.get("error").asText() : "未知错误";
            return ExecuteResponse.error(request.getRequestId(), error);
        }
        return ExecuteResponse.error(request.getRequestId(), "未知响应类型: " + msgType);
    }

    private Socket createAndHandshake(String host, int port, String authToken) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout((int) DEFAULT_TIMEOUT_MS);
        socket.setKeepAlive(true);
        String handshakeBody = authToken != null && !authToken.isEmpty()
                ? "{\"authToken\":\"" + authToken + "\"}"
                : "{}";
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HANDSHAKE, handshakeBody));
        out.flush();
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
