# event AI-API Reference

> Module: `event` | Package: `com.weacsoft.jaravel.vendor.event` | Version: 0.1.1

## Overview
event 模块提供 Laravel 风格的事件系统，包含 Event（事件标记接口）、Listener（监听器函数式接口）、Dispatcher（事件调度器契约）、EventDispatcher（默认实现，支持同步/异步分发）、QueueManager（多队列管理器，每队列独立线程池）、QueueDispatcher（持久化队列分发器契约）、ShouldQueue（标记异步执行）、@ListensTo（自动注册注解）、EventServiceProvider（服务提供者基类）和 EventFacade（门面）。支持自动重试、延迟执行、多队列隔离和持久化队列降级。

## Classes & Interfaces

### Event
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件标记接口，对齐 Laravel `Event`。所有业务事件类只需实现本接口即可被分发和监听。

#### Usage Example

```java
public record UserRegistered(Long userId, String email) implements Event {
}
```

---

### Listener
- **Type**: interface (functional)
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 监听器接口，对齐 Laravel `Listener`。泛型 T 表示该监听器能处理的事件类型。
- **Annotations**: `@FunctionalInterface`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `handle` | `T event` | `void` | 处理事件 |

#### Usage Example
```java
public class SendWelcomeMail implements Listener<UserRegistered> {
    @Override
    public void handle(UserRegistered event) {
        mailService.send(event.email, "欢迎注册");
    }
}

// Lambda 方式
Listener<UserRegistered> logger = event -> {
    System.out.println("用户注册: " + event.userId);
};
```

---

### Dispatcher
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件调度器契约，对齐 Laravel `Illuminate\Contracts\Events\Dispatcher`。维护事件类型到监听器列表的映射，提供注册、分发、查询和清理能力。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `listen` | `Class<? extends Event> eventClass, Listener<? extends Event> listener` | `void` | 注册监听器到指定事件类型 |
| `dispatch` | `Event event` | `void` | 分发事件，触发所有对应监听器 |
| `getListeners` | `Class<T> eventClass` | `<T extends Event> List<Listener<T>>` | 获取指定事件类型的监听器列表 |
| `clearListeners` | `Class<? extends Event> eventClass` | `void` | 清除指定事件类型的全部监听器 |
| `clearAllListeners` | 无 | `void` | 清除所有事件类型的全部监听器 |

---

### EventDispatcher
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件调度器默认实现，对齐 Laravel `Illuminate\Events\Dispatcher`。使用 ConcurrentHashMap + CopyOnWriteArrayList 保证线程安全。分发时采用 per-listener 队列决策：实现 ShouldQueue 的监听器异步执行，未实现的同步执行。支持自动重试和持久化队列降级。
- **Annotations**: `@Component`
- **Implements**: `Dispatcher`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `EventDispatcher` | `QueueManager queueManager` | 构造方法 | 创建事件调度器 |
| `listen` | `Class<? extends Event>, Listener<? extends Event>` | `void` | 注册监听器 |
| `dispatch` | `Event event` | `void` | 分发事件（按 per-listener 决策同步/异步） |
| `getListeners` | `Class<T> eventClass` | `List<Listener<T>>` | 获取监听器列表 |
| `clearListeners` | `Class<? extends Event> eventClass` | `void` | 清除指定事件监听器 |
| `clearAllListeners` | 无 | `void` | 清除所有监听器 |
| `setQueueDispatcher` | `QueueDispatcher queueDispatcher` | `void` | 设置持久化队列分发器 |
| `setQueueEnabled` | `boolean queueEnabled` | `void` | 设置全局异步开关 |
| `isQueueEnabled` | 无 | `boolean` | 是否启用异步分发 |
| `getQueueManager` | 无 | `QueueManager` | 获取队列管理器 |

#### Usage Example
```java
// 注册监听器
dispatcher.listen(UserRegistered.class, event -> {
    mailService.send(event.email);
});

// 分发事件
dispatcher.dispatch(new UserRegistered(1L, "alice@example.com"));

// 查询
List<Listener<UserRegistered>> listeners = dispatcher.getListeners(UserRegistered.class);
```

---

### ShouldQueue
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 标记监听器应通过队列异步执行，对齐 Laravel `ShouldQueue`。实现此接口的监听器将被异步分发。

#### Methods (default)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `queue` | 无 | `String` | 队列名称，默认 "default" |
| `delay` | 无 | `long` | 延迟执行毫秒数，默认 0 |

#### Usage Example
```java
@Component
@ListensTo(UserRegistered.class)
public class WelcomeMailListener implements Listener<UserRegistered>, ShouldQueue {
    @Override
    public void handle(UserRegistered event) {
        mailService.send(event.email);
    }
    @Override
    public String queue() { return "email"; }  // 使用 email 队列
    @Override
    public long delay() { return 5000; }       // 延迟 5 秒执行
}
```

---

### @ListensTo
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 监听器绑定注解，对齐 Laravel `EventServiceProvider::$listen` 数组。标注在 Listener Bean 上，声明监听的事件类型。EventListenerRegistrar 会在所有单例就绪后自动扫描注册。
- **Target**: `ElementType.TYPE`
- **Retention**: `RetentionPolicy.RUNTIME`

#### Elements

| Element | Type | Description |
|---------|------|-------------|
| `value` | `Class<? extends Event>` | 该监听器监听的事件类型 |

#### Usage Example
```java
@Component
@ListensTo(UserRegistered.class)
public class RegistrationLogListener implements Listener<UserRegistered> {
    @Override
    public void handle(UserRegistered event) {
        logService.log("用户注册: " + event.userId);
    }
}
// 自动注册，无需手动调用 dispatcher.listen()
```

---

### QueueManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 队列管理器，对齐 Laravel 队列配置与多队列能力。每个命名队列拥有独立线程池，不同队列互不阻塞。队列按需创建。支持延迟任务调度和优雅关闭。

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `DEFAULT_QUEUE` | `String` | 默认队列名 "default" |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `QueueManager` | `EventProperties properties` | 构造方法 | 创建队列管理器 |
| `getOrCreateExecutor` | `String queueName` | `ExecutorService` | 获取或创建队列执行器 |
| `submit` | `String queueName, Runnable task` | `void` | 提交任务到队列立即执行 |
| `schedule` | `String queueName, Runnable task, long delayMs` | `void` | 延迟提交任务到队列 |
| `getRetryMaxAttempts` | 无 | `int` | 获取最大重试次数 |
| `getRetryDelayMs` | 无 | `long` | 获取重试间隔毫秒 |
| `shutdown` | 无 | `void` | 优雅关闭所有队列（@PreDestroy） |

---

### QueueDispatcher
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 队列分发器接口，对齐 Laravel `Illuminate\Contracts\Queue\Queue`。抽象异步事件的持久化队列分发后端。当可用时，EventDispatcher 优先使用 QueueDispatcher 将事件分发到持久化队列（如数据库队列），不可用时降级为内存队列。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `dispatch` | `String queueName, Object listener, Event event, long delayMs` | `void` | 将事件分发到指定队列 |
| `isAvailable` | 无 | `boolean` | 队列分发器是否可用（default true） |

---

### EventServiceProvider
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件服务提供者基类，对齐 Laravel `EventServiceProvider`。子类在 register() 中调用 listen() 注册事件-监听器映射。
- **Extends**: `com.weacsoft.jaravel.vendor.core.provider.ServiceProvider`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `listen` | `Class<T> eventClass, Listener<T> listener` | `void` | 注册监听器（protected，委托给 Dispatcher） |

#### Usage Example
```java
@Component
public class AppEventServiceProvider extends EventServiceProvider {
    @Override
    public void register() {
        listen(UserRegistered.class, new WelcomeMailListener());
        listen(UserRegistered.class, new RegistrationLogListener());
        listen(OrderPaid.class, new UpdateInventoryListener());
    }
}
```

---

### EventFacade
- **Type**: class (final)
- **Package**: `com.weacsoft.jaravel.vendor.event.facade`
- **Description**: 事件门面，对齐 Laravel `Event::`。所有方法为静态方法，通过 Facade 解析 Dispatcher。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `dispatch` | `Event event` | `void` | 分发事件 |
| `listen` | `Class<T> eventClass, Listener<T> listener` | `void` | 注册监听器 |
| `clearListeners` | `Class<? extends Event> eventClass` | `void` | 清除指定事件监听器 |
| `clearAllListeners` | 无 | `void` | 清除所有监听器 |

#### Usage Example
```java
// 分发事件
EventFacade.dispatch(new UserRegistered(1L, "alice@example.com"));

// 注册监听器
EventFacade.listen(UserRegistered.class, event -> {
    mailService.send(event.email);
});

// 清除
EventFacade.clearListeners(UserRegistered.class);
```

---

### EventListenerRegistrar
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件监听器自动注册器，对齐 Laravel `EventServiceProvider::boot()`。在所有单例就绪后扫描容器中所有标注 @ListensTo 的 Listener Bean，自动注册到 Dispatcher。
- **Annotations**: `@Component`
- **Implements**: `org.springframework.beans.factory.SmartInitializingSingleton`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `afterSingletonsInstantiated` | 无 | `void` | 所有单例就绪后自动扫描注册（由框架调用） |

---

### EventAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件模块自动装配，注册 QueueManager、EventDispatcher 和 EventListenerRegistrar。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(Dispatcher.class)`, `@EnableConfigurationProperties(EventProperties.class)`

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `queueManager` | `EventProperties properties` | `QueueManager` | 队列管理器（@Bean, @ConditionalOnMissingBean） |
| `eventDispatcher` | `QueueManager, EventProperties, ObjectProvider<QueueDispatcher>` | `EventDispatcher` | 事件调度器（@Bean, @ConditionalOnMissingBean） |
| `eventListenerRegistrar` | `Dispatcher, ApplicationContext` | `EventListenerRegistrar` | 监听器自动注册器（@Bean, @ConditionalOnMissingBean） |

---

### EventProperties
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.event`
- **Description**: 事件模块配置属性，前缀 `jaravel.event`，对齐 Laravel `config/event.php` 与 `config/queue.php`。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.event")`

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `queueEnabled` | `boolean` | `false` | 是否启用异步队列分发 |
| `queue.defaultPoolSize` | `Integer` | `null`(CPU 核心数) | 默认队列线程池大小 |
| `queue.queuePoolSizes` | `Map<String, Integer>` | `{}` | 各命名队列线程池大小覆盖 |
| `retry.maxAttempts` | `int` | `3` | 最大重试次数（不含首次执行） |
| `retry.delayMs` | `long` | `1000` | 重试间隔毫秒 |

#### Usage Example
```yaml
# application.yml
jaravel:
  event:
    queue-enabled: true
    queue:
      default:
        pool-size: 4
      email:
        pool-size: 2
    retry:
      max-attempts: 5
      delay-ms: 2000
```
