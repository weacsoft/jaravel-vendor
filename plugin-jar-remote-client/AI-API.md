# plugin-jar-remote-client AI-API Reference

> Module: `plugin-jar-remote-client` | Package: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client` | Version: 0.1.0

## Overview

plugin-jar-remote-client 模块是 JAR 插件远程执行体系的客户端，提供将插件方法调用转发到远程子运算服务器执行的能力。该模块基于 CS（Client-Server）中心化架构：客户端通过 `RemoteExecutionDispatcher` 统一入口发起执行请求，由 `RequestCoordinator` 协调器决定执行位置——优先在本地已加载的插件上执行（若配置了本地插件管理器引用），本地无此插件时则按轮询策略转发到在线子服务器。

模块提供两种传输模式：`TcpTransport`（默认，TCP 长连接 + 二进制帧协议，高性能但需额外端口）与 `HttpTransport`（JSON-RPC 风格，复用 Web 服务器端口，部署灵活）。可通过配置 `jaravel.plugin-jar.remote.client.transport` 在 `tcp` 与 `http` 间切换。`RemoteProxy` 基于 Java 动态代理实现透明远程调用，调用方像调用本地方法一样使用远程服务，代理自动完成参数序列化、远程执行与返回值解包。`SubServerRegistry` 负责子服务器的注册、注销、查询与在线状态维护，`SubServerInfo` 封装子服务器连接信息与状态。

### 模块依赖关系

本模块所有类均位于 `com.weacsoft.jaravel.vendor.plugin.jar.remote.client` 包下，但核心传输契约与协议定义依赖 **plugin-jar-remote-server** 模块的 `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol` 包：

- `RemoteTransport`（传输层接口）：`HttpTransport` 与 `TcpTransport` 均实现此接口，定义 `send`/`connect`/`disconnect`/`isConnected`/`getType` 方法契约。
- `ExecuteRequest` / `ExecuteResponse`：远程执行请求与响应的数据模型，贯穿客户端与服务端。
- `ProtocolCodec`：二进制帧编解码器，`TcpTransport` 与 `RemotePluginClient` 使用其完成帧的编码与读取。
- `RemoteProtocol`：协议常量定义（如 `MSG_EXECUTE_REQUEST`、`MSG_EXECUTE_RESPONSE`、`MSG_HANDSHAKE`、`MSG_HANDSHAKE_ACK`、`MSG_ERROR` 等消息类型）。

此外，本模块依赖 **plugin-jar-core** 模块：

- `Application.HotPluginManagerRef`：本地插件管理器引用接口，`RequestCoordinator` 与 `RemoteExecutionDispatcher` 通过它实现本地优先执行。
- `HotPluginManager`：核心插件管理器，自动装配时作为可选 Bean 注入以启用协调器本地执行能力。

引入本模块后，`RemoteClientAutoConfiguration` 自动装配 `RemoteExecutionDispatcher` Bean，全程不暴露任何 HTTP 接口，所有操作均通过 Java 方法调用完成。

## Classes & Interfaces

### RemoteExecutionDispatcher
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 远程执行调度器，CS 中心化架构入口。整合 `SubServerRegistry`（子服务器注册表）、`RemoteTransport`（传输层）和 `RequestCoordinator`（请求协调器），提供统一的高层 API。支持两种执行模式：指定子服务器执行（`executeOn`）与协调器分配执行（`execute`）；支持两种传输模式（TCP/HTTP），可通过 `setTransport` 切换。所有操作均为 Java 方法调用，不暴露 HTTP 接口。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RemoteExecutionDispatcher` | - | - | 构造调度器（默认 TCP 传输，无本地执行能力） |
| `RemoteExecutionDispatcher` | `RemoteTransport transport, Application.HotPluginManagerRef localManagerRef` | - | 构造调度器，指定传输层与本地插件管理器引用（null 表示仅转发不本地执行） |
| `setTransport` | `RemoteTransport newTransport` | `void` | 切换传输模式（会断开现有 TCP 连接） |
| `getTransportType` | - | `String` | 返回当前传输类型（"TCP" 或 "HTTP"） |
| `registerSubServer` | `String id, String host, int port, String authToken` | `SubServerInfo` | 注册子服务器（含认证令牌） |
| `registerSubServer` | `String id, String host, int port` | `SubServerInfo` | 注册子服务器（无认证） |
| `unregisterSubServer` | `String id` | `boolean` | 注销子服务器并断开连接 |
| `getSubServers` | - | `List<SubServerInfo>` | 获取所有已注册的子服务器 |
| `getOnlineSubServers` | - | `List<SubServerInfo>` | 获取所有在线的子服务器 |
| `startRemoteMode` | `String subServerId` | `boolean` | 启动远程模式，连接到指定子服务器 |
| `startRemoteMode` | `String host, int port, String authToken` | `boolean` | 启动远程模式，直接传入地址端口（含认证） |
| `startRemoteMode` | `String host, int port` | `boolean` | 启动远程模式（无认证） |
| `stopRemoteMode` | `String subServerId` | `void` | 停止远程模式，断开与指定子服务器的连接 |
| `stopAllRemoteModes` | - | `void` | 停止所有远程连接 |
| `executeOn` | `String subServerId, String pluginId, String beanName, String methodName, List<String> args, List<String> argTypes` | `ExecuteResponse` | 在指定子服务器上远程执行插件方法 |
| `executeOn` | `String subServerId, String pluginId, String beanName, String methodName` | `ExecuteResponse` | 在指定子服务器上远程执行插件方法（无参数） |
| `execute` | `String pluginId, String beanName, String methodName, List<String> args, List<String> argTypes` | `ExecuteResponse` | 协调器分配执行（优先本地，无则轮询转发子服务器） |
| `execute` | `String pluginId, String beanName, String methodName` | `ExecuteResponse` | 协调器分配执行（无参数） |
| `executeRound` | `String pluginId, String beanName, String methodName, List<String> args, List<String> argTypes` | `ExecuteResponse` | 轮询选择在线子服务器执行（不尝试本地执行） |
| `executeRound` | `String pluginId, String beanName, String methodName` | `ExecuteResponse` | 轮询执行（无参数） |
| `createProxy` | `Class<T> interfaceClass, String pluginId, String beanName, String subServerId` | `T` | 创建远程服务代理（指定子服务器） |
| `createProxy` | `Class<T> interfaceClass, String pluginId, String beanName` | `T` | 创建远程服务代理（由协调器分配） |
| `getRegistry` | - | `SubServerRegistry` | 返回子服务器注册表 |
| `getTransport` | - | `RemoteTransport` | 返回传输层 |
| `getCoordinator` | - | `RequestCoordinator` | 返回请求协调器 |

#### Usage Example
```java
@Autowired
private RemoteExecutionDispatcher dispatcher;

// 注册子服务器
dispatcher.registerSubServer("sub1", "192.168.1.100", 9700, "auth-token");

// 启动远程模式
dispatcher.startRemoteMode("sub1");

// 指定子服务器执行
ExecuteResponse resp = dispatcher.executeOn("sub1", "blog", "blogController",
        "list", args, argTypes);

// 协调器分配执行（优先本地）
ExecuteResponse resp2 = dispatcher.execute("blog", "blogController",
        "list", args, argTypes);

// 创建透明代理
BlogService blog = dispatcher.createProxy(BlogService.class, "blog", "blogService", "sub1");
String title = blog.getTitle(42);
```

### RemotePluginClient
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 远程插件执行客户端。连接到远程子服务器，发送方法执行请求并接收响应。每个子服务器维护一个 TCP 长连接，首次执行时建立并复用到后续请求，连接断开时自动重连。当前实现为同步阻塞语义，每个请求使用唯一 requestId 与响应匹配。直接使用 `ProtocolCodec` 与 `RemoteProtocol` 完成 TCP 二进制帧编解码。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `startRemoteMode` | `String host, int port, String authToken` | `boolean` | 启动远程模式，建立 TCP 连接并完成握手 |
| `startRemoteMode` | `String host, int port` | `boolean` | 启动远程模式（无认证） |
| `stopRemoteMode` | `String host, int port` | `void` | 停止远程模式，断开与指定子服务器的连接 |
| `stopAll` | - | `void` | 停止所有远程连接 |
| `isConnected` | `String host, int port` | `boolean` | 检查与指定子服务器的连接是否活跃 |
| `executeRemotely` | `String host, int port, String pluginId, String beanName, String methodName, List<String> args, List<String> argTypes` | `ExecuteResponse` | 远程执行插件方法（同步等待响应，连接未建立时自动连接） |
| `executeRemotely` | `String host, int port, String pluginId, String beanName, String methodName` | `ExecuteResponse` | 远程执行插件方法（无参数） |

#### Usage Example
```java
RemotePluginClient client = new RemotePluginClient();

// 启动远程模式
client.startRemoteMode("192.168.1.100", 9700, "auth-token");

// 远程执行插件方法
ExecuteResponse result = client.executeRemotely("192.168.1.100", 9700,
        "blog", "blogController", "list", args, argTypes);

// 停止远程模式
client.stopRemoteMode("192.168.1.100", 9700);
```

### RequestCoordinator
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 请求协调器，CS 中心化架构核心。当客户端不指定子服务器时，由协调器决定执行位置：优先本地执行（若插件在本地已加载且配置了 `localManagerRef`，通过 `Application.HotPluginManagerRef.getServiceFromPlugin` 获取 Bean 反射执行），本地无此插件时按轮询策略转发到在线子服务器。协调器本身也是一个服务器节点，可本地执行也可转发。远程转发时使用轮询负载均衡，并根据响应成功与否更新子服务器在线状态。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RequestCoordinator` | `SubServerRegistry registry, RemoteTransport transport, Application.HotPluginManagerRef localManagerRef` | - | 构造请求协调器 |
| `dispatch` | `ExecuteRequest request` | `ExecuteResponse` | 调度执行请求，优先本地执行，本地无此插件时转发到子服务器 |

#### Usage Example
```java
SubServerRegistry registry = new SubServerRegistry();
RemoteTransport transport = new TcpTransport();
RequestCoordinator coordinator = new RequestCoordinator(registry, transport, managerRef);

ExecuteResponse response = coordinator.dispatch(request);
```

### RemoteProxy
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 远程服务代理工厂。通过 Java 动态代理实现自动包装/解包：调用方像调用本地方法一样调用远程服务，代理自动将方法名和参数序列化为 `ExecuteRequest`，通过 `RemoteExecutionDispatcher` 发送到远程服务器，并将响应解包为返回值。自动包装规则：方法名映射为 `ExecuteRequest.methodName`，每个参数 JSON 序列化为字符串存入 `args`、全限定类名存入 `argTypes`，返回值从 `ExecuteResponse.result` 反序列化为方法返回类型。远程执行失败时抛出 `RuntimeException`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `create` | `Class<T> interfaceClass, String pluginId, String beanName, RemoteExecutionDispatcher dispatcher, String subServerId` | `T` | 创建远程服务代理（指定子服务器，静态方法） |
| `create` | `Class<T> interfaceClass, String pluginId, String beanName, RemoteExecutionDispatcher dispatcher` | `T` | 创建远程服务代理（由协调器分配，静态方法） |

#### Inner Class: RemoteInvocationHandler
- **Type**: private static class（implements `java.lang.reflect.InvocationHandler`）
- **Description**: 远程调用处理器，拦截代理对象上的方法调用将其转换为远程执行请求。`Object` 原生方法直接本地调用，其余方法构建 `ExecuteRequest` 并根据是否指定 `subServerId` 选择 `executeOn` 或 `execute` 发送。

#### Usage Example
```java
// 定义服务接口（需在主程序和子服务器上同时存在）
public interface BlogService {
    String getTitle(long id);
    List<String> getPosts(int page, int size);
}

// 创建远程代理（指定子服务器）
BlogService blog = RemoteProxy.create(BlogService.class, "blog", "blogService",
        dispatcher, "sub1");
String title = blog.getTitle(42);

// 创建远程代理（由协调器分配）
BlogService blog2 = RemoteProxy.create(BlogService.class, "blog", "blogService",
        dispatcher, null);
List<String> posts = blog2.getPosts(1, 10);
```

### SubServerRegistry
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 子服务器注册表。管理所有已注册的远程子运算服务器，提供注册、注销、查询等方法。使用 `ConcurrentHashMap` 保证线程安全。安全设计：仅提供 Java 方法，不暴露 HTTP 接口，调用方通过方法调用操作子服务器列表。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `registerSubServer` | `String id, String host, int port, String authToken` | `SubServerInfo` | 注册子服务器（ID 已存在则更新连接信息） |
| `registerSubServer` | `String id, String host, int port` | `SubServerInfo` | 注册子服务器（无认证） |
| `unregisterSubServer` | `String id` | `boolean` | 注销子服务器，不存在返回 false |
| `getSubServer` | `String id` | `SubServerInfo` | 获取子服务器信息，不存在返回 null |
| `getSubServers` | - | `List<SubServerInfo>` | 获取所有已注册的子服务器 |
| `getOnlineSubServers` | - | `List<SubServerInfo>` | 获取所有在线的子服务器 |
| `updateOnlineStatus` | `String id, boolean online` | `void` | 更新子服务器在线状态（上线时刷新心跳时间戳） |
| `size` | - | `int` | 返回已注册的子服务器数量 |
| `clear` | - | `void` | 清空所有子服务器 |

#### Usage Example
```java
SubServerRegistry registry = new SubServerRegistry();
registry.registerSubServer("sub1", "192.168.1.100", 9700, "auth-token");

List<SubServerInfo> online = registry.getOnlineSubServers();
registry.unregisterSubServer("sub1");
```

### SubServerInfo
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: 子服务器信息模型。描述一个远程子运算服务器的连接信息（标识、主机、端口、认证令牌）与状态（在线状态、最后心跳时间戳）。重写了 `equals`/`hashCode`（基于 id、host、port）与 `toString`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `SubServerInfo` | - | - | 无参构造 |
| `SubServerInfo` | `String id, String host, int port` | - | 构造（无认证） |
| `SubServerInfo` | `String id, String host, int port, String authToken` | - | 构造（含认证令牌） |
| `getId` | - | `String` | 获取子服务器唯一标识 |
| `setId` | `String id` | `void` | 设置子服务器唯一标识 |
| `getHost` | - | `String` | 获取主机地址 |
| `setHost` | `String host` | `void` | 设置主机地址 |
| `getPort` | - | `int` | 获取 TCP 端口 |
| `setPort` | `int port` | `void` | 设置 TCP 端口 |
| `getAuthToken` | - | `String` | 获取认证令牌 |
| `setAuthToken` | `String authToken` | `void` | 设置认证令牌 |
| `isOnline` | - | `boolean` | 获取是否在线 |
| `setOnline` | `boolean online` | `void` | 设置是否在线 |
| `getLastHeartbeat` | - | `long` | 获取最后心跳时间戳（毫秒） |
| `setLastHeartbeat` | `long lastHeartbeat` | `void` | 设置最后心跳时间戳 |

### TcpTransport
- **Type**: class
- **Implements**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport`
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: TCP 传输实现。使用 TCP 长连接 + 二进制帧协议（依赖 `ProtocolCodec` 与 `RemoteProtocol`）传输执行请求。每个目标服务器维护一个长连接复用到后续请求，连接断开时自动重连。通过 `MSG_HANDSHAKE`/`MSG_HANDSHAKE_ACK` 完成握手与认证。适合高性能场景，但需要额外端口。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `send` | `String host, int port, String authToken, ExecuteRequest request` | `ExecuteResponse` | 发送执行请求（连接未建立时自动连接，失败时重连一次） |
| `connect` | `String host, int port, String authToken` | `boolean` | 建立 TCP 连接并完成握手 |
| `disconnect` | `String host, int port` | `void` | 断开与指定服务器的连接 |
| `isConnected` | `String host, int port` | `boolean` | 检查连接是否活跃 |
| `getType` | - | `String` | 返回 "TCP" |
| `disconnectAll` | - | `void` | 断开所有连接 |

#### Usage Example
```java
TcpTransport transport = new TcpTransport();
transport.connect("192.168.1.100", 9700, "auth-token");

ExecuteResponse resp = transport.send("192.168.1.100", 9700, "auth-token", request);

transport.disconnectAll();
```

### HttpTransport
- **Type**: class
- **Implements**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.RemoteTransport`
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Description**: HTTP 传输实现（JSON-RPC 风格）。使用 HTTP POST 发送 JSON 请求体到服务端的 RPC 端点，复用现有 Web 服务器端口，无需额外端口。端点路径由构造函数指定（需与服务端控制器中注册的路径一致），默认为 `/__remote_rpc__`。请求头含 `Content-Type: application/json;charset=UTF-8` 与可选 `X-Auth-Token`。HTTP 模式无需建立长连接，`connect`/`disconnect`/`isConnected` 分别恒返回 true/无操作/true。默认超时 30000 毫秒。

#### Fields

| Field | Type | Value | Description |
|-------|------|-------|-------------|
| `DEFAULT_ENDPOINT` | `String` | `"/__remote_rpc__"` | 默认 RPC 端点路径（public static final） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `HttpTransport` | - | - | 构造 HTTP 传输，使用默认端点路径 |
| `HttpTransport` | `String endpoint` | - | 构造 HTTP 传输，指定端点路径（空则用默认） |
| `getEndpoint` | - | `String` | 返回当前端点路径 |
| `send` | `String host, int port, String authToken, ExecuteRequest request` | `ExecuteResponse` | 通过 HTTP POST 发送 JSON 请求并解析响应（4xx+ 返回错误响应） |
| `connect` | `String host, int port, String authToken` | `boolean` | HTTP 模式无需长连接，恒返回 true |
| `disconnect` | `String host, int port` | `void` | HTTP 模式无需断开，无操作 |
| `isConnected` | `String host, int port` | `boolean` | HTTP 模式始终可用，恒返回 true |
| `getType` | - | `String` | 返回 "HTTP" |

#### Usage Example
```java
HttpTransport transport = new HttpTransport("/my-rpc");
ExecuteResponse resp = transport.send("192.168.1.100", 8080, "auth-token", request);
```

### RemoteClientProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.plugin-jar.remote.client")`
- **Description**: 远程插件客户端配置属性，前缀 `jaravel.plugin-jar.remote.client`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用客户端，默认 true |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 |
| `getTransport` | - | `String` | 获取传输模式，默认 "tcp" |
| `setTransport` | `String transport` | `void` | 设置传输模式（tcp 或 http） |
| `getHttpEndpoint` | - | `String` | 获取 HTTP 模式 RPC 端点路径，默认 `HttpTransport.DEFAULT_ENDPOINT` |
| `setHttpEndpoint` | `String httpEndpoint` | `void` | 设置 HTTP 端点路径 |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-jar:
    remote:
      client:
        enabled: true
        transport: tcp             # tcp（默认）或 http
        http-endpoint: /my-rpc     # HTTP 模式端点路径，需与服务端一致
```

### RemoteClientAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.client`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(RemoteExecutionDispatcher.class)`, `@EnableConfigurationProperties(RemoteClientProperties.class)`
- **Description**: 远程插件客户端自动装配。引入本模块后自动创建 `RemoteExecutionDispatcher` Bean。根据配置 `jaravel.plugin-jar.remote.client.transport` 选择 TCP 或 HTTP 传输：`http` 时创建 `HttpTransport`（使用配置的端点路径），否则创建 `TcpTransport`。若 `HotPluginManager` Bean 存在，自动注入为本地执行引用，使 `RemoteExecutionDispatcher.execute` 支持本地优先执行。安全设计：不暴露任何 HTTP 接口，所有操作均通过 Java 方法调用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `remoteExecutionDispatcher` | `RemoteClientProperties properties, ObjectProvider<HotPluginManager> managerProvider` | `RemoteExecutionDispatcher` | 创建远程执行调度器（`@Bean`），按配置选择传输层并注入本地插件管理器引用 |

#### Usage Example
```java
// 引入本模块后自动装配，直接注入使用
@Autowired
private RemoteExecutionDispatcher dispatcher;

// 也可通过配置切换传输模式
// jaravel.plugin-jar.remote.client.transport=http
```

## 跨模块依赖说明

本模块（plugin-jar-remote-client）的类均位于 `client` 包下，但其运行依赖以下外部模块的类型：

### 依赖 plugin-jar-remote-server 的 protocol 包

`com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol` 包提供传输契约与协议定义，本模块的 `TcpTransport`、`HttpTransport`、`RemotePluginClient`、`RequestCoordinator`、`RemoteExecutionDispatcher`、`RemoteProxy` 均引用其中类型：

| 外部类型 | 所在包 | 本模块使用方 | 用途 |
|---------|--------|-------------|------|
| `RemoteTransport` | `...remote.protocol` | `TcpTransport`（实现）、`HttpTransport`（实现）、`RemoteExecutionDispatcher`、`RequestCoordinator` | 传输层接口契约 |
| `ExecuteRequest` | `...remote.protocol` | `RemoteExecutionDispatcher`、`RequestCoordinator`、`RemoteProxy`、`TcpTransport`、`HttpTransport`、`RemotePluginClient` | 执行请求数据模型 |
| `ExecuteResponse` | `...remote.protocol` | 同上 | 执行响应数据模型（含 `success`、`error`、`result`、`ok`、`error` 静态工厂） |
| `ProtocolCodec` | `...remote.protocol` | `TcpTransport`、`RemotePluginClient` | 二进制帧编解码（`encodeFrame`、`readFrameWithType`） |
| `RemoteProtocol` | `...remote.protocol` | `TcpTransport`、`RemotePluginClient` | 协议消息类型常量（`MSG_HANDSHAKE`、`MSG_HANDSHAKE_ACK`、`MSG_EXECUTE_REQUEST`、`MSG_EXECUTE_RESPONSE`、`MSG_ERROR`） |

### 依赖 plugin-jar-core

| 外部类型 | 所在包 | 本模块使用方 | 用途 |
|---------|--------|-------------|------|
| `Application.HotPluginManagerRef` | `...plugin.jar.annotation` | `RemoteExecutionDispatcher`、`RequestCoordinator`、`RemoteClientAutoConfiguration` | 本地插件管理器引用接口，通过 `getServiceFromPlugin` 获取本地 Bean 实现优先本地执行 |
| `HotPluginManager` | `...plugin.jar.manager` | `RemoteClientAutoConfiguration` | 核心插件管理器，作为可选 Bean 注入协调器以启用本地执行 |

> 注：上述 `protocol` 包类型由 plugin-jar-remote-server 模块定义，本模块作为客户端消费这些契约；服务端需实现对应的处理器（如 `HttpRpcHandler.processRequest`）与 TCP 服务端逻辑。详细的服务端 API 请参阅 plugin-jar-remote-server 模块的 AI-API 文档。
