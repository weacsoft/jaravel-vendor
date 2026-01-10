# Jaravel Auth Module

参考Laravel认证系统实现的Java认证模块，支持多种认证方式（Guard）。

## 核心特性

1. **Authenticatable接口** - 用户模型实现此接口即可支持认证
2. **Guard机制** - 支持多种认证方式（SessionGuard等）
3. **全局Auth门面类** - 提供类似Laravel的Auth.user()等静态方法
4. **中间件支持** - Authenticate和Guest中间件
5. **Spring Boot自动装配** - 开箱即用

## 快速开始

### 1. 实现用户模型

```java
import com.weacsoft.jaravel.auth.Authenticatable;

public class User implements Authenticatable {
    private Long id;
    private String username;
    private String password;
    private String rememberToken;

    @Override
    public Object getAuthIdentifier() {
        return id;
    }

    @Override
    public String getAuthIdentifierName() {
        return "id";
    }

    @Override
    public String getAuthPassword() {
        return password;
    }

    @Override
    public String getRememberToken() {
        return rememberToken;
    }

    @Override
    public void setRememberToken(String token) {
        this.rememberToken = token;
    }

    @Override
    public String getRememberTokenName() {
        return "remember_token";
    }
}
```

### 2. 配置用户提供者

```java
@Configuration
public class AuthConfig {

    @Bean
    public UserProvider userProvider() {
        EloquentUserProvider provider = new EloquentUserProvider();
        
        User user1 = new User(1L, "admin", "password123", "admin@example.com");
        User user2 = new User(2L, "user", "password456", "user@example.com");
        
        provider.addUser(user1);
        provider.addUser(user2);
        
        return provider;
    }
}
```

### 3. 使用Auth门面类

```java
import com.weacsoft.jaravel.auth.Auth;

// 登录
Auth.login(user);

// 获取当前用户
Authenticatable user = Auth.user();

// 检查是否已认证
if (Auth.check()) {
    // 用户已登录
}

// 检查是否为访客
if (Auth.guest()) {
    // 用户未登录
}

// 尝试登录
boolean success = Auth.attempt(new Object[]{"admin", "password123"});

// 登出
Auth.logout();

// 使用特定的Guard
Authenticatable user = Auth.user("api");
Auth.login(user, "api");
```

### 4. 使用中间件

```java
import com.weacsoft.jaravel.auth.middleware.Authenticate;
import com.weacsoft.jaravel.auth.middleware.Guest;

// 需要认证的路由
router.get("/dashboard", request -> {
    return ResponseBuilder.json(Auth.user());
}).middleware(new Authenticate());

// 需要访客的路由
router.get("/login", request -> {
    return ResponseBuilder.view("login", Map.of());
}).middleware(new Guest());

// 使用特定的Guard
router.get("/api/profile", request -> {
    return ResponseBuilder.json(Auth.user("api"));
}).middleware(new Authenticate("api"));
```

## API文档

### Auth门面类

| 方法 | 说明 |
|------|------|
| `Auth.user()` | 获取当前认证用户 |
| `Auth.user(String guard)` | 获取指定Guard的认证用户 |
| `Auth.check()` | 检查是否已认证 |
| `Auth.check(String guard)` | 检查指定Guard是否已认证 |
| `Auth.guest()` | 检查是否为访客 |
| `Auth.guest(String guard)` | 检查指定Guard是否为访客 |
| `Auth.login(Authenticatable user)` | 登录用户 |
| `Auth.login(Authenticatable user, String guard)` | 使用指定Guard登录用户 |
| `Auth.logout()` | 登出当前用户 |
| `Auth.logout(String guard)` | 登出指定Guard的用户 |
| `Auth.attempt(Object[] credentials)` | 尝试使用凭证登录 |
| `Auth.attempt(Object[] credentials, String guard)` | 使用指定Guard尝试登录 |
| `Auth.once(Object[] credentials)` | 一次性验证（不保存Session） |
| `Auth.validate(Authenticatable user)` | 验证用户凭证 |

### 中间件

| 中间件 | 说明 |
|--------|------|
| `Authenticate` | 需要认证，未认证返回401 |
| `Authenticate(String guard)` | 需要指定Guard的认证 |
| `Guest` | 需要访客，已认证重定向到首页 |
| `Guest(String guard)` | 检查指定Guard是否为访客 |

### 自定义Guard

```java
Auth.extend("custom", name -> {
    return new CustomGuard(name, userProvider, request);
});

Auth.setDefaultGuard("custom");
```

## 架构说明

### 核心组件

- **Authenticatable**: 用户模型接口，定义认证所需的方法
- **Guard**: 认证守卫接口，定义认证行为
- **UserProvider**: 用户提供者接口，负责用户数据的获取和验证
- **AuthManager**: 认证管理器，管理多个Guard实例
- **Auth**: 全局门面类，提供静态方法访问认证功能

### SessionGuard

基于Session的认证实现，将用户ID存储在Session中：
- Session key: `{guard}_id`
- 默认Guard: `web`

### 扩展性

1. **自定义UserProvider**: 实现UserProvider接口，支持数据库、缓存等数据源
2. **自定义Guard**: 实现Guard接口，支持Token、OAuth等认证方式
3. **自定义中间件**: 实现Middleware接口，添加自定义认证逻辑

## 注意事项

1. 确保在Spring Boot应用中引入`springboot-starter`依赖
2. 用户模型需要实现Authenticatable接口
3. SessionGuard依赖Request的Session功能
4. 密码验证需要自行实现加密逻辑（如BCrypt）
