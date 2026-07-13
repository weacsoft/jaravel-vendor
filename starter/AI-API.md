# starter AI-API Reference

> Module: `starter` | Package: `com.weacsoft.jaravel.vendor.springboot` | Version: 0.1.2

## Overview

starter 模块是 Jaravel 框架的聚合 Starter，提供一键引入所有模块的自动装配能力。通过 `JaravelAutoConfiguration` 使用 `@Import` 注解将各模块的 AutoConfiguration 类聚合在一起，业务方只需在 `pom.xml` 中引入 `jaravel-starter` 依赖，即可自动装配框架核心（Core、Database、Cache、Session、Auth、Router、Event）和所有 vendor 扩展模块（Redis 缓存、Redis 会话、Artisan、Schedule、Queue、SpringBoot 桥接、微信 SDK 等）。各模块的装配均带有条件注解，仅当对应依赖存在时才生效。

## Classes & Interfaces

### JaravelAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.springboot`
- **Annotations**: `@AutoConfiguration`, `@Import({ CoreAutoConfiguration, DatabaseAutoConfiguration, CacheAutoConfiguration, RedisCacheAutoConfiguration, SessionAutoConfiguration, SessionRedisAutoConfiguration, AuthAutoConfiguration, RouterAutoConfiguration, EventAutoConfiguration, ArtisanAutoConfiguration, ScheduleAutoConfiguration, QueueDatabaseAutoConfiguration, SpringBootRouteAutoConfiguration, ResponseAutoConfiguration, WechatAutoConfiguration })`
- **Description**: Jaravel 框架聚合自动装配入口。通过 `@Import` 将所有模块的 AutoConfiguration 类集中导入，实现一键引入。各被导入的 AutoConfiguration 类自身带有 `@ConditionalOnClass`、`@ConditionalOnBean` 等条件注解，仅当对应依赖和前置 Bean 存在时才实际装配，因此引入 starter 不会因缺少某个可选依赖而启动失败。

#### Methods

该类本身不定义任何方法，仅作为 `@Import` 聚合入口。所有功能由被导入的各模块 AutoConfiguration 提供。

#### 被聚合的模块

| 模块 | AutoConfiguration 类 | 装配条件 |
|------|----------------------|----------|
| core | `CoreAutoConfiguration` | 无条件（框架核心） |
| database | `DatabaseAutoConfiguration` | `DataSource` 存在 |
| cache | `CacheAutoConfiguration` | 无条件 |
| redis-cache | `RedisCacheAutoConfiguration` | `RedisManager` + `CacheManager` 存在 |
| session | `SessionAutoConfiguration` | 无条件 |
| session-redis | `SessionRedisAutoConfiguration` | `RedisManager` + `SessionManager` 存在 |
| auth | `AuthAutoConfiguration` | 无条件 |
| router | `RouterAutoConfiguration` | Web 应用环境 |
| event | `EventAutoConfiguration` | 无条件 |
| artisan | `ArtisanAutoConfiguration` | `jaravel.artisan.enabled=true` |
| schedule | `ScheduleAutoConfiguration` | `jaravel.schedule.enabled=true` |
| queue-database | `QueueDatabaseAutoConfiguration` | `JdbcTemplate` 存在 |
| springboot | `SpringBootRouteAutoConfiguration` | Web 应用环境 |
| springboot | `ResponseAutoConfiguration` | Web 应用环境 |
| wechat-sdk | `WechatAutoConfiguration` | `jaravel.wechat.enabled=true` |

#### Usage Example
```xml
<!-- pom.xml: 只需引入 starter，所有模块自动可用 -->
<dependency>
    <groupId>com.weacsoft.jaravel</groupId>
    <artifactId>jaravel-starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

```yaml
# application.yml: 按需配置各模块
jaravel:
  cache:
    default-store: redis
    redis:
      connection: cache
  session:
    redis:
      connection: session
      ttl: 7200
  artisan:
    enabled: true
  schedule:
    enabled: true
    pool-size: 4
  queue:
    database:
      enabled: true
  wechat:
    enabled: true
    mp-app-id: wx1234567890abcdef
    mp-secret: your-secret
```

```java
// 引入 starter 后，所有 Facade 可直接使用
// 无需额外 @EnableXxx 注解
@RestController
public class DemoController {

    @GetMapping("/demo")
    public Object demo() {
        Cache::put("key", "value", 60)
        Session::put("user_id", 1);
        Queue::push("SendEmailJob", emailData);
        return "ok";  // 自动包装为统一响应格式
    }
```
