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

详细文档请参考 `PROJECT_SUMMARY.md` 第 11 章。
