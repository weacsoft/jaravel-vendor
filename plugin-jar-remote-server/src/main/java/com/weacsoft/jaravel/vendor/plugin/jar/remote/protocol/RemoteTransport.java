package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

/**
 * 远程传输层抽象。
 * <p>
 * 定义统一的传输接口，支持 TCP 和 HTTP 两种实现。
 * 客户端通过此接口发送执行请求，不关心底层传输细节。
 * <p>
 * <h3>TCP 模式</h3>
 * 使用独立 TCP 端口，二进制帧协议，适合高性能场景。
 * 需要额外端口。
 * <p>
 * <h3>HTTP 模式</h3>
 * 使用 JSON-RPC over HTTP，复用现有 Web 服务器端口，适合无法开放额外端口的场景。
 * 服务端通过编程式注册的内部 HTTP 端点接收请求。
 */
public interface RemoteTransport {

    /**
     * 发送执行请求并同步等待响应。
     *
     * @param host       目标主机
     * @param port       目标端口
     * @param authToken   认证令牌（null 表示不认证）
     * @param request    执行请求
     * @return 执行响应
     */
    ExecuteResponse send(String host, int port, String authToken, ExecuteRequest request);

    /**
     * 建立连接（TCP 模式建立长连接，HTTP 模式为空操作）。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param authToken  认证令牌
     * @return 连接成功返回 true
     */
    boolean connect(String host, int port, String authToken);

    /**
     * 断开连接。
     *
     * @param host 目标主机
     * @param port 目标端口
     */
    void disconnect(String host, int port);

    /**
     * 检查连接是否活跃。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return 活跃返回 true
     */
    boolean isConnected(String host, int port);

    /**
     * 返回传输类型。
     *
     * @return 传输类型名称（"TCP" 或 "HTTP"）
     */
    String getType();
}
