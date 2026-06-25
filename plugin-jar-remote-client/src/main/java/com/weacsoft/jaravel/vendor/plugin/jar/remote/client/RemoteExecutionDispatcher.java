package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * 远程执行调度器（CS 中心化架构入口）。
 * <p>
 * 整合 {@link SubServerRegistry}（子服务器注册表）、{@link RemoteTransport}（传输层）
 * 和 {@link RequestCoordinator}（请求协调器），提供统一的高层 API。
 * <p>
 * <h3>两种执行模式</h3>
 * <ul>
 *   <li><b>指定子服务器</b>：{@link #executeOn} — 直接发送到指定子服务器执行</li>
 *   <li><b>协调器分配</b>：{@link #execute} — 不指定子服务器，由协调器决定本地执行或转发到可用子服务器</li>
 * </ul>
 * <p>
 * <h3>两种传输模式</h3>
 * <ul>
 *   <li><b>TCP</b>：默认，高性能，需额外端口</li>
 *   <li><b>HTTP</b>：JSON-RPC，复用 Web 端口，适合无法开额外端口的场景</li>
 * </ul>
 * 通过 {@link #setTransport} 切换传输模式。
 * <p>
 * <h3>安全设计</h3>
 * 所有操作均为 Java 方法调用，不暴露 HTTP 接口。
 * 子服务器注册/注销/查询均通过方法调用完成。
 */
public class RemoteExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecutionDispatcher.class);

    private final SubServerRegistry registry;
    private final RemoteTransport transport;
    private final RequestCoordinator coordinator;

    /** 轮询计数器（用于负载均衡） */
    private volatile int roundRobinIndex = 0;

    /**
     * 构造远程执行调度器（默认 TCP 传输，无本地执行能力）。
     */
    public RemoteExecutionDispatcher() {
        this(new TcpTransport(), null);
    }

    /**
     * 构造远程执行调度器，指定传输层和本地插件管理器引用。
     *
     * @param transport       传输层（TCP 或 HTTP）
     * @param localManagerRef 本地插件管理器引用（null 表示不本地执行，仅转发）
     */
    public RemoteExecutionDispatcher(RemoteTransport transport,
                                      Application.HotPluginManagerRef localManagerRef) {
        this.registry = new SubServerRegistry();
        this.transport = transport;
        this.coordinator = new RequestCoordinator(registry, transport, localManagerRef);
    }

    // ==================== 传输模式切换 ====================

    /**
     * 切换传输模式。
     * <p>
     * 注意：切换会断开所有现有 TCP 连接。
     *
     * @param newTransport 新的传输层
     */
    public void setTransport(RemoteTransport newTransport) {
        if (transport instanceof TcpTransport oldTcp) {
            oldTcp.disconnectAll();
        }
        log.info("传输模式切换: {} -> {}", transport.getType(), newTransport.getType());
    }

    /**
     * 返回当前传输类型。
     */
    public String getTransportType() {
        return transport.getType();
    }

    // ==================== 子服务器管理 ====================

    /**
     * 注册子服务器。
     *
     * @param id        子服务器唯一标识
     * @param host      主机地址
     * @param port      端口（TCP 模式为 TCP 端口，HTTP 模式为 Web 端口）
     * @param authToken 认证令牌（可选）
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerSubServer(String id, String host, int port, String authToken) {
        return registry.registerSubServer(id, host, port, authToken);
    }

    /**
     * 注册子服务器（无认证）。
     */
    public SubServerInfo registerSubServer(String id, String host, int port) {
        return registry.registerSubServer(id, host, port, null);
    }

    /**
     * 注销子服务器，同时断开连接。
     */
    public boolean unregisterSubServer(String id) {
        SubServerInfo info = registry.getSubServer(id);
        if (info != null) {
            transport.disconnect(info.getHost(), info.getPort());
        }
        return registry.unregisterSubServer(id);
    }

    /**
     * 获取所有已注册的子服务器。
     */
    public List<SubServerInfo> getSubServers() {
        return registry.getSubServers();
    }

    /**
     * 获取所有在线的子服务器。
     */
    public List<SubServerInfo> getOnlineSubServers() {
        return registry.getOnlineSubServers();
    }

    // ==================== 远程模式控制 ====================

    /**
     * 启动远程模式：连接到指定子服务器。
     *
     * @param subServerId 子服务器唯一标识
     * @return 连接成功返回 true
     */
    public boolean startRemoteMode(String subServerId) {
        SubServerInfo info = registry.getSubServer(subServerId);
        if (info == null) {
            log.warn("子服务器未注册: {}", subServerId);
            return false;
        }
        boolean ok = transport.connect(info.getHost(), info.getPort(), info.getAuthToken());
        if (ok) {
            registry.updateOnlineStatus(subServerId, true);
        }
        return ok;
    }

    /**
     * 启动远程模式：直接传入服务器地址和端口（运行时动态连接）。
     */
    public boolean startRemoteMode(String host, int port, String authToken) {
        return transport.connect(host, port, authToken);
    }

    /**
     * 启动远程模式（无认证）。
     */
    public boolean startRemoteMode(String host, int port) {
        return transport.connect(host, port, null);
    }

    /**
     * 停止远程模式：断开与指定子服务器的连接。
     */
    public void stopRemoteMode(String subServerId) {
        SubServerInfo info = registry.getSubServer(subServerId);
        if (info != null) {
            transport.disconnect(info.getHost(), info.getPort());
            registry.updateOnlineStatus(subServerId, false);
        }
    }

    /**
     * 停止所有远程连接。
     */
    public void stopAllRemoteModes() {
        for (SubServerInfo info : registry.getSubServers()) {
            transport.disconnect(info.getHost(), info.getPort());
            registry.updateOnlineStatus(info.getId(), false);
        }
    }

    // ==================== 远程执行 ====================

    /**
     * 在指定子服务器上远程执行插件方法。
     *
     * @param subServerId 子服务器唯一标识
     * @param pluginId    插件 ID
     * @param beanName    Bean 名称
     * @param methodName  方法名
     * @param args        参数值列表（每个参数 JSON 序列化为字符串）
     * @param argTypes    参数类型列表（全限定类名）
     * @return 执行响应
     */
    public ExecuteResponse executeOn(String subServerId, String pluginId, String beanName,
                                     String methodName, List<String> args, List<String> argTypes) {
        SubServerInfo info = registry.getSubServer(subServerId);
        if (info == null) {
            return ExecuteResponse.error(null, "子服务器未注册: " + subServerId);
        }
        String requestId = UUID.randomUUID().toString();
        ExecuteRequest request = new ExecuteRequest(requestId, pluginId, beanName, methodName, args, argTypes);
        ExecuteResponse response = transport.send(info.getHost(), info.getPort(), info.getAuthToken(), request);
        if (response.isSuccess()) {
            registry.updateOnlineStatus(subServerId, true);
        } else {
            log.warn("远程执行失败: subServer={}, pluginId={}, method={}",
                    subServerId, pluginId, methodName);
        }
        return response;
    }

    /**
     * 在指定子服务器上远程执行插件方法（无参数）。
     */
    public ExecuteResponse executeOn(String subServerId, String pluginId,
                                     String beanName, String methodName) {
        return executeOn(subServerId, pluginId, beanName, methodName, null, null);
    }

    /**
     * 协调器分配执行（不指定子服务器）。
     * <p>
     * 由 {@link RequestCoordinator} 决定执行位置：
     * <ol>
     *   <li>优先本地执行（若插件已加载且配置了 localManagerRef）</li>
     *   <li>本地无此插件时，轮询选择一个在线子服务器转发</li>
     * </ol>
     *
     * @param pluginId   插件 ID
     * @param beanName   Bean 名称
     * @param methodName 方法名
     * @param args       参数值列表（JSON 序列化）
     * @param argTypes   参数类型列表（全限定类名）
     * @return 执行响应
     */
    public ExecuteResponse execute(String pluginId, String beanName,
                                   String methodName, List<String> args, List<String> argTypes) {
        String requestId = UUID.randomUUID().toString();
        ExecuteRequest request = new ExecuteRequest(requestId, pluginId, beanName, methodName, args, argTypes);
        return coordinator.dispatch(request);
    }

    /**
     * 协调器分配执行（无参数）。
     */
    public ExecuteResponse execute(String pluginId, String beanName, String methodName) {
        return execute(pluginId, beanName, methodName, null, null);
    }

    /**
     * 轮询选择一个在线子服务器执行（简单负载均衡）。
     * <p>
     * 与 {@link #execute} 的区别：此方法不尝试本地执行，直接转发到子服务器。
     *
     * @param pluginId   插件 ID
     * @param beanName   Bean 名称
     * @param methodName 方法名
     * @param args       参数值列表
     * @param argTypes   参数类型列表
     * @return 执行响应
     */
    public synchronized ExecuteResponse executeRound(String pluginId, String beanName,
                                                     String methodName, List<String> args,
                                                     List<String> argTypes) {
        List<SubServerInfo> online = registry.getOnlineSubServers();
        if (online.isEmpty()) {
            return ExecuteResponse.error(null, "无在线子服务器");
        }
        int idx = roundRobinIndex % online.size();
        roundRobinIndex++;
        SubServerInfo target = online.get(idx);
        return executeOn(target.getId(), pluginId, beanName, methodName, args, argTypes);
    }

    /**
     * 轮询执行（无参数）。
     */
    public ExecuteResponse executeRound(String pluginId, String beanName, String methodName) {
        return executeRound(pluginId, beanName, methodName, null, null);
    }

    // ==================== 自动代理 ====================

    /**
     * 创建远程服务代理（自动包装/解包）。
     * <p>
     * 调用方像调用本地方法一样使用返回的代理对象，
     * 代理自动将方法名和参数序列化、远程执行、解包返回值。
     *
     * @param <T>            服务接口类型
     * @param interfaceClass 服务接口类
     * @param pluginId       插件 ID
     * @param beanName       Bean 名称
     * @param subServerId    子服务器 ID（null 表示由协调器分配）
     * @return 远程服务代理
     */
    public <T> T createProxy(Class<T> interfaceClass, String pluginId, String beanName,
                              String subServerId) {
        return RemoteProxy.create(interfaceClass, pluginId, beanName, this, subServerId);
    }

    /**
     * 创建远程服务代理（协调器分配，不指定子服务器）。
     */
    public <T> T createProxy(Class<T> interfaceClass, String pluginId, String beanName) {
        return RemoteProxy.create(interfaceClass, pluginId, beanName, this, null);
    }

    // ==================== 内部组件访问 ====================

    /**
     * 返回子服务器注册表。
     */
    public SubServerRegistry getRegistry() {
        return registry;
    }

    /**
     * 返回传输层。
     */
    public RemoteTransport getTransport() {
        return transport;
    }

    /**
     * 返回请求协调器。
     */
    public RequestCoordinator getCoordinator() {
        return coordinator;
    }
}
