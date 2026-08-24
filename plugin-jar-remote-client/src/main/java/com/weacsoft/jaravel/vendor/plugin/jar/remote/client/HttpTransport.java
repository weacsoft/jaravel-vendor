package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
import com.weacsoft.jaravel.vendor.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 传输实现（JSON-RPC 风格）。
 * <p>
 * 使用 HTTP POST 发送 JSON 请求体到服务端的 RPC 端点。
 * 复用现有 Web 服务器端口，无需额外端口。
 * <p>
 * <h3>端点路径</h3>
 * 端点路径由用户自行配置（服务端通过 {@code HttpRpcHandler.processRequest} 静态方法
 * 在自己的控制器中处理请求，客户端通过本类的构造函数指定端点路径）。
 * <p>
 * 默认端点路径为 {@code /__remote_rpc__}，可通过构造函数或配置修改。
 * <p>
 * <h3>请求格式</h3>
 * <pre>
 * POST {endpoint}
 * Content-Type: application/json;charset=UTF-8
 * X-Auth-Token: {可选认证令牌}
 *
 * Body: ExecuteRequest 的 JSON 序列化
 * </pre>
 * <p>
 * <h3>响应格式</h3>
 * <pre>
 * HTTP 200
 * Content-Type: application/json;charset=UTF-8
 *
 * Body: ExecuteResponse 的 JSON 序列化
 * </pre>
 * <p>
 * <h3>适用场景</h3>
 * 无法开放额外 TCP 端口时使用，性能略低于 TCP 但部署更灵活。
 */
public class HttpTransport implements RemoteTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpTransport.class);

    /** 默认 RPC 端点路径 */
    public static final String DEFAULT_ENDPOINT = "/__remote_rpc__";

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /** RPC 端点路径 */
    private final String endpoint;

    /**
     * 构造 HTTP 传输，使用默认端点路径 {@code /__remote_rpc__}。
     */
    public HttpTransport() {
        this(DEFAULT_ENDPOINT);
    }

    /**
     * 构造 HTTP 传输，指定端点路径。
     * <p>
     * 端点路径必须与服务端控制器中注册的路径一致。
     *
     * @param endpoint RPC 端点路径（如 {@code /my-rpc}）
     */
    public HttpTransport(String endpoint) {
        this.endpoint = endpoint != null && !endpoint.isEmpty() ? endpoint : DEFAULT_ENDPOINT;
    }

    /**
     * 返回当前端点路径。
     */
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public ExecuteResponse send(String host, int port, String authToken, ExecuteRequest request) {
        HttpURLConnection conn = null;
        try {
            String urlStr = String.format("http://%s:%d%s", host, port, endpoint);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("X-Auth-Token", authToken);
            }
            conn.setConnectTimeout((int) DEFAULT_TIMEOUT_MS);
            conn.setReadTimeout((int) DEFAULT_TIMEOUT_MS);
            conn.setDoOutput(true);

            String body = Json.stringify(request);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return ExecuteResponse.error(request.getRequestId(), "HTTP " + code + ": 无响应体");
            }
            byte[] respBytes = stream.readAllBytes();
            String respBody = new String(respBytes, StandardCharsets.UTF_8);
            if (code >= 400) {
                return ExecuteResponse.error(request.getRequestId(), "HTTP " + code + ": " + respBody);
            }
            return Json.parse(respBody, ExecuteResponse.class);
        } catch (IOException e) {
            log.error("HTTP 请求失败: {}:{}{}", host, port, endpoint, e);
            return ExecuteResponse.error(request.getRequestId(), "HTTP 请求失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public boolean connect(String host, int port, String authToken) {
        // HTTP 模式无需建立长连接，始终返回 true
        return true;
    }

    @Override
    public void disconnect(String host, int port) {
        // HTTP 模式无需断开连接
    }

    @Override
    public boolean isConnected(String host, int port) {
        // HTTP 模式始终可用
        return true;
    }

    @Override
    public String getType() {
        return "HTTP";
    }
}
