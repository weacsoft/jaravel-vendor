# plugin-jar-remote-client

> 包名：com.weacsoft.jaravel.vendor.plugin.jar.remote.client
> 版本：0.1.0

JAR 插件远程执行客户端（P2SP 主节点）。管理子服务器注册表，通过 TCP 或 HTTP 将方法调用分派到远程子服务器执行，实现主从业务处理。

## 核心类

| 类 | 说明 |
|---|------|
| `SubServerInfo` | 子服务器信息模型 |
| `SubServerRegistry` | 子服务器注册表（register, unregister, get） |
| `TcpTransport` | TCP 传输实现（长连接、自动重连） |
| `HttpTransport` | HTTP 传输实现（JSON-RPC，可配置端点路径） |
| `RemoteProxy` | 动态代理工厂（自动包装参数/解包返回值） |
| `RequestCoordinator` | 请求协调器（本地优先执行，轮询转发子服务器） |
| `RemoteExecutionDispatcher` | 高层调度器（整合注册表+传输+协调器） |
| `RemoteClientProperties` | 配置属性 |
| `RemoteClientAutoConfiguration` | 自动装配 |

## 配置

```yaml
jaravel:
  plugin-jar:
    remote:
      client:
        enabled: true              # 引入模块后默认启用
        transport: tcp             # tcp 或 http
        http-endpoint: /my-rpc     # HTTP 模式端点路径
```

## 工作原理

本模块作为 P2SP 架构中的**主节点**（协调端），管理多个子服务器（`plugin-jar-remote-server`），将方法调用请求分派到远程子服务器执行。

### 核心流程

1. **注册子服务器**：子服务器启动后向主节点注册（`SubServerRegistry.register`）
2. **本地优先**：`RequestCoordinator` 先尝试本地执行，失败后轮询转发到子服务器
3. **动态代理**：`RemoteProxy.create()` 生成接口的动态代理，自动包装参数、解包返回值
4. **传输层**：支持 TCP（长连接 + 自动重连）和 HTTP（JSON-RPC）两种模式

### 使用示例

```java
@Autowired
private RemoteExecutionDispatcher dispatcher;

// 注册子服务器
dispatcher.registerSubServer("node-1", "192.168.1.10", 9700);

// 通过动态代理调用远程方法
MyService proxy = RemoteProxy.create(MyService.class, dispatcher, "node-1");
String result = proxy.doWork("param");
```

## 树形路由

当子服务器数量较多或需要多层级联时，可启用树形路由模式。该模式下协调器按树形层级路由请求，根子服务器（直接子节点）本地执行不了时由其 `RemotePluginServer` 中继转发给孙节点，实现多层级联执行。

### 配置

```yaml
jaravel:
  plugin-jar:
    remote:
      client:
        enabled: true
        transport: tcp
        # 树形路由配置
        tree-routing-enabled: true   # 启用树形路由（优先转发给根子服务器）
        max-hops: 5                  # 请求最大跳数（防环）
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `tree-routing-enabled` | boolean | `false` | 是否启用树形路由。启用后协调器优先转发给根子服务器，并在请求中携带路由元数据防止环路 |
| `max-hops` | int | `5` | 请求最大跳数限制。超过此跳数的请求将被拒绝 |

### 层级路由策略

启用树形路由后，`RequestCoordinator` 采用以下层级路由策略：

```
协调器（Coordinator）
  │  1. 优先本地执行
  │  2. treeRoutingEnabled=true → 优先选择根子服务器（isRoot()）
  │     并按中继能力排序（relayEnabled=true 优先）
  │  3. 携带路由元数据：sourceNodeId / visitedNodes / maxHops
  ▼
根子服务器（Node-A，relayEnabled=true）
  │  本地无插件 → RemotePluginServer.executeWithRelay 中继转发
  ▼
孙节点（Node-A1，relayEnabled=false）
  │  本地执行成功 → 返回结果
  ▼
结果沿原路返回：Node-A1 → Node-A → Coordinator
```

1. **根节点优先**：树形模式下协调器优先将请求转发给根子服务器（`parentId` 为空的节点），由根子服务器决定本地执行还是中继转发。
2. **中继能力排序**：候选子服务器按 `relayEnabled` 排序，具备中继能力的节点优先，确保请求能继续向更深层级转发。
3. **路由元数据**：转发时在 `ExecuteRequest` 中设置 `sourceNodeId`（协调器节点 ID）、`visitedNodes`（已访问节点列表，初始包含协调器自身）、`maxHops`（最大跳数），防止环路。
4. **向后兼容**：`tree-routing-enabled` 默认 `false`，未启用时协调器行为与扁平轮询模式完全一致，直接轮询所有在线子服务器。

### 树形注册

通过 `RemoteExecutionDispatcher.registerChild` 或 `SubServerRegistry.registerChild` 注册树形层级关系，自动维护父子关系与节点深度：

```java
// 注册根节点（parentId=null）
dispatcher.registerChild("node-root", "192.168.1.10", 9700, null);

// 注册子节点（parentId=node-root）
dispatcher.registerChild("node-A", "192.168.1.11", 9701, "node-root");
dispatcher.registerChild("node-B", "192.168.1.12", 9702, "node-root");

// 注册孙节点（parentId=node-A）
dispatcher.registerChild("node-A1", "192.168.1.13", 9703, "node-A");

// 查看树形结构
System.out.println(dispatcher.dumpTree());
```
