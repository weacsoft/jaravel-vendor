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

详细文档请参考 `PROJECT_SUMMARY.md` 第 11 章。
