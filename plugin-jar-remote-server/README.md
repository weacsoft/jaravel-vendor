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
