# plugin-jar-remote-server AI-API Reference

> Module: `plugin-jar-remote-server` | Package: `com.weacsoft.jaravel.vendor.plugin.jar.remote` | Version: 0.1.0

## Overview

plugin-jar-remote-server 模块是 JAR 插件系统的远程执行服务端（P2SP 子节点），通过 TCP 接收远程方法调用请求并在本地执行插件方法。模块包含两个包：

- **server 包**：提供 TCP 服务端实现（`RemotePluginServer`）、HTTP RPC 请求处理工具（`HttpRpcHandler`）、自动装配（`RemoteServerAutoConfiguration`）和配置属性（`RemoteServerProperties`）。
- **protocol 包**：定义远程通信协议，包括执行请求/响应模型（`ExecuteRequest`/`ExecuteResponse`）、二进制帧编解码工具（`ProtocolCodec`）、协议常量（`RemoteProtocol`）以及传输层抽象接口（`RemoteTransport`）。

服务端支持两种接入方式：TCP 模式（通过 `RemotePluginServer` 监听独立端口，使用二进制帧协议，适合高性能场景）和 HTTP 模式（通过 `HttpRpcHandler` 静态方法，由用户自行创建控制器调用，复用现有 Web 服务器端口）。两种模式均通过 `HotPluginManagerRef`（来自 plugin-jar-core 的 `@Application` 注解）获取插件 Bean 并反射调用目标方法，实现方法级隔离——每次调用独立，不共享调用间状态。通信协议采用大端序二进制帧：`magic(4B) + msgType(4B) + bodyLen(4B) + body(N bytes)`，body 为 UTF-8 编码的 JSON。支持可选的 authToken 握手认证，请求体大小限制 50MB。

## Classes & Interfaces

### HttpRpcHandler
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.server`
- **Description**: HTTP RPC 请求处理工具类。不注册任何 HTTP 端点，仅提供静态方法供用户在自己的控制器中调用。用户自行决定端点路径、HTTP 方法、认证方式等，只需将请求体 JSON 字符串传入本类的静态方法即可。内部解析 `ExecuteRequest`、本地执行插件方法、返回 `ExecuteResponse` 的 JSON 序列化。构造方法为 private，不可实例化。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `processRequest` | `Application.HotPluginManagerRef managerRef, String authToken, String token, String requestBody` | `String` | 处理 RPC 请求（带认证）。若 authToken 非空且与传入 token 不匹配，返回认证失败响应；否则解析请求体、本地执行插件方法并返回响应 JSON 字符串 |
| `processRequest` | `Application.HotPluginManagerRef managerRef, String requestBody` | `String` | 处理 RPC 请求（不认证）。委托给带认证版本，authToken 与 token 均传 null |

#### Usage Example
```java
@RestController
public class MyRpcController {
    @Autowired
    private HotPluginManager manager;

    @PostMapping("/my-custom-rpc-endpoint")
    public String handleRpc(@RequestBody String body,
                            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        // manager 实现了 HotPluginManagerRef 接口
        return HttpRpcHandler.processRequest(manager, "my-secret", token, body);
    }
}
```

### RemotePluginServer
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.server`
- **Description**: 远程插件执行 TCP 服务端。监听 TCP 端口，接收来自客户端的方法执行请求，在本地通过 `HotPluginManagerRef` 获取插件 Bean 并反射调用目标方法，返回执行结果。使用独立缓存线程池（守护线程）处理每个客户端连接，支持并发请求。安全设计：不暴露 HTTP 接口、支持 authToken 握手认证（可选）、请求体大小限制 50MB。方法级隔离：每次调用独立，隔离度由插件 ClassLoader 保证。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `RemotePluginServer` | `int port, String authToken, Application.HotPluginManagerRef managerRef` | - | 构造远程插件执行服务端，指定监听端口、认证令牌（null 表示不认证）和插件管理器引用 |
| `start` | - | `void` | 启动 TCP 服务端，创建线程池并开启接收循环（已运行则跳过） |
| `stop` | - | `void` | 停止 TCP 服务端，关闭 ServerSocket 与所有活跃连接，关闭线程池 |
| `isRunning` | - | `boolean` | 返回服务端是否正在运行 |
| `getPort` | - | `int` | 返回监听端口 |
| `getActiveConnectionCount` | - | `int` | 返回当前活跃连接数 |

#### Usage Example
```java
// 运行时手动启动（不依赖自动装配）
Application.HotPluginManagerRef ref = pluginManager; // manager 实现了该接口
RemotePluginServer server = new RemotePluginServer(9700, "secret-token", ref);
server.start();

// 停止服务端
server.stop();
```

### RemoteServerAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.server`
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(HotPluginManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.plugin-jar.remote.server", name = "enabled", havingValue = "true")`, `@EnableConfigurationProperties(RemoteServerProperties.class)`
- **Description**: 远程插件服务端自动装配。当 `jaravel.plugin-jar.remote.server.enabled=true` 时自动创建 `RemotePluginServer` Bean 并调用 `start()` 启动 TCP 服务端。引入本模块但未设置 `enabled=true` 时不会触发自动装配，可在运行时通过直接构造 `RemotePluginServer` 手动启动。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `remotePluginServer` | `RemoteServerProperties properties, HotPluginManager manager` | `RemotePluginServer` | 创建远程插件服务端 Bean 并自动启动（`@Bean`） |

### RemoteServerProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.server`
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.plugin-jar.remote.server")`
- **Description**: 远程插件服务端配置属性，前缀 `jaravel.plugin-jar.remote.server`。TCP 模式通过 `enabled=true` 自动启动；HTTP 模式不自动注册端点，用户需自行创建控制器并调用 `HttpRpcHandler.processRequest` 静态方法。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `isEnabled` | - | `boolean` | 是否启用 TCP 远程服务端，默认 false |
| `setEnabled` | `boolean enabled` | `void` | 设置是否启用 TCP 远程服务端 |
| `getPort` | - | `int` | 获取 TCP 监听端口，默认 9700 |
| `setPort` | `int port` | `void` | 设置 TCP 监听端口 |
| `getAuthToken` | - | `String` | 获取认证令牌（null 或空表示不认证） |
| `setAuthToken` | `String authToken` | `void` | 设置认证令牌 |

#### Usage Example
```yaml
# application.yml
jaravel:
  plugin-jar:
    remote:
      server:
        enabled: true
        port: 9700
        auth-token: "secret-token"
```

### ExecuteRequest
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol`
- **Description**: 远程方法执行请求。由客户端发送给服务端，请求在服务端本地执行指定的插件方法。隔离度：方法级，每次执行都是独立的，不共享调用间状态。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ExecuteRequest` | - | - | 无参构造 |
| `ExecuteRequest` | `String requestId, String pluginId, String beanName, String methodName, List<String> args, List<String> argTypes` | - | 全参构造 |
| `getRequestId` | - | `String` | 获取请求 ID（用于匹配响应） |
| `setRequestId` | `String requestId` | `void` | 设置请求 ID |
| `getPluginId` | - | `String` | 获取插件 ID |
| `setPluginId` | `String pluginId` | `void` | 设置插件 ID |
| `getBeanName` | - | `String` | 获取 Bean 名称（在插件 Spring 容器中的名称） |
| `setBeanName` | `String beanName` | `void` | 设置 Bean 名称 |
| `getMethodName` | - | `String` | 获取方法名 |
| `setMethodName` | `String methodName` | `void` | 设置方法名 |
| `getArgs` | - | `List<String>` | 获取参数值列表（每个参数 JSON 序列化为字符串） |
| `setArgs` | `List<String> args` | `void` | 设置参数值列表 |
| `getArgTypes` | - | `List<String>` | 获取参数类型列表（全限定类名，用于反射查找方法） |
| `setArgTypes` | `List<String> argTypes` | `void` | 设置参数类型列表 |

#### Usage Example
```java
ExecuteRequest request = new ExecuteRequest(
        "uuid-1234",
        "blog",
        "blogController",
        "list",
        List.of("{\"page\":1}"),
        List.of("java.lang.Integer")
);
```

### ExecuteResponse
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol`
- **Description**: 远程方法执行响应。由服务端返回给客户端，包含执行结果或错误信息。提供 `ok`/`error` 静态工厂方法构造成功/失败响应。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ExecuteResponse` | - | - | 无参构造 |
| `ExecuteResponse` | `String requestId, boolean success, String result, String resultType, String error` | - | 全参构造 |
| `ok` | `String requestId, String result, String resultType` | `ExecuteResponse` | 静态工厂方法，构造成功响应（success=true） |
| `error` | `String requestId, String error` | `ExecuteResponse` | 静态工厂方法，构造失败响应（success=false） |
| `getRequestId` | - | `String` | 获取对应请求的 ID |
| `setRequestId` | `String requestId` | `void` | 设置请求 ID |
| `isSuccess` | - | `boolean` | 是否执行成功 |
| `setSuccess` | `boolean success` | `void` | 设置是否成功 |
| `getResult` | - | `String` | 获取执行结果（JSON 序列化，成功时） |
| `setResult` | `String result` | `void` | 设置执行结果 |
| `getResultType` | - | `String` | 获取结果类型（全限定类名，成功时） |
| `setResultType` | `String resultType` | `void` | 设置结果类型 |
| `getError` | - | `String` | 获取错误信息（失败时） |
| `setError` | `String error` | `void` | 设置错误信息 |

#### Usage Example
```java
// 成功响应
ExecuteResponse ok = ExecuteResponse.ok("uuid-1234", "{\"title\":\"hello\"}", "java.lang.String");

// 失败响应
ExecuteResponse err = ExecuteResponse.error("uuid-1234", "Bean 未找到: pluginId=blog, beanName=blogController");
```

### ProtocolCodec
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol`
- **Description**: 协议帧读写工具。负责将消息编码为二进制帧和从二进制流解码消息。帧格式遵循 `RemoteProtocol` 定义：读取时校验魔数和 body 长度（上限 50MB），构造方法为 private，不可实例化。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `readFrame` | `InputStream in` | `String` | 读取一帧消息，返回消息体 JSON 字符串（校验魔数与 body 长度） |
| `readFrameWithType` | `InputStream in` | `Object[]` | 读取一帧消息并返回消息类型，`[0]=msgType`（int）、`[1]=body`（JSON 字符串） |
| `encodeFrame` | `int msgType, String body` | `byte[]` | 编码一帧消息，返回编码后的字节数组（body 为 null 时编码为空 body） |

#### Usage Example
```java
// 服务端读取一帧
Object[] frame = ProtocolCodec.readFrameWithType(inputStream);
int msgType = (int) frame[0];
String body = (String) frame[1];

// 编码并发送一帧
byte[] bytes = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HEARTBEAT, "{}");
outputStream.write(bytes);
outputStream.flush();
```

### RemoteProtocol
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol`
- **Description**: 远程插件执行协议常量。定义二进制帧格式（大端序）：`magic(4 bytes) + msgType(4 bytes) + bodyLen(4 bytes) + body(N bytes)`。其中 magic 固定为 `0x4A52504D`（"JRPM" = Jaravel Remote Plugin Protocol），body 为 UTF-8 编码的 JSON 消息体。构造方法为 private，不可实例化。

#### Constants

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `MAGIC` | `int` | `0x4A52504D` | 协议魔数（"JRPM"） |
| `MSG_HANDSHAKE` | `int` | `1` | 消息类型：握手 |
| `MSG_HANDSHAKE_ACK` | `int` | `2` | 消息类型：握手确认 |
| `MSG_HEARTBEAT` | `int` | `3` | 消息类型：心跳 |
| `MSG_EXECUTE_REQUEST` | `int` | `4` | 消息类型：执行请求 |
| `MSG_EXECUTE_RESPONSE` | `int` | `5` | 消息类型：执行响应 |
| `MSG_ERROR` | `int` | `6` | 消息类型：错误 |
| `HEADER_LENGTH` | `int` | `12` | 帧头长度（magic + msgType + bodyLen） |

### RemoteTransport
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol`
- **Description**: 远程传输层抽象。定义统一的传输接口，支持 TCP 和 HTTP 两种实现。客户端通过此接口发送执行请求，不关心底层传输细节。TCP 模式使用独立 TCP 端口与二进制帧协议，适合高性能场景（需额外端口）；HTTP 模式使用 JSON-RPC over HTTP，复用现有 Web 服务器端口，适合无法开放额外端口的场景。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `send` | `String host, int port, String authToken, ExecuteRequest request` | `ExecuteResponse` | 发送执行请求并同步等待响应 |
| `connect` | `String host, int port, String authToken` | `boolean` | 建立连接（TCP 模式建立长连接，HTTP 模式为空操作），成功返回 true |
| `disconnect` | `String host, int port` | `void` | 断开连接 |
| `isConnected` | `String host, int port` | `boolean` | 检查连接是否活跃，活跃返回 true |
| `getType` | - | `String` | 返回传输类型名称（"TCP" 或 "HTTP"） |

#### Usage Example
```java
public class TcpRemoteTransport implements RemoteTransport {
    @Override
    public ExecuteResponse send(String host, int port, String authToken, ExecuteRequest request) {
        // 通过 TCP 二进制帧发送请求并接收响应
    }

    @Override
    public boolean connect(String host, int port, String authToken) { /* ... */ }

    @Override
    public void disconnect(String host, int port) { /* ... */ }

    @Override
    public boolean isConnected(String host, int port) { /* ... */ }

    @Override
    public String getType() { return "TCP"; }
}
```
