# Jaravel

Jaravel 是一个轻量级的 Java Web 开发框架，提供了类似 Laravel 的开发体验，包含路由、中间件、请求处理、响应构建、事件监听等核心功能。

## 核心模块

### 1. request - HTTP 请求处理

- **Request** - 请求对象包装，提供参数获取、Cookie 操作等功能
- **RequestFactory** - 请求对象工厂，用于创建 Request 实例

### 2. response - HTTP 响应构建

- **Response** - 响应接口，定义响应基本操作
- **ResponseBuilder** - 响应构建器，提供多种响应类型创建
- **JSONResponseResolver** - JSON 响应解析器

### 3. route - 路由和中间件

- **Route** - 路由定义和处理
- **Router** - 路由器，管理路由注册和匹配
- **RouteService** - 路由服务，处理路由分发
- **Controller** - 控制器基类
- **Middleware** - 中间件接口及实现
    - **EncryptCookies** - Cookie 加密中间件
    - **TrimStrings** - 字符串修剪中间件
    - **TrustProxies** - 代理信任中间件
    - **VerifyCsrfToken** - CSRF 令牌验证中间件

### 4. listener - 事件监听系统

- **Event** - 事件标记接口
- **Listener** - 监听器接口
- **ListenerService** - 事件监听服务，支持同步和异步执行

### 5. jblade - 模板引擎

- **BladeEngine** - Blade 模板引擎
- **BladeCompiler** - 模板编译器
- **BladeTemplate** - 模板对象
- **BladeContext** - 模板上下文

### 6. util - 工具类

- **StringUtils** - 字符串工具类
- **ExpiryMap** - 带过期时间的 Map 实现
- **memory** - 内存编译相关工具类

### 7. springboot-starter - Spring Boot 集成

- **SpringBootRouteAutoConfiguration** - 路由自动配置
- **SpringBootRequestMVCResolver** - 请求解析器
- **SpringBootResponseMVCResolver** - 响应解析器
- **ListenerAutoConfiguration** - 事件监听自动配置
- **ListenerProperties** - 事件监听配置属性

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>springboot-starter</artifactId>
    <version>0.0.5</version>
</dependency>
```

### 2. 配置路由

```java
@Configuration
public class RouteConfig {
    
    @Autowired
    private RouteService routeService;
    
    @PostConstruct
    public void registerRoutes() {
        routeService.get("/", (request, response) -> {
            return response.ok("Hello, Jaravel!");
        });
        
        routeService.post("/user", (request, response) -> {
            String name = request.get("name");
            return response.json(Map.of("name", name, "message", "User created"));
        });
    }
}
```

### 3. 使用事件系统

```java
// 定义事件
public class UserRegisteredEvent implements Event {
    private String username;
    
    public UserRegisteredEvent(String username) {
        this.username = username;
    }
    
    public String getUsername() {
        return username;
    }
}

// 定义监听器
public class SendWelcomeEmailListener implements Listener<UserRegisteredEvent> {
    @Override
    public void handle(UserRegisteredEvent event) {
        System.out.println("Sending welcome email to: " + event.getUsername());
    }
}

// 注册监听器
@Autowired
private ListenerService listenerService;

@PostConstruct
public void registerListeners() {
    listenerService.listen(UserRegisteredEvent.class, new SendWelcomeEmailListener());
}

// 触发事件
listenerService.dispatch(new UserRegisteredEvent("john"));
```

### 4. 配置属性

在 `application.yml` 中配置：

```yaml
jaravel:
  listener:
    queue-enabled: true  # 启用异步事件处理
```

## 技术特点

- **轻量级** - 核心功能模块化，按需引入
- **Spring Boot 集成** - 提供自动配置，开箱即用
- **类似 Laravel 的 API** - 熟悉的开发体验
- **事件系统** - 支持同步和异步事件处理
- **模板引擎** - 内置 Blade 模板引擎
- **中间件支持** - 灵活的中间件机制
- **线程安全** - 核心组件线程安全设计

## 项目结构

```
├── request/           # HTTP 请求处理
├── response/          # HTTP 响应构建
├── route/             # 路由和中间件
├── listener/          # 事件监听系统
├── jblade/            # 模板引擎
├── util/              # 工具类
└── springboot-starter # Spring Boot 集成
```

## 构建和测试

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test
```

## 许可证

MIT License