# Jaravel

基于 Spring Boot 3 的轻量级 Web 开发框架，提供优雅的请求处理、响应构建和路由管理功能。

## 简介

Jaravel 是一个受 Laravel 启发的 Java Web 框架，为 Spring Boot 应用提供简洁的 API 设计，让开发者能够更优雅地处理 HTTP 请求和响应。

## 核心特性

- **统一的请求处理** - 简化获取请求参数、文件、头信息等操作
- **灵活的响应构建** - 支持自定义状态码、响应头和内容
- **优雅的路由定义** - 链式调用定义路由，支持 RESTful 风格
- **中间件机制** - 支持请求预处理和后处理
- **路由分组** - 支持命名空间、前缀和名称配置
- **Spring Boot 自动配置** - 开箱即用，零配置启动

## 模块说明

### request
提供统一的 HTTP 请求处理类，支持：
- Query 参数获取
- Input 参数获取（表单/JSON）
- 文件上传处理
- Header 信息读取
- Cookie 管理
- Session 操作

### response
提供 HTTP 响应接口和构建器，支持：
- 自定义状态码
- 响应头设置
- 响应内容构建

### route
提供路由和控制器功能，支持：
- RESTful 路由定义（GET/POST/PUT/DELETE/PATCH）
- 路由分组
- 中间件链式调用
- 命名空间和前缀管理

### springboot-starter
Spring Boot 自动配置模块，一键集成所有功能。

### jblade
JBlade 模板引擎，受 Laravel Blade 启发的 Java 模板引擎，支持：
- 模板继承（@extends, @section, @yield）
- 条件判断（@if, @elseif, @else, @endif）
- 循环（@for, @foreach）
- 组件系统（@component, @endcomponent, @slot, @endslot）
- 变量输出（{{ $var }}）
- 注释（{{-- 注释 --}}）
- 动态编译和内存缓存

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加 JitPack 仓库：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

添加依赖：

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>springboot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

### 2. 定义路由

创建一个配置类来定义路由：

```java
import com.weacsoft.jaravel.controller.Controller;
import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.http.response.ResponseBuilder;
import com.weacsoft.jaravel.route.Router;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public Router router() {
        Router router = new Router();

        // 定义 GET 路由
        router.get("/hello", request -> {
            String name = request.query("name", "World");
            return ResponseBuilder.ok();
        });

        // 定义 POST 路由
        router.post("/user", request -> {
            String username = request.input("username");
            String email = request.input("email");
            // 处理用户创建逻辑
            return ResponseBuilder.ok();
        });

        return router;
    }
}
```

### 3. 使用中间件

```java
import com.weacsoft.jaravel.middleware.Middleware;

public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next) {
        String token = request.header("Authorization");
        if (token == null || token.isEmpty()) {
            return ResponseBuilder.unauthorized();
        }
        // 验证 token 逻辑
        return next.apply(request);
    }
}
```

在路由中使用中间件：

```java
router.get("/api/profile", request -> {
    // 处理逻辑
    return ResponseBuilder.ok();
}).middleware(new AuthMiddleware());
```

### 4. 路由分组

```java
Map<String, String> groupParams = Map.of(
    "prefix", "api",
    "namespace", "api"
);

router.group(groupParams, apiRouter -> {
    apiRouter.get("/users", request -> {
        // GET /api/users
        return ResponseBuilder.ok();
    });

    apiRouter.post("/users", request -> {
        // POST /api/users
        return ResponseBuilder.ok();
    });
});
```

## API 文档

### JBlade 模板引擎

#### 基础用法

```java
import com.weacsoft.jaravel.jblade.BladeEngine;
import java.util.HashMap;
import java.util.Map;

// 创建引擎
BladeEngine engine = new BladeEngine("templates");

// 准备数据
Map<String, Object> data = new HashMap<>();
data.put("name", "World");
data.put("items", Arrays.asList("Apple", "Banana", "Orange"));

// 渲染模板
String result = engine.render("welcome", data);
System.out.println(result);
```

#### 变量输出

```jblade
<h1>Hello, {{ $name }}!</h1>
<p>Count: {{ $items.size() }}</p>
```

#### 条件判断

```jblade
@if ($user)
    <p>Welcome, {{ $user.name }}!</p>
@else
    <p>Please login.</p>
@endif
```

#### 循环

```jblade
<ul>
    @foreach ($items as $item)
        <li>{{ $item }}</li>
    @endforeach
</ul>
```

#### 模板继承

**父模板 (layouts/app.jblade):**
```jblade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'Default Title')</title>
</head>
<body>
    @yield('content')
</body>
</html>
```

**子模板:**
```jblade
@extends('layouts.app')

@section('title', 'My Page')

@section('content')
    <h1>Welcome</h1>
    <p>This is the content.</p>
@endsection
```

#### 组件系统

**定义组件 (components/alert.jblade):**
```jblade
<div class="alert alert-{{ $type }}">
    @if ($title)
        <h4>{{ $title }}</h4>
    @endif
    
    @if ($slot)
        <p>{{ $slot }}</p>
    @endif
</div>
```

**使用组件:**
```jblade
<!-- 基本使用 -->
@component('alert', ['type' => 'success'])
    操作成功！
@endcomponent

<!-- 带标题 -->
@component('alert', ['type' => 'warning', 'title' => '警告'])
    请注意此操作
@endcomponent

<!-- 使用插槽 -->
@component('card', ['title' => '我的卡片'])
    @slot('header')
        <span>卡片头部</span>
    @endslot
    
    <p>卡片内容</p>
    
    @slot('footer')
        <button>确定</button>
    @endslot
@endcomponent
```

**组件变量说明:**
- `$slot` - 默认插槽内容
- `$header`, `$footer` 等 - 命名插槽内容
- `$type`, `$title` 等 - 组件参数

**嵌套组件:**
```jblade
@component('card', ['title' => '嵌套示例'])
    @slot('header')
        <h3>重要通知</h3>
    @endslot
    
    <p>以下是警告信息：</p>
    
    @component('alert', ['type' => 'danger'])
        这是嵌套在卡片中的警告
    @endcomponent
@endcomponent
```

#### 注释

```jblade
{{-- 这是一个注释，不会输出到HTML --}}
```

#### 完整示例

**模板文件 (templates/welcome.jblade):**
```jblade
<!DOCTYPE html>
<html>
<head>
    <title>JBlade Demo</title>
</head>
<body>
    <h1>Hello, {{ $name }}!</h1>
    
    @if ($items)
        <ul>
            @foreach ($items as $item)
                <li>{{ $item }}</li>
            @endforeach
        </ul>
    @endif
    
    @component('alert', ['type' => 'info'])
        Welcome to JBlade!
    @endcomponent
</body>
</html>
```

**Java 代码:**
```java
BladeEngine engine = new BladeEngine("templates");
Map<String, Object> data = new HashMap<>();
data.put("name", "Jaravel");
data.put("items", Arrays.asList("Feature 1", "Feature 2", "Feature 3"));
String html = engine.render("welcome", data);
```

#### 运行测试

JBlade 提供了完整的组件功能测试示例：

```bash
# 编译项目
mvn clean compile

# 运行组件测试
java -cp "target/classes;..." ComponentTest
```

测试模板位于 `jblade/templates/` 目录：
- `component_test.jblade` - 组件功能测试主模板
- `alert.jblade` - 警告组件
- `card.jblade` - 卡片组件
- `list.jblade` - 列表组件

测试内容包括：
1. 基本组件使用
2. 带标题的组件
3. 使用插槽
4. 自定义卡片组件
5. 嵌套组件
6. 列表组件

### Request API

#### 获取参数

```java
// 获取所有参数（query + input）
Map<String, Object> all = request.all();

// 获取指定参数（优先从 input 获取）
String name = request.get("name");
String name = request.get("name", "default");
Integer age = request.get("age", Integer.class);

// 获取多个值
List<Object> names = request.gets("name");
```

#### Query 参数

```java
// 获取所有 query 参数
Map<String, Object> query = request.query();

// 获取指定 query 参数
String name = request.query("name");
String name = request.query("name", "default");
Integer page = request.query("page", Integer.class);

// 获取多个值
List<Object> tags = request.queries("tags");
```

#### Input 参数

```java
// 获取所有 input 参数
Map<String, Object> input = request.input();

// 获取指定 input 参数
String username = request.input("username");
String username = request.input("username", "guest");
Integer age = request.input("age", Integer.class);

// 获取多个值
List<Object> hobbies = request.inputs("hobbies");
```

#### 文件上传

```java
// 获取所有文件
Map<String, Object> files = request.file();

// 获取指定文件
MultipartFile avatar = request.file("avatar");

// 获取多个文件
List<MultipartFile> documents = request.files("documents");
```

#### Header 信息

```java
// 获取所有 header
Map<String, Object> headers = request.header();

// 获取指定 header
String contentType = request.header("Content-Type");
String token = request.header("Authorization", "");

// 获取多个值
List<Object> values = request.headers("X-Custom-Header");
```

#### Cookie

```java
// 获取所有 cookie
Map<String, Object> cookies = request.cookie();

// 获取指定 cookie
String sessionId = request.cookie("JSESSIONID");
String theme = request.cookie("theme", "light");

// 获取多个值
List<Object> values = request.cookies("preferences");
```

#### Session

```java
// 获取所有 session
Map<String, Object> session = request.session();

// 获取指定 session
String userId = request.session("userId");
String role = request.session("role", "guest");

// 获取多个值
List<Object> permissions = request.sessions("permissions");
```

#### 检查参数是否存在

```java
boolean hasName = request.has("name");
boolean hasFile = request.hasFile("avatar");
boolean hasHeader = request.hasHeader("Authorization");
boolean hasCookie = request.hasCookie("JSESSIONID");
boolean hasSession = request.hasSession("userId");
```

### Response API

#### 使用 ResponseBuilder

```java
// 成功响应
Response response = ResponseBuilder.ok();

// 自定义响应
Response response = new Response() {
    @Override
    public int getStatus() {
        return 200;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return Map.of("Content-Type", List.of("application/json"));
    }

    @Override
    public String getContent() {
        return "{\"message\":\"success\"}";
    }
};
```

### Router API

#### 定义路由

```java
// GET 请求
Route route = router.get("/path", request -> {
    return ResponseBuilder.ok();
});

// POST 请求
Route route = router.post("/path", request -> {
    return ResponseBuilder.ok();
});

// PUT 请求
Route route = router.put("/path", request -> {
    return ResponseBuilder.ok();
});

// DELETE 请求
Route route = router.delete("/path", request -> {
    return ResponseBuilder.ok();
});

// PATCH 请求
Route route = router.patch("/path", request -> {
    return ResponseBuilder.ok();
});

// 所有 HTTP 方法
Router groupRouter = router.all("/path", request -> {
    return ResponseBuilder.ok();
});
```

#### 路由配置

```java
// 设置路由名称
route.name("user.profile");

// 设置路由前缀
route.prefix("v1");

// 添加中间件
route.middleware(new AuthMiddleware(), new LogMiddleware());
```

#### 路由分组

```java
Map<String, String> params = Map.of(
    "prefix", "api/v1",
    "namespace", "api.v1",
    "name", "api"
);

router.group(params, group -> {
    group.get("/users", request -> {
        // 完整路径: /api/v1/users
        return ResponseBuilder.ok();
    }).name("users.index");

    group.post("/users", request -> {
        // 完整路径: /api/v1/users
        return ResponseBuilder.ok();
    }).name("users.store");
});
```

### Middleware API

#### 创建中间件

```java
public class LogMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next) {
        // 请求前处理
        System.out.println("Request: " + request.getRequest().getRequestURI());

        // 调用下一个中间件或处理器
        Response response = next.apply(request);

        // 响应后处理
        System.out.println("Response status: " + response.getStatus());

        return response;
    }
}
```

#### 使用中间件

```java
// 单个中间件
router.get("/protected", request -> {
    return ResponseBuilder.ok();
}).middleware(new AuthMiddleware());

// 多个中间件
router.post("/data", request -> {
    return ResponseBuilder.ok();
}).middleware(new AuthMiddleware(), new LogMiddleware(), new ValidationMiddleware());

// 路由组中间件
router.group(Map.of("prefix", "api"), api -> {
    api.middleware(new AuthMiddleware());
    api.get("/users", request -> ResponseBuilder.ok());
    api.post("/users", request -> ResponseBuilder.ok());
});
```

## 技术栈

- Java 8+
- Spring Boot 3.x
- Spring Web MVC
- Jakarta Servlet API
- Lombok
- Hutool JSON

## 版本历史

### 0.0.1 (当前版本)
- 初始版本发布
- 支持请求处理
- 支持响应构建
- 支持路由定义
- 支持中间件机制
- 支持 Spring Boot 自动配置

## 许可证

本项目采用开源许可证，具体请参考项目文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 GitHub Issues 联系。
