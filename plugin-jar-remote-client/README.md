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
