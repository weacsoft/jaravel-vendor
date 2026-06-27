# plugin-jar-remote-server

> 包名：com.weacsoft.jaravel.vendor.plugin.jar.remote.server
> 版本：0.1.0

JAR 插件远程执行服务端（P2SP 子节点）。通过 TCP 或 HTTP 接收远程方法调用请求，在本地执行插件方法。

## 可独立于热加载使用

**本模块可独立于 jaravel 热加载插件系统（plugin-jar-core）使用，仅依赖 SpringBoot。** 远程执行系统通过 `BeanResolver` 接口获取目标 Bean，与具体的 Bean 来源解耦：

- **有热加载插件（plugin-jar-core 在类路径上）**：自动使用 `HotPluginManager` 适配为 `BeanResolver`，按 `pluginId` 从对应插件的 ClassLoader 中获取 Bean，执行热加载插件中的方法。
- **无热加载插件（仅引入 remote-server + remote-client）**：自动使用 `SpringBeanResolver`，直接从 Spring `ApplicationContext` 中按 `beanName` 获取普通 Spring Bean，`pluginId` 参数被忽略。

`plugin-jar-core` 现为**可选依赖**（`optional=true`）。自动装配（`RemoteServerAutoConfiguration`）通过 `@ConditionalOnClass`/`@ConditionalOnMissingClass` 在启动时自动选择合适的 `BeanResolver` 实现，使用者无需关心底层差异。

> 相关 SPI 类位于 `com.weacsoft.jaravel.vendor.plugin.jar.remote.spi` 包：
> - `BeanResolver`：Bean 解析器接口，方法 `Object getBean(String pluginId, String beanName)`
> - `SpringBeanResolver`：基于 Spring `ApplicationContext` 的默认实现

## 核心类

| 类 | 说明 |
|---|------|
| `RemoteProtocol` | 协议常量（魔数、消息类型、帧头长度） |
| `ProtocolCodec` | 帧编解码工具 |
| `ExecuteRequest` | 执行请求模型 |
| `ExecuteResponse` | 执行响应模型 |
| `RemoteTransport` | 传输层抽象接口 |
| `RemotePluginServer` | TCP 服务端（监听、握手、执行、线程池） |
| `HttpRpcHandler` | HTTP RPC 静态处理工具（用户自行创建控制器调用） |
| `RemoteServerProperties` | 配置属性 |
| `RemoteServerAutoConfiguration` | 自动装配（TCP 模式） |

## 配置

```yaml
jaravel:
  plugin-jar:
    remote:
      server:
        enabled: true              # 启用 TCP 服务端
        port: 9700                 # TCP 监听端口
        auth-token: "my-secret"    # 认证令牌
```

### 独立使用（无热加载）

无需引入 plugin-jar-core，仅依赖 SpringBoot 即可使用远程执行功能。此时远程执行的目标是普通 Spring Bean，`pluginId` 参数被忽略。配置与上方完全一致，无需额外开关——自动装配会检测类路径上是否存在 `HotPluginManager`，不存在则自动使用 `SpringBeanResolver`。

Maven 依赖（仅需 remote-server、remote-client 与 SpringBoot，无需 plugin-jar-core）：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-remote-server</artifactId>
</dependency>
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>plugin-jar-remote-client</artifactId>
</dependency>
```

调用示例（被远程执行的 Bean 为普通 Spring Bean，`pluginId` 传任意值即可）：

```java
// 客户端：调用远程 Spring Bean 的方法
// pluginId 在无热加载模式下被忽略，beanName 须为 Spring 容器中注册的 Bean 名称
ExecuteResponse resp = dispatcher.execute("any", "userService", "getUser", args, argTypes);
```

> 自动选择逻辑：`RemoteServerAutoConfiguration` 通过 `@ConditionalOnMissingClass("...HotPluginManager")` 激活 `SpringBeanResolver`；当类路径存在 `HotPluginManager` 时改用 `HotPluginManager` 适配为 `BeanResolver`。使用者无需手动配置。

## 工作原理

本模块作为 P2SP 架构中的**子节点**（执行端），接收来自主节点（`plugin-jar-remote-client`）的方法调用请求，在本地通过反射执行插件方法并返回结果。

### TCP 模式（默认）

启动时自动创建 `RemotePluginServer`，监听指定端口，采用自定义二进制帧协议：
- 帧头：魔数(4B) + 消息类型(1B) + 正文长度(4B)
- 正文：JSON 序列化的 `ExecuteRequest` / `ExecuteResponse`
- 认证：握手阶段校验 `auth-token`

### HTTP 模式

通过 `HttpRpcHandler` 提供静态方法，用户自行创建 Spring Controller 调用。注入 `BeanResolver`（自动装配已提供）传入 `processRequest`：

```java
@RestController
@RequestMapping("/rpc")
public class RpcController {
    @Autowired
    private BeanResolver beanResolver;  // 由自动装配提供（SpringBeanResolver 或 HotPluginManager 适配）

    @PostMapping("/execute")
    public String execute(@RequestBody String body,
                          @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return HttpRpcHandler.processRequest(beanResolver, "my-secret", token, body);
    }
}
```

## 树形拓扑（中继转发）

当单个子节点无法独立完成插件执行（本地未加载目标插件）时，可通过树形中继转发将请求逐级下发给子节点的子节点，实现多层级联执行。此能力由 `relay-enabled` 开关控制，默认关闭，保持向后兼容。

### 架构图

```
                        ┌──────────────────┐
                        │  Coordinator     │  (plugin-jar-remote-client 主节点)
                        │  协调器/根节点     │
                        └────────┬─────────┘
                                 │ registerChildNode
                ┌────────────────┼────────────────┐
                ▼                ▼                 ▼
        ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
        │  Node-A      │ │  Node-B      │ │  Node-C      │  (中继层)
        │ relay=true   │ │ relay=true   │ │ relay=false  │
        └──────┬───────┘ └──────────────┘ └──────────────┘
               │ registerChildNode
        ┌──────┴───────┐
        ▼              ▼
  ┌────────────┐ ┌────────────┐
  │  Node-A1   │ │  Node-A2   │  (叶子层，仅本地执行)
  └────────────┘ └────────────┘
```

请求流向：Coordinator → Node-A（本地无插件，中继转发）→ Node-A1（本地执行成功，返回结果）。

### 三层架构

本模块采用三层分离设计，核心逻辑不依赖 SpringBoot，可独立用于非 Spring 环境：

| 层级 | 类 | 说明 |
|------|------|------|
| **核心层（无 SpringBoot）** | `RemoteProtocol`、`ProtocolCodec`、`ExecuteRequest`、`ExecuteResponse`、`RemoteTransport` | 协议常量、帧编解码、请求/响应模型、传输抽象。纯 POJO，无 Spring 依赖 |
| **SPI 层** | `BeanResolver`、`SpringBeanResolver` | Bean 解析器接口及其 Spring 默认实现。解耦远程执行与具体 Bean 来源（热加载插件或 Spring 容器），使本模块可独立于 plugin-jar-core 使用 |
| **服务核心层** | `RemotePluginServer`、`HttpRpcHandler` | TCP 服务端实现与 HTTP RPC 处理工具，含中继转发逻辑。通过 `BeanResolver` 接口获取 Bean（热加载模式由 `HotPluginManager` 适配，无热加载模式由 `SpringBeanResolver` 实现），不直接依赖 Spring 容器或 plugin-jar-core |
| **适配层** | `RemoteServerProperties`、`RemoteServerAutoConfiguration` | SpringBoot 配置属性与自动装配。`enabled=true` 时自动创建并启动 `RemotePluginServer`，并通过 `@ConditionalOnClass`/`@ConditionalOnMissingClass` 自动选择 `BeanResolver` 实现 |

### 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `node-id` | String | 自动生成 `server-xxxxxxxx` | 本节点 ID，树形拓扑中唯一标识。用于路由元数据与防环检测 |
| `relay-enabled` | boolean | `false` | 是否启用中继转发。启用后本地无插件时自动转发给已注册的子节点 |
| `max-hops` | int | `5` | 请求最大跳数限制。超过此跳数的请求将被拒绝，防止无限转发 |

```yaml
jaravel:
  plugin-jar:
    remote:
      server:
        enabled: true
        port: 9700
        auth-token: "my-secret"
        # 树形中继配置
        node-id: "node-root"
        relay-enabled: true
        max-hops: 5
```

### 中继转发流程

1. **本地执行**：收到执行请求后，`RemotePluginServer.executeWithRelay` 优先尝试本地执行（通过 `tryLocalExecute` 获取插件 Bean 反射调用）。
2. **转发子节点**：本地无此插件（`tryLocalExecute` 返回 null）且 `relayEnabled=true` 时，调用 `relayToChildren` 轮询选择在线子节点，通过 `forwardToChild` 经 TCP 转发。
3. **防环检测**：转发前调用 `ExecuteRequest.markVisited` 标记本节点已访问并递增跳数，子节点收到请求后检查 `visitedNodes` 是否包含自身。

```
executeWithRelay(request)
  ├── 防环检查：visitedNodes 包含本节点？→ 拒绝
  ├── 跳数检查：currentHop > maxHops？→ 拒绝
  ├── tryLocalExecute(request) → 本地有插件？→ 返回结果
  └── relayEnabled && 有子节点？
        ├── relayToChildren(request)
        │     ├── markVisited(nodeId)  // 标记访问，跳数+1
        │     ├── 轮询选择在线子节点
        │     └── forwardToChild(child, relayRequest)  // TCP 转发
        └── 无中继能力 → 返回 "Bean 未找到" 错误
```

### 防环机制（三重保护）

树形转发通过三重机制防止请求环路：

| 保护机制 | 字段/参数 | 作用 |
|----------|-----------|------|
| **visitedNodes** | `ExecuteRequest.visitedNodes` | 已访问节点 ID 列表。子节点收到请求时检查自身是否在列表中，若在则拒绝执行，防止环路 |
| **maxHops** | `ExecuteRequest.maxHops` / `RemotePluginServer.maxHops` | 最大跳数限制（默认 5）。每转发一次 `currentHop +1`，超过 `maxHops` 即拒绝转发 |
| **maxDepth** | `SubServerRegistry.maxDepth` | 树形注册最大深度（默认 5，客户端侧）。注册子节点时若深度超过限制抛出 `IllegalStateException`，从拓扑构建阶段即阻断过深层级 |

### 向后兼容

- `relay-enabled` 默认为 `false`：未配置时服务端行为与原有扁平模式完全一致，仅本地执行，不转发。
- 树形路由元数据字段（`sourceNodeId`/`visitedNodes`/`maxHops`/`currentHop`）在扁平模式下保持默认值，不影响原有逻辑。
- 现有 `MSG_EXECUTE_REQUEST` 消息类型照常处理；树形转发使用独立的 `MSG_RELAY_REQUEST`/`MSG_RELAY_RESPONSE` 消息类型，互不干扰。
