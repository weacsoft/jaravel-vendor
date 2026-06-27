# plugin-jar-remote-server

> 包名：com.weacsoft.jaravel.vendor.plugin.jar.remote.server
> 版本：0.1.0

JAR 插件远程执行服务端（P2SP 子节点）。通过 TCP 或 HTTP 接收远程方法调用请求，在本地执行插件方法。

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

## 工作原理

本模块作为 P2SP 架构中的**子节点**（执行端），接收来自主节点（`plugin-jar-remote-client`）的方法调用请求，在本地通过反射执行插件方法并返回结果。

### TCP 模式（默认）

启动时自动创建 `RemotePluginServer`，监听指定端口，采用自定义二进制帧协议：
- 帧头：魔数(4B) + 消息类型(1B) + 正文长度(4B)
- 正文：JSON 序列化的 `ExecuteRequest` / `ExecuteResponse`
- 认证：握手阶段校验 `auth-token`

### HTTP 模式

通过 `HttpRpcHandler` 提供静态方法，用户自行创建 Spring Controller 调用：

```java
@RestController
@RequestMapping("/rpc")
public class RpcController {
    @PostMapping("/execute")
    public Object execute(@RequestBody ExecuteRequest request) {
        return HttpRpcHandler.handle(request);
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
| **服务核心层** | `RemotePluginServer`、`HttpRpcHandler` | TCP 服务端实现与 HTTP RPC 处理工具，含中继转发逻辑。通过 `HotPluginManagerRef` 接口获取插件 Bean，不直接依赖 Spring 容器 |
| **适配层** | `RemoteServerProperties`、`RemoteServerAutoConfiguration` | SpringBoot 配置属性与自动装配。`enabled=true` 时自动创建并启动 `RemotePluginServer` |

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
