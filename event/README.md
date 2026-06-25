# event 模块

> Jaravel-Vendor 的事件模块，提供 Laravel 风格的 Event/Listener 契约、同步与异步事件分发、`ShouldQueue` per-listener 队列决策、`QueueManager` 多队列线程池管理、`@ListensTo` 注解自动注册、`EventFacade` 静态门面。包名统一为 `com.weacsoft.jaravel.vendor.event`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. Event —— 事件标记接口](#4-event--事件标记接口)
- [5. Listener —— 监听器接口](#5-listener--监听器接口)
- [6. ShouldQueue —— 队列化标记接口](#6-shouldqueue--队列化标记接口)
- [7. Dispatcher —— 事件调度器契约](#7-dispatcher--事件调度器契约)
- [8. EventDispatcher —— 事件调度器实现](#8-eventdispatcher--事件调度器实现)
- [9. QueueManager —— 队列管理器](#9-queuemanager--队列管理器)
- [10. QueueDispatcher —— 队列分发器接口](#10-queuedispatcher--队列分发器接口)
- [11. ListensTo —— 监听器绑定注解](#11-listensto--监听器绑定注解)
- [12. EventListenerRegistrar —— 自动注册器](#12-eventlistenerregistrar--自动注册器)
- [13. EventServiceProvider —— 事件服务提供者基类](#13-eventserviceprovider--事件服务提供者基类)
- [14. EventFacade —— 事件门面](#14-eventfacade--事件门面)
- [15. 示例：用户注册事件](#15-示例用户注册事件)
- [16. EventAutoConfiguration —— 自动装配](#16-eventautoconfiguration--自动装配)
- [17. 配置选项](#17-配置选项)
- [18. 线程安全说明](#18-线程安全说明)

---

## 1. 模块概述

`event` 模块对齐 Laravel 的事件系统，核心特性如下：

| Laravel 特性 | event 对应实现 | 说明 |
| --- | --- | --- |
| `Event` 基类 | `Event` 接口 | 事件标记接口 |
| `Listener` | `Listener<T>` 接口 | 监听器接口（函数式） |
| `ShouldQueue` | `ShouldQueue` 接口 | 标记监听器异步执行，支持 `queue()` 与 `delay()` |
| `Illuminate\Events\Dispatcher` | `Dispatcher` / `EventDispatcher` | 事件调度器，同步/异步分发 |
| 队列配置 | `QueueManager` | 每队列独立线程池，可配置大小与重试 |
| 持久化队列 | `QueueDispatcher` | 持久化队列分发器接口，可用时优先于内存队列（如数据库队列） |
| `EventServiceProvider::$listen` | `EventServiceProvider` / `@ListensTo` | 监听器注册（编程式或注解式） |
| `Event::` 门面 | `EventFacade` | 静态 API |
| `config/event.php` / `config/queue.php` | `EventProperties` | `jaravel.event.*` 配置 |

### 核心机制

- **per-listener 队列决策**：实现了 `ShouldQueue` 的监听器异步执行，`queue()` 指定队列名，`delay()` 指定延迟毫秒数
- **多队列能力**：每个命名队列拥有独立的线程池，不同队列的监听器互不阻塞
- **重试机制**：监听器执行抛出异常时，按配置自动重试（可配置最大重试次数与重试间隔）
- **全局开关**：`queueEnabled` 对未实现 `ShouldQueue` 的监听器生效，但 per-listener 的 `ShouldQueue` 决策优先

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>event</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `com.weacsoft:core` | `Facade` 基础设施、`ServiceProvider` 基类 |
| `org.springframework.boot:spring-boot-autoconfigure` | 自动装配 |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Spring 6.x）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.event
├── Event                        // 事件标记接口
├── Listener                     // 监听器接口（函数式，泛型 T extends Event）
├── ShouldQueue                  // 队列化标记接口（queue() / delay()）
├── Dispatcher                   // 事件调度器契约（接口）
├── EventDispatcher              // 事件调度器实现（同步/异步分发 + 重试）
├── QueueManager                 // 队列管理器（每队列独立线程池 + 重试配置）
├── QueueDispatcher              // 队列分发器接口（持久化队列抽象，如数据库队列）
├── ListensTo                    // 监听器绑定注解（@ListensTo(EventClass.class)）
├── EventListenerRegistrar       // 监听器自动注册器（扫描 @ListensTo）
├── EventServiceProvider         // 事件服务提供者基类（编程式注册）
├── EventProperties              // 配置属性（jaravel.event.*）
├── EventAutoConfiguration       // 自动装配
├── facade
│   └── EventFacade              // 事件门面（Event.listen() / Event.dispatch()）
└── example
    ├── UserRegisteredEvent      // 示例事件
    ├── LogRegistrationListener  // 示例同步监听器
    └── SendWelcomeEmailListener // 示例异步监听器（ShouldQueue）
```

---

## 4. Event —— 事件标记接口

`com.weacsoft.jaravel.vendor.event.Event`

对齐 Laravel `Event`。所有业务事件类只需实现本接口，即可被 `Dispatcher` 分发、被 `Listener` 监听。

### 使用示例

```java
public record UserRegisteredEvent(Long userId, String name) implements Event {
}
```

---

## 5. Listener —— 监听器接口

`com.weacsoft.jaravel.vendor.event.Listener`

对齐 Laravel `Listener`。函数式接口，泛型 `T` 表示该监听器能处理的事件类型。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void handle(T event)` | 处理事件 |

### 使用示例

```java
public class SendWelcomeMail implements Listener<UserRegisteredEvent> {
    @Override
    public void handle(UserRegisteredEvent event) {
        mailService.send(event.getUserId());
    }
}
```

也可使用 Lambda 表达式：

```java
EventFacade.listen(UserRegisteredEvent.class, event -> {
    System.out.println("用户注册: " + event.getName());
});
```

---

## 6. ShouldQueue —— 队列化标记接口

`com.weacsoft.jaravel.vendor.event.ShouldQueue`

对齐 Laravel 的 `ShouldQueue`。实现此接口的监听器将被异步分发到 `QueueManager` 管理的命名队列中执行，未实现的将同步执行。

### 方法文档

| 方法签名 | 默认值 | 说明 |
| --- | --- | --- |
| `default String queue()` | `"default"` | 队列名称 |
| `default long delay()` | `0` | 延迟执行毫秒数（0 表示立即执行） |

### 使用示例

```java
public class SendWelcomeEmailListener implements Listener<UserRegisteredEvent>, ShouldQueue {

    @Override
    public void handle(UserRegisteredEvent event) {
        // 发送欢迎邮件（异步执行）
        mailService.sendWelcome(event.getUserId());
    }

    @Override
    public String queue() {
        return "email";    // 使用 "email" 队列
    }

    @Override
    public long delay() {
        return 5000;       // 延迟 5 秒执行
    }
}
```

---

## 7. Dispatcher —— 事件调度器契约

`com.weacsoft.jaravel.vendor.event.Dispatcher`

对齐 Laravel `Illuminate\Contracts\Events\Dispatcher`。负责维护「事件类型 -> 监听器列表」的映射。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void listen(Class<? extends Event> eventClass, Listener<? extends Event> listener)` | 注册监听器到指定事件类型 |
| `void dispatch(Event event)` | 分发事件，依次触发该事件类型对应的所有监听器 |
| `<T extends Event> List<Listener<T>> getListeners(Class<T> eventClass)` | 获取指定事件类型已注册的监听器列表 |
| `void clearListeners(Class<? extends Event> eventClass)` | 清除指定事件类型的全部监听器 |
| `void clearAllListeners()` | 清除所有事件类型的全部监听器 |

---

## 8. EventDispatcher —— 事件调度器实现

`com.weacsoft.jaravel.vendor.event.EventDispatcher`

对齐 Laravel `Illuminate\Events\Dispatcher`。标注 `@Component`，使用 `ConcurrentHashMap` + `CopyOnWriteArrayList` 维护事件与监听器的映射。

### 分发决策流程

```
dispatch(event)
    │
    ▼
遍历该事件类型的所有监听器
    │
    ├── 监听器实现 ShouldQueue？
    │       ├── 是 -> 异步分发到 queue() 指定的队列，支持 delay() 延迟
    │       └── 否 -> 检查全局 queueEnabled 开关
    │               ├── true  -> 异步分发到 default 队列
    │               └── false -> 同步执行（含重试）
    │
    ▼
invokeWithRetry(listener, event)
    │
    ├── 执行成功 -> 返回
    └── 抛出异常 -> 按 retryMaxAttempts 重试
            ├── 重试成功 -> 返回
            └── 重试耗尽 -> 记录错误日志，不中断其它监听器
```

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void listen(Class<? extends Event>, Listener<? extends Event>)` | 注册监听器 |
| `void dispatch(Event event)` | 分发事件（按上述决策流程） |
| `<T extends Event> List<Listener<T>> getListeners(Class<T>)` | 获取监听器列表 |
| `void clearListeners(Class<? extends Event>)` | 清除指定事件监听器 |
| `void clearAllListeners()` | 清除全部监听器 |
| `void setQueueEnabled(boolean)` | 设置全局异步开关 |
| `boolean isQueueEnabled()` | 是否启用异步队列分发 |
| `void setQueueDispatcher(QueueDispatcher)` | 注入持久化队列分发器，注入后优先于 `QueueManager` |
| `QueueManager getQueueManager()` | 获取队列管理器 |

### 使用示例

```java
@Autowired
private Dispatcher dispatcher;

// 注册监听器
dispatcher.listen(UserRegisteredEvent.class, new LogRegistrationListener());
dispatcher.listen(UserRegisteredEvent.class, new SendWelcomeEmailListener());

// 分发事件
dispatcher.dispatch(new UserRegisteredEvent(1L, "Alice"));
// LogRegistrationListener 同步执行
// SendWelcomeEmailListener 异步执行到 "email" 队列
```

---

## 9. QueueManager —— 队列管理器

`com.weacsoft.jaravel.vendor.event.QueueManager`

对齐 Laravel 的队列配置与多队列能力。每个命名队列拥有独立的线程池，不同队列的监听器互不阻塞。队列按需创建：首次向某队列提交任务时才创建对应大小的线程池。

### 常量

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `DEFAULT_QUEUE` | `"default"` | 默认队列名 |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `ExecutorService getOrCreateExecutor(String queueName)` | 获取或创建指定队列的执行器（`computeIfAbsent` 保证单例） |
| `void submit(String queueName, Runnable task)` | 提交任务到指定队列立即执行 |
| `void schedule(String queueName, Runnable task, long delayMs)` | 延迟提交任务到指定队列 |
| `int getRetryMaxAttempts()` | 获取最大重试次数（不含首次执行） |
| `long getRetryDelayMs()` | 获取重试间隔毫秒 |
| `void shutdown()` | 优雅关闭所有队列执行器与调度器（`@PreDestroy`） |

### 线程池创建逻辑

```
getOrCreateExecutor(queueName)
    │
    ▼
executors.computeIfAbsent(queueName, this::createExecutor)
    │
    ▼
createExecutor(queueName):
    poolSize = queuePoolSizes.getOrDefault(queueName, defaultPoolSize)
    return Executors.newFixedThreadPool(poolSize, DaemonThreadFactory)
```

- 默认线程池大小：配置优先，否则使用 CPU 核心数
- 各队列线程池大小覆盖：`queue.<name>.pool-size`
- 所有线程为**守护线程**（`setDaemon(true)`），避免异步事件任务阻止 JVM 退出
- 延迟任务通过单独的调度线程池（2 个线程）统一管理

### 优雅关闭

`shutdown()` 方法标注 `@PreDestroy`，在容器销毁时调用：

1. 先 `shutdown()` 调度器与所有队列执行器
2. 最多等待 5 秒
3. 超时则执行 `shutdownNow()` 强制关闭

### 使用示例

```java
@Autowired
private QueueManager queueManager;

// 提交任务到默认队列
queueManager.submit("default", () -> System.out.println("Hello"));

// 提交任务到 email 队列
queueManager.submit("email", () -> sendEmail());

// 延迟 5 秒提交
queueManager.schedule("email", () -> sendEmail(), 5000);
```

---

## 10. QueueDispatcher —— 队列分发器接口

`com.weacsoft.jaravel.vendor.event.QueueDispatcher`

对齐 Laravel `Illuminate\Contracts\Queue\Queue`。持久化队列分发器接口，抽象异步事件的队列分发后端。当可用时，`EventDispatcher` 优先使用 `QueueDispatcher` 将事件分发到持久化队列（如数据库队列），实现多实例消费和任务持久化；不可用时降级为内存队列（`QueueManager`）。

### 设计动机

`QueueManager` 提供的是**内存队列**——任务存在于 JVM 进程内，进程重启后丢失。`QueueDispatcher` 抽象了**持久化队列**后端，使事件任务可以被持久化到数据库、Redis 等外部存储，支持：

- **多实例消费**：多个应用实例从同一队列抢占任务
- **任务持久化**：进程重启后未处理的任务不丢失
- **延迟执行**：任务可延迟到指定时间后执行

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `void dispatch(String queueName, Object listener, Event event, long delayMs)` | 将事件分发到指定队列，`delayMs` 为 0 表示立即执行 |
| `default boolean isAvailable()` | 队列分发器是否可用（已连接到后端），默认返回 `true` |

### 分发决策流程

```
EventDispatcher.dispatch(event)
    │
    ├── 监听器实现 ShouldQueue？
    │       ├── 是 -> 异步分发
    │       │       ├── QueueDispatcher 可用？ -> queueDispatcher.dispatch(queue, listener, event, delay)
    │       │       │                             （持久化到数据库/Redis，多实例消费）
    │       │       └── 不可用 -> queueManager.submit/schedule(queue, task, delay)
    │       │                     （内存队列，进程内执行）
    │       └── 否 -> 同步执行（含重试）
```

### 实现方

- `queue-database` 模块提供 `DatabaseQueueDriver` 实现（通过 `DatabaseQueueDispatcher` 适配）
- 业务方也可自定义实现，通过 Spring bean 注入自动生效

### 使用示例

业务方通常无需直接调用 `QueueDispatcher`，`EventDispatcher` 会自动选择。若需自定义实现：

```java
@Component
public class MyQueueDispatcher implements QueueDispatcher {
    @Override
    public void dispatch(String queueName, Object listener, Event event, long delayMs) {
        // 将监听器与事件序列化后推送到自定义队列后端
        String payload = serialize(listener, event);
        myQueueBackend.push(queueName, payload, delayMs);
    }

    @Override
    public boolean isAvailable() {
        return myQueueBackend.isConnected();
    }
}
```

实现注册为 Spring bean 后，`EventDispatcher` 会通过 `setQueueDispatcher()` 自动注入并优先使用。

---

## 11. ListensTo —— 监听器绑定注解

`com.weacsoft.jaravel.vendor.event.ListensTo`

对齐 Laravel 中「在 `EventServiceProvider::$listen` 数组里声明监听器对应事件」的用法。标注在实现了 `Listener` 的 Bean 类上，声明该监听器所监听的事件类型。

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `value` | `Class<? extends Event>` | 该监听器监听的事件类型 |

### 使用示例

```java
@Component
@ListensTo(UserRegisteredEvent.class)
public class SendWelcomeMail implements Listener<UserRegisteredEvent> {
    @Override
    public void handle(UserRegisteredEvent event) {
        mailService.send(event.getUserId());
    }
}
```

`EventListenerRegistrar` 会在所有单例就绪后扫描带本注解的监听器 Bean，自动将其注册到 `Dispatcher`。

---

## 12. EventListenerRegistrar —— 自动注册器

`com.weacsoft.jaravel.vendor.event.EventListenerRegistrar`

对齐 Laravel `EventServiceProvider::boot()` 中批量注册监听器的行为。标注 `@Component`，实现 `SmartInitializingSingleton`。

### 工作流程

```
所有单例 Bean 实例化完成
        │
        ▼
afterSingletonsInstantiated()
        │
        ▼
applicationContext.getBeansOfType(Listener.class)
        │
        ▼
遍历每个 Listener Bean:
    检查是否标注 @ListensTo 注解？
        ├── 是 -> dispatcher.listen(anno.value(), bean)
        └── 否 -> 跳过
```

采用构造器注入，确保 `Dispatcher` 与 `ApplicationContext` 在注册前已就绪。

---

## 13. EventServiceProvider —— 事件服务提供者基类

`com.weacsoft.jaravel.vendor.event.EventServiceProvider`

对齐 Laravel 的 `EventServiceProvider`。子类在 `register()` 中调用 `listen()` 注册事件-监听器映射。继承自 `core` 模块的 `ServiceProvider`。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `protected <T extends Event> void listen(Class<T> eventClass, Listener<T> listener)` | 注册监听器，委托给 `Dispatcher.listen()` |

`dispatcher` 由 Spring 容器自动注入（`@Autowired`），`ProviderRegistry` 会在所有单例就绪后调用 `register()`。

### 使用示例

```java
@Component
public class AppEventServiceProvider extends EventServiceProvider {

    @Override
    public void register() {
        listen(UserRegisteredEvent.class, new SendWelcomeEmailListener());
        listen(UserRegisteredEvent.class, new LogRegistrationListener());
    }
}
```

---

## 14. EventFacade —— 事件门面

`com.weacsoft.jaravel.vendor.event.facade.EventFacade`

对齐 Laravel `Event::` 静态调用。`final` 工具类，不可实例化。通过 `Facade.resolve(Dispatcher.class)` 从 Spring 容器解析 `Dispatcher`。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `static void dispatch(Event event)` | 分发事件 |
| `static <T extends Event> void listen(Class<T> eventClass, Listener<T> listener)` | 注册监听器 |
| `static void clearListeners(Class<? extends Event> eventClass)` | 清除指定事件监听器 |
| `static void clearAllListeners()` | 清除全部监听器 |

### 使用示例

```java
// 注册监听器
EventFacade.listen(UserRegisteredEvent.class, event -> {
    System.out.println("用户注册: " + event.getName());
});

// 分发事件
EventFacade.dispatch(new UserRegisteredEvent(1L, "Alice"));

// 清除监听器
EventFacade.clearListeners(UserRegisteredEvent.class);
EventFacade.clearAllListeners();
```

---

## 15. 示例：用户注册事件

模块内置了完整的示例代码，位于 `com.weacsoft.jaravel.vendor.event.example` 包。

### 15.1 UserRegisteredEvent —— 示例事件

```java
public class UserRegisteredEvent implements Event {
    private final Long userId;
    private final String name;

    public UserRegisteredEvent(Long userId, String name) { ... }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
}
```

### 15.2 LogRegistrationListener —— 同步监听器

未实现 `ShouldQueue`，因此会被**同步执行**，对齐 Laravel 中普通的同步监听器。

```java
public class LogRegistrationListener implements Listener<UserRegisteredEvent> {
    @Override
    public void handle(UserRegisteredEvent event) {
        log.info("[同步] 记录用户注册日志: {} ({})", event.getName(), event.getUserId());
    }
}
```

### 15.3 SendWelcomeEmailListener —— 异步监听器

实现 `ShouldQueue`，因此会被**异步分发**到 `"email"` 队列执行，对齐 Laravel 中实现 `ShouldQueue` 的队列化监听器。

```java
public class SendWelcomeEmailListener implements Listener<UserRegisteredEvent>, ShouldQueue {
    @Override
    public void handle(UserRegisteredEvent event) {
        log.info("[队列] 发送欢迎邮件给用户 {} ({})", event.getName(), event.getUserId());
    }

    @Override
    public String queue() {
        return "email";    // 使用 "email" 队列
    }
}
```

### 15.4 完整使用流程

```java
// 1. 注册监听器（编程式或注解式）
EventFacade.listen(UserRegisteredEvent.class, new LogRegistrationListener());
EventFacade.listen(UserRegisteredEvent.class, new SendWelcomeEmailListener());

// 2. 分发事件
EventFacade.dispatch(new UserRegisteredEvent(1L, "Alice"));

// 执行结果：
// [同步] 记录用户注册日志: Alice (1)           <- 同步立即执行
// [队列] 发送欢迎邮件给用户 Alice (1)          <- 异步在 "email" 队列执行
```

---

## 16. EventAutoConfiguration —— 自动装配

`com.weacsoft.jaravel.vendor.event.EventAutoConfiguration`

Spring Boot 自动装配类，注册以下 Bean：

| Bean | 类型 | 说明 |
| --- | --- | --- |
| `queueManager` | `QueueManager` | 队列管理器，按配置初始化线程池大小与重试参数 |
| `eventDispatcher` | `EventDispatcher`（作为 `Dispatcher` 契约实现） | 事件调度器，注入 QueueManager 并初始化异步开关。当容器中存在 `QueueDispatcher` bean 时自动注入，启用持久化队列分发 |
| `eventListenerRegistrar` | `EventListenerRegistrar` | 监听器自动注册器，扫描 `@ListensTo` 注解 |

所有 Bean 均带 `@ConditionalOnMissingBean`，允许业务方自定义替换。

---

## 17. 配置选项

配置前缀为 `jaravel.event`，对应 `EventProperties` 类。

```yaml
jaravel:
  event:
    queue-enabled: true                  # 启用异步队列分发（默认 false）
    queue:
      default:
        pool-size: 4                     # 默认队列线程池大小（默认 CPU 核心数）
      email:
        pool-size: 2                     # "email" 队列线程池大小
    retry:
      max-attempts: 3                    # 最大重试次数（默认 3，不含首次执行）
      delay-ms: 1000                     # 重试间隔毫秒（默认 1000）
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.event.queue-enabled` | `boolean` | `false` | 是否启用异步队列分发（全局开关） |
| `jaravel.event.queue.default.pool-size` | `Integer` | `null`（CPU 核心数） | 默认队列线程池大小 |
| `jaravel.event.queue.<name>.pool-size` | `Integer` | 无 | 指定队列的线程池大小覆盖 |
| `jaravel.event.retry.max-attempts` | `int` | `3` | 最大重试次数（不含首次执行） |
| `jaravel.event.retry.delay-ms` | `long` | `1000` | 重试间隔毫秒 |

### 配置说明

- `queue-enabled` 是全局开关，对**未实现 `ShouldQueue`** 的监听器生效。实现 `ShouldQueue` 的监听器始终异步执行，不受此开关影响。
- `queue.default.pool-size` 未配置时使用 `Runtime.getRuntime().availableProcessors()`（CPU 核心数）。
- `queue.<name>.pool-size` 中 `<name>` 对应 `ShouldQueue.queue()` 返回值，如 `email`、`sms` 等。
- `retry.max-attempts` 不含首次执行，即总执行次数 = 1 + max-attempts。

---

## 18. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `EventDispatcher` | 线程安全 | 使用 `ConcurrentHashMap` + `CopyOnWriteArrayList` 维护事件-监听器映射。`queueEnabled` 为 `volatile`，保证可见性。并发注册与分发安全 |
| `QueueManager` | 线程安全 | 使用 `ConcurrentHashMap` 维护队列名到执行器的映射，`computeIfAbsent` 保证每队列只创建一个执行器。`queuePoolSizes` 为 `ConcurrentHashMap`，构造后只读 |
| `EventFacade` | 线程安全 | 静态方法，每次调用通过 `Facade.resolve()` 从容器解析 `Dispatcher`，无共享可变状态 |
| `EventListenerRegistrar` | 单次执行 | `afterSingletonsInstantiated()` 在所有单例就绪后单线程调用，无需考虑并发 |
| `EventServiceProvider` | 单次执行 | `register()` 在引导阶段单线程调用 |
| `Event` / `Listener` / `ShouldQueue` | 取决于实现 | 接口/标记接口，线程安全性取决于具体实现类。建议监听器实现为无状态对象 |
| `EventProperties` | 配置只读 | Spring Boot 配置属性绑定，启动后只读 |

### 重试机制的线程安全

`invokeWithRetry` 方法无共享可变状态，`listener` 与 `event` 由调用方保证可见性（通过线程池提交时的 happens-before 关系）。重试通过 `Thread.sleep` 实现，在队列线程池的工作线程中阻塞等待，不影响其它队列。所有重试耗尽后仅记录错误日志，不会中断其它监听器的执行。
