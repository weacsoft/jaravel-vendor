# jblade 模块

> Jaravel-Vendor 的模板引擎模块，提供 Laravel Blade 风格的模板编译与渲染。支持 `{{ }}` 输出、`@if/@foreach/@for/@while` 控制结构、`@extends/@section/@yield` 模板继承、`@component` 组件等特性，通过内存编译（`MemoryClassLoader`）将模板编译为 Java 类后执行。支持预编译模式：开发阶段将模板预编译为字节码（打包文件或散乱 class），生产环境仅需 JRE 即可运行。包名统一为 `com.weacsoft.jaravel.vendor.jblade`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. BladeEngine —— 模板引擎](#4-bladeengine--模板引擎)
  - [4.1 模板缓存机制](#41-模板缓存机制)
- [5. BladeCompiler —— 模板编译器](#5-bladecompiler--模板编译器)
  - [5.1 表达式编译引擎](#51-表达式编译引擎)
- [6. BladeTemplate —— 模板基类](#6-bladetemplate--模板基类)
  - [6.1 PHP 辅助函数](#61-php-辅助函数)
- [7. BladeContext —— 执行上下文](#7-bladecontext--执行上下文)
- [8. 内存编译机制](#8-内存编译机制)
- [9. 工具类](#9-工具类)
- [10. 支持的指令](#10-支持的指令)
- [11. 使用示例](#11-使用示例)
- [12. 线程安全说明](#12-线程安全说明)
- [13. 静态资源 URL 生成（@asset）](#13-静态资源-url-生成asset)
- [14. 预编译功能](#14-预编译功能)
  - [14.1 设计理念](#141-设计理念)
  - [14.2 两种编译模式](#142-两种编译模式)
  - [14.3 BladePrecompiler —— 预编译工具](#143-bladeprecompiler--预编译工具)
  - [14.4 PrecompiledTemplateLoader —— 预编译模板加载器](#144-precompiledtemplateloader--预编译模板加载器)
  - [14.5 BladePrecompilerMain —— 命令行工具](#145-bladeprecompilermain--命令行工具)
  - [14.6 JRE-only 运行示例](#146-jre-only-运行示例)
- [15. 内置辅助函数与中间件联动（CSRF / route）](#15-内置辅助函数与中间件联动csrf--route)
  - [15.1 CSRF 防护：csrf_field() / csrf_token() / @csrf](#151-csrf-防护csrf_field--csrf_token--csrf)
  - [15.2 route() 与 url()：按路由名 / 按路径生成 URL](#152-route-与-url按路由名-按路径生成-url)
  - [15.3 开箱即用与“零注册”保证](#153-开箱即用与零注册保证)
- [16. 自定义扩展：注册 Blade 函数与指令](#16-自定义扩展注册-blade-函数与指令)
  - [16.1 注册自定义 Blade 函数（BladeFunctions）](#161-注册自定义-blade-函数bladefunctions)
  - [16.2 注册自定义指令（BladeDirectives）](#162-注册自定义指令bladedirectives)
  - [16.3 在 Jaravel（Spring Boot）中注册](#163-在-jaravelspring-boot中注册)
  - [16.4 内置函数一览与“不要重复注册”注意](#164-内置函数一览与不要重复注册注意)

---

## 1. 模块概述

`jblade` 模块对齐 Laravel 的 Blade 模板引擎，核心特性如下：

| Laravel 特性 | jblade 对应实现 | 说明 |
| --- | --- | --- |
| Blade 模板引擎 | `BladeEngine` | 模板引擎入口，构造时指定模板目录与后缀 |
| Blade 编译器 | `BladeCompiler` | 将 `@directives` 编译为 Java 源码并内存编译 |
| 编译后的模板 | `BladeTemplate` | 抽象基类，编译生成的类继承此类 |
| 模板变量上下文 | `BladeContext` | 变量、Section、组件等执行上下文 |
| `view()` 辅助函数 | `ResponseBuilder.view()` | HTTP 模块中的视图响应 |

### 缓存机制

`BladeEngine` 采用**两级缓存**避免每次渲染都重新编译模板（JavaC 编译开销较大）：

- **一级缓存（内存）**：`ConcurrentHashMap` 缓存编译后的 `Class<?>` 对象，进程内有效，始终启用。这是主缓存，解决"每用一次就编译一次"的核心问题。
- **二级缓存（可选）**：通过 cache 模块的 `CacheStore`（实例接口）缓存编译后的字节码（`byte[]`），支持跨进程/跨实例共享（如 Redis）。引入 cache 模块后自动启用，未引入时仅使用一级缓存。

> 关键修复：早期版本每次 `render()` 都会调用 `compiler.compile()`（含 JavaC 编译），现在仅在一二级缓存均未命中时才编译。详见 [4.1 模板缓存机制](#41-模板缓存机制)。

### 工作原理

jblade 支持两种运行模式：

- **运行时编译模式（默认）**：在运行时通过 `javax.tools.JavaCompiler` 将 `.blade.java` 模板编译为字节码并加载，需要完整的 JDK 环境。
- **预编译模式**：在开发阶段（有 JDK）使用 `BladePrecompiler` 将所有模板预编译为字节码，输出为打包文件（`.jblade.zip`）或散乱 `.class` 文件。生产环境（仅 JRE）通过 `BladeEngine.fromPrecompiledPackage()` 或 `BladeEngine.fromPrecompiledClasses()` 加载预编译产物，无需 JDK，无需运行时编译。详见 [第 14 节：预编译功能](#14-预编译功能)。

运行时编译模式的工作流程：

```
BladeEngine.render("users.list", variables)
        │
        ▼
loadTemplate("users.list")  -- 查一级缓存 → 查二级缓存 → 编译
        │
        ├── 1. 查一级缓存（ConcurrentHashMap），命中直接返回 Class
        ├── 2. 查二级缓存（CacheStore），命中则加载字节码
        ├── 3. 缓存未命中 → BladeCompiler.compile()
        │       ├── 读取模板文件（classpath: templateDir/users/list.blade.java）
        │       ├── 将 Blade 指令编译为 Java 源码
        │       └── 使用 javax.tools.JavaCompiler 内存编译
        ├── 4. 编译后字节码写入二级缓存、Class 写入一级缓存
        └── 5. 返回类全名
        │
        ▼
MemoryClassLoader.loadClass(className)
        │
        ▼
BladeTemplate 实例化 + 注入上下文变量
        │
        ▼
template.render() -> 输出 HTML 字符串
```

预编译模式的工作流程：

```
【开发阶段 — 需要 JDK】
BladePrecompiler / BladePrecompilerMain
        │
        ├── 1. 扫描模板目录下所有 .blade.java 文件
        ├── 2. BladeCompiler.compileSource() 编译每个模板为字节码
        ├── 3. PrecompiledTemplateLoader 保存产物
        │       ├── PACKAGED 模式 → .jblade.zip 打包文件
        │       └── CLASSES 模式 → 散乱 .class 文件到目录
        └── 4. 将预编译产物部署到生产环境

【生产环境 — 仅需 JRE】
BladeEngine.fromPrecompiledPackage("templates.jblade.zip")
        │
        ├── 1. PrecompiledTemplateLoader.loadFromPackage() 加载字节码
        ├── 2. 字节码注入 MemoryClassLoader
        └── 3. 返回 BladeEngine 实例

BladeEngine.render("users.list", variables)
        │
        ├── loadTemplate() 优先从已加载字节码获取 Class（无需编译）
        └── 实例化 + 渲染 → 输出 HTML 字符串
```

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>jblade</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `com.weacsoft:cache` | 可选依赖，提供 `CacheStore` 接口用于二级缓存（跨进程共享字节码，`optional = true`） |
| `org.springframework:spring-core` | `ClassPathResource` 读取 classpath 模板文件 |

> 运行环境要求：
> - **运行时编译模式**：JDK 17+（需使用 JDK 而非 JRE，因为依赖 `javax.tools.JavaCompiler`），Spring Boot 3.2.5（Spring 6.x）
> - **预编译模式**：生产环境仅需 JRE 17+（通过 `BladeEngine.fromPrecompiledPackage()` 或 `BladeEngine.fromPrecompiledClasses()` 加载预编译产物）；预编译阶段仍需 JDK

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor
├── jblade
│   ├── BladeEngine              // 模板引擎（入口，含 CompiledTemplateData 内部类，支持预编译加载）
│   ├── BladeCompiler            // 模板编译器（Blade -> Java 源码 -> 字节码，含表达式编译引擎）
│   ├── BladePrecompiler         // 预编译工具（开发阶段将模板预编译为字节码，含 CompileMode 枚举）
│   ├── PrecompiledTemplateLoader // 预编译模板加载器（从打包文件/目录加载字节码，含 PrecompiledBundle）
│   ├── BladePrecompilerMain     // 预编译命令行工具入口
│   ├── BladeTemplate            // 编译后模板的抽象基类（含 PHP 辅助函数）
│   └── BladeContext             // 执行上下文（变量/Section/组件）
└── utils
    ├── StringUtils              // 命名转换工具（驼峰/下划线/帕斯卡）
    └── memory
        ├── MemoryClassLoader    // 内存类加载器（从字节码加载类）
        ├── MemoryFileManager    // 内存文件管理器（捕获编译输出）
        ├── SourceCodeJavaFileObject  // 源代码文件对象（内存中的 .java）
        └── ClassFileJavaFileObject   // 字节码文件对象（内存中的 .class）
```

---

## 4. BladeEngine —— 模板引擎

`com.weacsoft.jaravel.vendor.jblade.BladeEngine`

模板引擎入口，负责加载、缓存、渲染模板。支持模板继承（`@extends`）与组件（`@component`）。采用两级缓存（一级内存 `ConcurrentHashMap` + 二级 `CacheStore`），仅在一二级缓存均未命中时才执行编译，避免重复编译开销。

### 4.0 视图标准层（View / Htmlable / Paginator 上提到 core）

为保证「用 database 不必依赖模板引擎」，`jblade` **仅作为 `core` 标准层的实现方**，而非契约提供方：

- `core.view.Htmlable` / `core.view.HtmlString`：免转义 HTML 值对象契约（`jblade` 不再重复定义）。
- `core.view.View`：视图渲染标准接口（`render` / `exists` / `name`）。**`BladeView` 实现的是 `core.view.View`**，框架（如 `Paginator.links()`）只依赖该标准接口，不依赖 `jblade`。
- `core.view.ViewManager`：视图管理者标准接口；`jblade.view.ViewManager` 实现它。
- `core.pagination.Paginator`：Laravel 风格分页器（标准层，不依赖 `jblade`）。

`jblade` 启动时通过 `ViewFacade.bind(manager)` 把默认 `View` 注入 `Paginator` 的 `ViewProvider`，
使 `database` 返回的 `core.pagination.Paginator` 能渲染分页模板；未引入 `jblade` 时 `links()` 降级为空串。

### 构造器

提供多种重载，最终委托到全参构造器：

| 构造器签名 | 说明 |
| --- | --- |
| `BladeEngine(String templateDir)` | 指定模板目录，默认后缀 `.blade.java`，无二级缓存 |
| `BladeEngine(String templateDir, String suffix)` | 指定模板目录与后缀 |
| `BladeEngine(String templateDir, CacheStore cacheStore)` | 指定模板目录与二级缓存 store，默认后缀 `.blade.java` |
| `BladeEngine(String templateDir, MemoryClassLoader classLoader)` | 指定模板目录与类加载器，默认后缀 `.blade.java` |
| `BladeEngine(String templateDir, String suffix, CacheStore cacheStore)` | 指定模板目录、后缀与二级缓存 store |
| `BladeEngine(String templateDir, CacheStore cacheStore, MemoryClassLoader classLoader)` | 指定模板目录、二级缓存 store 与类加载器，默认后缀 `.blade.java` |
| `BladeEngine(String templateDir, String suffix, CacheStore cacheStore, MemoryClassLoader classLoader)` | 全参构造器 |
| `static BladeEngine fromPrecompiledPackage(String packagePath)` | **工厂方法**：从预编译打包文件（`.jblade.zip`）创建引擎，仅需 JRE |
| `static BladeEngine fromPrecompiledClasses(String classesDir)` | **工厂方法**：从预编译 class 目录创建引擎，仅需 JRE |

> **缓存说明**：`cacheStore` 参数为 `CacheStore`（cache 模块的实例接口），可为 `null`。为 `null` 时仅使用一级内存缓存，不影响功能。引入 cache 模块后传入 `CacheStore` 实例即可启用二级缓存（跨进程共享字节码）。

> **后缀说明**：默认使用 `.blade.java` 后缀。采用该后缀可使常见 IDE（如 IntelliJ IDEA）将模板文件识别为 Java 相关文件，从而在模板内提供部分代码提示与语法高亮。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `String render(String templateName, Map<String, Object> variables)` | 渲染模板，注入变量，返回 HTML 字符串 |
| `String render(String templateName)` | 渲染模板（无变量） |
| `BladeTemplate loadTemplate(String templateName)` | 加载（编译 + 缓存）模板，返回 `BladeTemplate` 实例。内部按"查一级 → 查二级 → 编译 → 回填缓存"流程执行。**预编译模式**下优先从已加载字节码获取 `Class`，无需运行时编译 |
| `void clearCache()` | 清除所有缓存：一级缓存 + 二级缓存（按 key 逐个 `forget`，不调用 `flush()`）+ 模板实例缓存 |
| `void clearTemplate(String templateName)` | 清除指定模板的所有缓存（一级 Class + 二级 CacheStore + 实例），不影响其他模板 |
| `void clearTemplateInstanceCache()` | 仅清除模板实例缓存 |
| `MemoryClassLoader getMemoryClassLoader()` | 获取内存类加载器 |
| `CacheStore getCacheStore()` | 获取二级缓存 store（可能为 `null`） |
| `boolean isUseCacheStore()` | 是否启用了二级缓存（`CacheStore` 非 null 时为 true） |
| `int getTemplateInstanceCacheSize()` | 获取模板实例缓存大小 |
| `int getClassCacheSize()` | 获取一级缓存中的模板数量（`Class<?>` 缓存大小） |
| `String getSuffix()` | 获取模板文件后缀（默认 `.blade.java`） |

### 渲染流程

```
render(templateName, variables)
        │
        ▼
loadTemplate(templateName)
        │
        ├── 1. 查一级缓存（ConcurrentHashMap）—— 命中直接返回 Class
        ├── 2. 查二级缓存（CacheStore）—— 命中则加载字节码到 MemoryClassLoader
        ├── 3. 缓存未命中 → compiler.compile()  -- 编译模板，返回类全名
        ├── 4. 编译后字节码写入二级缓存、Class 写入一级缓存
        └── 5. 从实例缓存或反射创建 BladeTemplate 实例
        │
        ▼
template.resetContext()  -- 重置上下文
        │
        ▼
注入 variables 到 BladeContext
        │
        ▼
template.init()  -- 初始化（注册 Section 渲染器、解析 @extends）
        │
        ▼
检查是否有父模板（@extends）？
        ├── 是 -> 加载父模板，合并变量与 Section，调用父模板 render()
        └── 否 -> 调用当前模板 render()
        │
        ▼
返回 HTML 字符串
```

### 使用示例

```java
// 创建引擎（模板目录为 classpath 下的 templates，后缀 .blade.java）
BladeEngine engine = new BladeEngine("templates", ".blade.java");

// 渲染模板
Map<String, Object> vars = new HashMap<>();
vars.put("title", "用户列表");
vars.put("users", List.of("Alice", "Bob", "Charlie"));

String html = engine.render("users.list", vars);
System.out.println(html);
```

带缓存的引擎：

```java
// 使用 cache 模块的 CacheStore 缓存编译后的模板字节码（跨进程共享）
// 通过 CacheManager 按名称解析 store（如 redis、array）
CacheStore cacheStore = cacheManager.store("redis");
BladeEngine engine = new BladeEngine("templates", ".blade.java", cacheStore);

// 首次渲染会编译模板，后续渲染优先从一级缓存（内存 Class）加载；
// 进程重启后从二级缓存（CacheStore）加载字节码，避免重复编译
String html = engine.render("users.list", vars);

// 清除所有缓存（一级 + 二级 + 实例缓存），开发模式热更新
engine.clearCache();
```

> 若不传入 `CacheStore`，`BladeEngine` 仍会启用一级内存缓存（`ConcurrentHashMap`），仅无法跨进程共享字节码。

### 4.1 模板缓存机制

`BladeEngine` 采用两级缓存机制，避免每次渲染都重新编译模板（`BladeCompiler.compile()` 含 JavaC 编译，开销较大）。早期版本存在"每次 `render()` 都调用 `compile()`"的缺陷，现已修复：仅在一二级缓存均未命中时才编译。

#### 一级缓存（内存，始终启用）

- **存储结构**：`ConcurrentHashMap<String, Class<?>> templateClassCache`，模板名 → 编译后的 `Class<?>` 对象。
- **生命周期**：进程内有效，随 JVM 退出而失效。
- **用途**：解决"每用一次就编译一次"的核心问题。进程内重复渲染同一模板直接返回已加载的 `Class`，无任何编译开销。
- **查询方法**：`getClassCacheSize()` 返回一级缓存中的模板数量。

#### 二级缓存（可选，跨进程共享）

- **存储结构**：`CacheStore`（cache 模块的实例接口），缓存键为 `jblade:template:{templateName}`，值为 `CompiledTemplateData`（包含类名与字节码）。
- **生命周期**：由 `CacheStore` 实现决定（如 Redis 跨进程持久化、array 进程级）。
- **用途**：进程重启后从二级缓存加载字节码到 `MemoryClassLoader`，避免重新编译。引入 cache 模块并传入 `CacheStore` 实例后自动启用。
- **查询方法**：`getCacheStore()` 返回二级缓存 store（可能为 `null`）；`isUseCacheStore()` 判断是否启用。

#### 缓存流程

```
loadTemplate(templateName)
        │
        ▼
1. 查一级缓存（ConcurrentHashMap）
        ├── 命中 ──────────────────────────────┐
        │                                       │
        ▼ (未命中，加锁 double-checked)         │
2. 查二级缓存（CacheStore）                      │
        ├── 命中 -> 加载字节码到 MemoryClassLoader，得到 Class
        │                                       │
        ▼ (未命中)                              │
3. compiler.compile(templateName)  -- JavaC 编译，返回类全名
        │                                       │
        ▼                                       │
4. 从 MemoryClassLoader 加载 Class              │
        │                                       │
        ▼                                       │
5. 字节码写入二级缓存（CompiledTemplateData）     │
        │                                       │
        ▼                                       │
6. Class 写入一级缓存 <─────────────────────────┘
        │
        ▼
7. 从实例缓存或反射创建 BladeTemplate 实例并返回
```

> **降级策略**：二级缓存的读/写失败均会被捕获并降级（读失败重新编译，写失败不影响功能），确保缓存异常不会阻断渲染流程。

#### clearCache()

`clearCache()` 同时清除三类缓存：

1. **一级缓存**：`templateClassCache.clear()`
2. **二级缓存**：遍历已缓存的模板名，逐个调用 `cacheStore.forget(key)`（**不调用 `flush()`**，避免清空其他模块的缓存）
3. **模板实例缓存**：`templateInstanceCache` 重置并清空

仅清除模板实例缓存可使用 `clearTemplateInstanceCache()`。

清除单个模板缓存可使用 `clearTemplate(templateName)`，仅影响该模板，其他已编译模板不受影响。

#### CompiledTemplateData 内部类

`BladeEngine.CompiledTemplateData` 是二级缓存的序列化包装类，实现 `java.io.Serializable`，用于在 `CacheStore` 中存储编译后的模板数据。

| 字段/方法 | 类型 | 说明 |
| --- | --- | --- |
| `className` | `String`（字段） | 编译后的类全名 |
| `bytecode` | `byte[]`（字段） | 编译后的字节码 |
| `CompiledTemplateData(String className, byte[] bytecode)` | 构造器 | 创建包装对象 |
| `getClassName()` | `String` | 获取类全名 |
| `getBytecode()` | `byte[]` | 获取字节码 |

---

## 5. BladeCompiler —— 模板编译器

`com.weacsoft.jaravel.vendor.jblade.BladeCompiler`

将 Blade 模板编译为 Java 源码，再通过 `javax.tools.JavaCompiler` 内存编译为字节码。

### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `BladeCompiler(String templateDir, MemoryClassLoader classLoader)` | 默认后缀 `.blade.java` |
| `BladeCompiler(String templateDir, MemoryClassLoader classLoader, String suffix)` | 指定后缀 |

### 常量

| 常量 | 说明 |
| --- | --- |
| `String DEFAULT_SUFFIX` | 默认模板文件后缀，值为 `.blade.java` |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `String compile(String templateName)` | 编译模板，返回编译后的类全名 |
| `String compileSource(String templateName, String content)` | **新增**：直接编译模板内容（跳过文件读取），返回类全名。供 `BladePrecompiler` 预编译时使用 |
| `String compileExpression(String expr, Set<String> localVars)` | 编译 Blade 表达式为 Java 表达式（核心入口，按步骤依次处理字符串字面量、静态方法、对象方法/属性、数组访问、拼接、运算符等） |
| `String compileConditionExpression(String expr, Set<String> localVars)` | 编译条件表达式（`@if`/`@elseif`），布尔上下文，简单变量自动包装 `toBoolean()` |
| `String compileOutputExpression(String expr, Set<String> localVars)` | 编译输出表达式（`{{ }}`），值上下文，不包装 `toBoolean()` |
| `String compileStringLiterals(String expr)` | 将单引号字符串 `'text'` 编译为 Java 双引号 `"text"` |
| `String compileMethodCalls(String expr, Set<String> localVars)` | 将 `$var->method(args)` 编译为 `invokeMethod(...)` |
| `String compilePropertyAccess(String expr, Set<String> localVars)` | 将 `$var->prop` 编译为 `getProperty(...)` |
| `String compileMethodChainProperty(String expr)` | 将 `method()->prop` 编译为 `getProperty(method(), "prop")`，Carbon 特殊处理 `carbonToday()->year` |
| `String compileArrayAccess(String expr, Set<String> localVars)` | 将 `$var['key']` 编译为 `getMapValue(...)` |
| `String compileArrayLiterals(String expr, Set<String> localVars)` | 将 `['key' => value]` 编译为 `Map.of("key", value)` |
| `String compileStringConcatenation(String expr)` | 将 PHP 拼接运算符 `.` 编译为 Java `+` |
| `String compileNullCoalescing(String expr, Set<String> localVars)` | 将 `??` 编译为 `nullCoalescing(a, b)` |
| `String compileElvisOperator(String expr, Set<String> localVars)` | 将 `?:` 编译为 `elvis(a, b)` |
| `String compileTernaryOperator(String expr, Set<String> localVars)` | 将三元 `? :` 编译为 `toBoolean(cond) ? a : b` |
| `String compileVariables(String expr, Set<String> localVars)` | 将剩余 `$var` 编译为 `ctx.getVariable("var")` 或本地变量名 |
| `String getSuffix()` | 获取模板文件后缀（默认 `.blade.java`） |
| `MemoryClassLoader getClassLoader()` | **新增**：获取内部类加载器，供预编译工具提取编译后的字节码 |

### 编译流程

`compile(templateName)` 方法已重构，分离文件读取与编译逻辑：

```
compile(templateName)
        │
        ├── 1. 读取模板文件：classpath:templateDir/templateName(suffix)
        └── 2. 委托 compileSource(templateName, content)

compileSource(templateName, content)
        │
        ├── 1. generateClassName(templateName) -> "Blade_" + name（如 Blade_users_list）
        ├── 2. generateJavaCode(className, content) -> 生成 Java 源码
        ├── 3. 提取包名（从生成的源码中解析）
        ├── 4. 获取系统 JavaCompiler
        ├── 5. 创建 MemoryFileManager + SourceCodeJavaFileObject
        ├── 6. 执行编译任务（task.call()）
        ├── 7. 将编译后的字节码存入 MemoryClassLoader
        └── 8. 返回类全名
```

> `compileSource(templateName, content)` 可直接编译模板内容字符串，无需从文件系统读取。`BladePrecompiler` 预编译工具使用此方法批量编译模板。

### 生成的 Java 源码结构

编译器为每个模板生成一个继承 `BladeTemplate` 的 Java 类，包含：

```java
import com.weacsoft.jaravel.vendor.jblade.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

public class Blade_users_list extends BladeTemplate {

    // 每个 @section 生成一个 renderSection_xxx 方法
    private void renderSection_content(Writer writer) throws Exception {
        BladeContext ctx = getContext();
        // section 内容的编译代码
    }

    @Override
    public void init() {
        // 注册 Section 渲染器
        // 解析 @extends、@section 等指令
    }

    @Override
    public void render(Writer writer) throws Exception {
        BladeContext ctx = getContext();
        // 模板主体的编译代码
        // @yield -> 调用 Section 渲染器
        // {{ }} -> write(writer, expr)
        // @foreach -> for 循环
    }
}
```

### 正则模式

编译器使用以下正则表达式解析模板：

| 模式 | 用途 | 正则 |
| --- | --- | --- |
| `COMMENT_PATTERN` | 注释 `{{-- ... --}}` | `\{\{--.*?--\}\}` |
| `ECHO_PATTERN` | 输出 `{{ ... }}` | `\{\{\s*([^{}]+?)\s*\}\}` |
| `DIRECTIVE_PATTERN` | 指令 `@xxx(...)` | `@(\w+)\s*(?:\((.*?)\))?` |
| `VAR_PATTERN` | 变量 `$xxx` | `\$(\w+)` |

### 5.1 表达式编译引擎

jblade 的 `BladeCompiler` 原生支持 Blade 模板表达式语法，将其编译为等价的 Java 代码。这是 jblade 编译器的核心能力，不是外部转换层。`compileExpression` 方法按固定步骤依次处理各类语法：字符串字面量 → 静态方法调用 → 辅助函数 → 对象方法/属性 → 方法链属性 → 数组访问 → 关联数组字面量 → 字符串拼接 → 空合并 → Elvis → 三元 → 变量引用。

#### 支持的表达式语法

| Blade 表达式 | 编译目标 | 示例 |
| --- | --- | --- |
| 单引号字符串 `'text'` | Java 双引号 `"text"` | `'hello'` → `"hello"` |
| 静态方法调用 `URL::method()` | 方法调用 | `URL::asset('path')` → `asset("path")` |
| `Carbon::method()` | carbon 前缀方法 | `Carbon::parse($date)` → `carbonParse($date)` |
| 辅助函数 `csrf_field()` | 运行时 `csrf_field()`（隐藏 input） | `csrf_field()` → 调用运行时方法，返回 `<input type="hidden">` |
| 对象方法调用 `$var->method(args)` | `invokeMethod(...)` | `$item->getId()` → `invokeMethod(ctx.getVariable("item"), "getId")` |
| 对象属性访问 `$var->prop` | `getProperty(...)` | `$item->name` → `getProperty(ctx.getVariable("item"), "name")` |
| 方法链属性 `method()->prop` | `getProperty(method(), "prop")` | `carbonToday()->year` → `carbonYear(carbonToday())` |
| 数组访问 `$var['key']` | `getMapValue(...)` | `$item['image']` → `getMapValue(ctx.getVariable("item"), "image")` |
| 关联数组 `['key' => value]` | `Map.of("key", value)` | `['id' => $item->id]` → `Map.of("id", getProperty(...))` |
| 字符串拼接 `.` | Java `+` | `'a' . $b` → `"a" + ctx.getVariable("b")` |
| 空合并 `??` | `nullCoalescing(a, b)` | `$a ?? $b` → `nullCoalescing(...)` |
| Elvis `?:` | `elvis(a, b)` | `$a ?: $b` → `elvis(...)` |
| 三元 `? :` | `toBoolean(cond) ? a : b` | `$a ? $b : $c` → `toBoolean(...) ? ... : ...` |
| 变量引用 `$var` | `ctx.getVariable("var")` | `$item` → `ctx.getVariable("item")` |

#### 编译上下文

表达式编译器区分两种编译上下文：

- **条件上下文**（`compileConditionExpression`）：用于 `@if`、`@elseif` 指令，布尔上下文。简单变量引用自动包装为 `toBoolean()`，如 `$flag` → `toBoolean(ctx.getVariable("flag"))`。
- **输出上下文**（`compileOutputExpression`）：用于 `{{ }}` 输出，值上下文，不包装 `toBoolean()`，直接输出值。

#### 本地变量

在 `@foreach`、`@for` 循环中声明的循环变量（如 `@foreach($items as $item)` 中的 `$item`）会被记录为本地变量，编译时直接使用变量名而非 `ctx.getVariable()`，避免重复查询上下文。

---

## 6. BladeTemplate —— 模板基类

`com.weacsoft.jaravel.vendor.jblade.BladeTemplate`

编译生成的模板类的抽象基类。提供渲染基础设施与组件渲染支持。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `abstract void init()` | 初始化模板（注册 Section 渲染器、解析指令），由编译器生成实现 |
| `abstract void render(Writer writer)` | 渲染模板到 Writer，由编译器生成实现 |
| `String render()` | 渲染模板，返回字符串（内部使用 `StringWriter`） |
| `BladeContext getContext()` | 获取执行上下文 |
| `void setContext(BladeContext)` | 设置执行上下文 |
| `void setEngine(BladeEngine)` | 设置关联的引擎（用于组件渲染） |
| `boolean isInitialized()` | 是否已初始化 |
| `void setInitialized(boolean)` | 设置初始化状态 |
| `void resetContext()` | 重置上下文（新建 `BladeContext`，标记未初始化） |
| `void resetContext(BladeContext)` | 重置为指定上下文 |
| `protected void write(Writer, String)` | 写入字符串 |
| `protected void write(Writer, Object)` | 写入对象（调用 `toString()`） |
| `protected boolean toBoolean(Object)` | 将值转为布尔（null=false, Number!=0, String 非空） |
| `protected void renderComponent(Writer, String, Map, Map)` | 渲染组件 |
| `protected String route(String name)` | 生成路由 URL，对齐 PHP `route('name')` |
| `protected String route(String name, Object params)` | 生成带参数的路由 URL，对齐 PHP `route('name', ['key' => value])`（params 可为 Map 或 null） |
| `protected String asset(String path)` | 生成静态资源 URL，与 `url()` 完全一致（不附加任何前缀），对齐 PHP `asset('path')` |
| `protected String url(String path)` | 生成 URL，对齐 PHP `url('path')` |
| `protected Object session(String key)` | 获取 session 值，对齐 PHP `session('key')` |
| `protected String old(String key)` | 获取旧输入值，对齐 PHP `old('key')` |
| `protected String csrf_field()` | CSRF 表单字段，对齐 PHP `csrf_field()` |
| `protected String csrf_token()` | CSRF token，对齐 PHP `csrf_token()` |
| `protected Object getProperty(Object obj, String name)` | 反射获取对象属性，对齐 PHP `$var->prop` |
| `protected Object getMapValue(Object obj, String key)` | 获取 Map 值，对齐 PHP `$var['key']` |
| `protected Object invokeMethod(Object obj, String method, Object... args)` | 反射调用对象方法，对齐 PHP `$var->method(args)` |
| `protected Object elvis(Object a, Object b)` | Elvis 运算符，对齐 PHP `$a ?: $b` |
| `protected Object nullCoalescing(Object a, Object b)` | 空合并运算符，对齐 PHP `$a ?? $b` |
| `protected String concat(Object... parts)` | 字符串拼接，对齐 PHP `.` 运算符 |
| `protected boolean empty(Object obj)` | 空值检查，对齐 PHP `empty($var)` |
| `protected int intval(Object obj)` | 转整数，对齐 PHP `intval($var)` |
| `protected String json_encode(Object obj)` | JSON 编码，对齐 PHP `json_encode($var)` |
| `protected int count(Object obj)` | 计数，对齐 PHP `count($var)` |
| `protected String sprintf(String format, Object... args)` | 格式化字符串，对齐 PHP `sprintf(...)` |
| `protected String str_replace(String search, String replace, String subject)` | 字符串替换，对齐 PHP `str_replace(...)` |
| `protected String implode(String glue, Object obj)` | 数组连接，对齐 PHP `implode(...)` |
| `protected double ceil(double val)` | 向上取整，对齐 PHP `ceil($var)` |
| `protected double floor(double val)` | 向下取整，对齐 PHP `floor($var)` |
| `protected LocalDateTime carbonParse(Object date)` | Carbon 日期解析，对齐 PHP `Carbon::parse($date)` |
| `protected LocalDate carbonToday()` | Carbon 当前日期，对齐 PHP `Carbon::today()` |
| `protected int carbonYear(Object date)` | Carbon 年份，对齐 PHP `Carbon::today()->year` |

### 组件渲染机制

`renderComponent` 方法支持 `@component` 指令：

1. 保存当前上下文的组件状态
2. 设置组件数据与插槽（slot）
3. 加载组件模板，注入 `$slot` 变量与组件数据
4. 调用组件模板的 `render()`
5. 恢复上下文状态

### 6.1 PHP 辅助函数

`BladeTemplate` 内置了一系列 PHP 辅助方法，对齐 Laravel Blade 模板中常用的 PHP 函数与 Laravel 辅助函数。这些方法由表达式编译引擎在编译时自动调用，使模板中可以直接使用 PHP 风格的语法。

#### Laravel 辅助函数

| 方法 | 对齐 PHP 函数 | 说明 |
| --- | --- | --- |
| `route(name)` | `route('name')` | 生成路由 URL |
| `route(name, params)` | `route('name', ['key' => value])` | 生成带参数的路由 URL |
| `asset(path)` | `asset('path')` | 生成静态资源 URL，与 `url()` 行为一致（无前缀） |
| `url(path)` | `url('path')` | 生成 URL |
| `session(key)` | `session('key')` | 获取 session 值 |
| `old(key)` | `old('key')` | 获取旧输入值 |
| `csrf_field()` | `csrf_field()` / `@csrf` | CSRF 隐藏表单字段。**仅当 `VerifyCsrfToken` 中间件已应用于当前路由时**才输出 `<input type="hidden" name="_token" value="...">`；若未启用该中间件，`csrf_field()` 返回空字符串（等同指令不存在，不输出任何隐藏域）。`{{ csrf_field() }}` 原样输出（不转义） |
| `csrf_token()` | `csrf_token()` | CSRF token 字符串。**由框架开箱即用内置注册**（SpringBoot 自动配置）：仅当 `VerifyCsrfToken` 中间件已应用于当前路由时返回非空令牌（读取 `VerifyCsrfToken` 存入 `HttpSession` 的 token，无则生成并写回，且与校验同源）；未启用中间件时为空串 |

#### 对象与数组操作

| 方法 | 对齐 PHP 语法 | 说明 |
| --- | --- | --- |
| `getProperty(obj, name)` | `$var->prop` | 反射获取对象属性（依次尝试 getter、isser、字段、Map.get） |
| `getMapValue(obj, key)` | `$var['key']` | 获取 Map 值，非 Map 时回退到 `getProperty` |
| `invokeMethod(obj, method, args)` | `$var->method(args)` | 反射调用对象方法（支持精确匹配与 Object 参数回退） |

#### 运算符

| 方法 | 对齐 PHP 语法 | 说明 |
| --- | --- | --- |
| `elvis(a, b)` | `$a ?: $b` | Elvis 运算符，a 为真返回 a，否则返回 b |
| `nullCoalescing(a, b)` | `$a ?? $b` | 空合并运算符，a 不为 null 返回 a，否则返回 b |
| `concat(parts...)` | `.` 运算符 | 字符串拼接 |

#### PHP 内置函数

| 方法 | 对齐 PHP 函数 | 说明 |
| --- | --- | --- |
| `empty(obj)` | `empty($var)` | 空值检查（null、空字符串、空集合、0 等为空） |
| `intval(obj)` | `intval($var)` | 转整数（支持 Number、String、Boolean） |
| `json_encode(obj)` | `json_encode($var)` | JSON 编码（支持 String、Map、Collection） |
| `count(obj)` | `count($var)` | 计数（支持 Collection、Map、Object[]） |
| `sprintf(format, args)` | `sprintf(...)` | 格式化字符串，委托 `String.format` |
| `str_replace(search, replace, subject)` | `str_replace(...)` | 字符串替换 |
| `implode(glue, obj)` | `implode(...)` | 数组/集合连接为字符串 |
| `ceil(val)` | `ceil($var)` | 向上取整 |
| `floor(val)` | `floor($var)` | 向下取整 |

#### Carbon 日期函数

| 方法 | 对齐 PHP 语法 | 说明 |
| --- | --- | --- |
| `carbonParse(date)` | `Carbon::parse($date)` | Carbon 日期解析，返回 `LocalDateTime` |
| `carbonToday()` | `Carbon::today()` | Carbon 当前日期，返回 `LocalDate` |
| `carbonYear(date)` | `Carbon::today()->year` | 获取年份，支持 `LocalDate` 与 `LocalDateTime` |

---

## 7. BladeContext —— 执行上下文

`com.weacsoft.jaravel.vendor.jblade.BladeContext`

模板执行时的上下文，维护变量、Section、组件等状态。

### 方法文档

#### 变量管理

| 方法签名 | 说明 |
| --- | --- |
| `void setVariable(String name, Object value)` | 设置变量 |
| `Object getVariable(String name)` | 获取变量 |
| `Map<String, Object> getVariables()` | 获取所有变量 |

#### Section 管理（模板继承）

| 方法签名 | 说明 |
| --- | --- |
| `void setSection(String name, String content)` | 设置 Section 内容 |
| `String getSection(String name)` | 获取 Section 内容 |
| `void setSectionRenderer(String name, Consumer<Writer>)` | 设置 Section 渲染器 |
| `Consumer<Writer> getSectionRenderer(String name)` | 获取 Section 渲染器 |
| `void startSection(String name)` | 开始 Section |
| `void appendSectionContent(String content)` | 追加 Section 内容 |
| `void endSection()` | 结束 Section |
| `String getParentTemplate()` | 获取父模板名（`@extends`） |
| `void setParentTemplate(String)` | 设置父模板名 |

#### 组件管理

| 方法签名 | 说明 |
| --- | --- |
| `void startComponent(String name)` | 开始组件 |
| `void endComponent()` | 结束组件 |
| `void setComponentData(String key, Object value)` | 设置组件数据 |
| `Object getComponentData(String key)` | 获取组件数据 |
| `void startSlot(String name)` | 开始插槽 |
| `void endSlot()` | 结束插槽 |
| `String getSlot(String name)` | 获取插槽内容 |

#### 重置

| 方法签名 | 说明 |
| --- | --- |
| `void reset()` | 清空所有状态（变量、Section、组件等） |

---

## 8. 内存编译机制

`com.weacsoft.jaravel.vendor.utils.memory` 包提供了将 Java 源码在内存中编译并加载的机制，无需写入磁盘文件。

### 8.1 MemoryClassLoader —— 内存类加载器

`com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader`

继承 `ClassLoader`，从内存中读取 class 字节码加载类。

| 方法签名 | 说明 |
| --- | --- |
| `Map<String, byte[]> getCompiledClasses()` | 获取所有已编译类的字节码映射 |
| `List<String> getCompiledClassesName()` | 获取所有已编译类名列表 |
| `void removeAll()` | 清除所有已编译类 |
| `Class<?> findClass(String name)` | 重写：从 `compiledClasses` 中查找字节码并 `defineClass` |

### 8.2 MemoryFileManager —— 内存文件管理器

`com.weacsoft.jaravel.vendor.utils.memory.MemoryFileManager`

继承 `ForwardingJavaFileManager`，捕获编译器输出的类字节码到内存。

| 方法签名 | 说明 |
| --- | --- |
| `JavaFileObject getJavaFileForOutput(...)` | 重写：将编译输出重定向到 `ClassFileJavaFileObject` |
| `List<String> getGeneratedClassNames()` | 获取生成的类名列表 |
| `byte[] getGeneratedClass(String className)` | 获取生成的类字节码 |

### 8.3 SourceCodeJavaFileObject —— 源代码文件对象

`com.weacsoft.jaravel.vendor.utils.memory.SourceCodeJavaFileObject`

继承 `SimpleJavaFileObject`，将 Java 源码字符串包装为编译器可识别的文件对象。

| 方法签名 | 说明 |
| --- | --- |
| `CharBuffer getCharContent(boolean)` | 返回源码内容的 `CharBuffer` |

### 8.4 ClassFileJavaFileObject —— 字节码文件对象

`com.weacsoft.jaravel.vendor.utils.memory.ClassFileJavaFileObject`

继承 `SimpleJavaFileObject`，使用 `ByteArrayOutputStream` 捕获编译器输出的字节码。

| 方法签名 | 说明 |
| --- | --- |
| `OutputStream openOutputStream()` | 返回内部 `ByteArrayOutputStream` |
| `byte[] getBytes()` | 获取捕获的字节码 |

### 编译流程图

```
BladeCompiler.compile()
        │
        ▼
SourceCodeJavaFileObject(fullClassName, sourceCode)   -- 源码对象
        │
        ▼
JavaCompiler.getTask(null, MemoryFileManager, diagnostics, ...)
        │
        ▼
task.call()  -- 编译
        │
        ▼
MemoryFileManager.getGeneratedClassNames()  -- 获取生成的类名
        │
        ▼
MemoryFileManager.getGeneratedClass(name)   -- 获取字节码
        │
        ▼
MemoryClassLoader.getCompiledClasses().put(name, bytes)  -- 存入类加载器
        │
        ▼
返回类全名
```

---

## 9. 工具类

### 9.1 StringUtils —— 命名转换工具

`com.weacsoft.jaravel.vendor.utils.StringUtils`

| 方法签名 | 说明 | 示例 |
| --- | --- | --- |
| `static String underlineToCamelCase(String)` | 下划线转小驼峰 | `user_name` -> `userName` |
| `static String camelCaseToUnderline(String)` | 小驼峰转下划线 | `userName` -> `user_name` |
| `static String underlineToPascalCase(String)` | 下划线转大驼峰 | `user_name` -> `UserName` |
| `static String pascalCaseToUnderline(String)` | 大驼峰转下划线 | `UserName` -> `user_name` |
| `static String camelCaseToPascalCase(String)` | 小驼峰转大驼峰 | `userName` -> `UserName` |
| `static String pascalCaseToCamelCase(String)` | 大驼峰转小驼峰 | `UserName` -> `userName` |

---

## 10. 支持的指令

### 输出指令

| 指令 | 语法 | 说明 |
| --- | --- | --- |
| 输出变量 | `{{ $name }}` | 输出变量值（调用 `toString()`） |
| 输出表达式 | `{{ $user->name }}` | 输出对象属性，由表达式编译引擎编译为 `getProperty(...)` |
| 输出拼接 | `{{ 'Hello, ' . $name }}` | 字符串拼接，编译为 Java `+` |
| 输出空合并 | `{{ $title ?? 'Default' }}` | 空合并运算符，编译为 `nullCoalescing(...)` |
| 输出辅助函数 | `{{ asset('css/app.css') }}` | 调用 PHP 辅助函数 |
| 路由辅助函数 | `{{ route('user.profile') }}` / `{{ route('user.profile', ['id' => 1]) }}` | 调用框架开箱即用内置的 `route()` 生成路由 URL |
| 注释 | `{{-- 注释内容 --}}` | 注释，编译时移除 |

### 路由指令

| 指令 | 语法 | 说明 |
| --- | --- | --- |
| 路由 URL | `@route('user.profile')` / `@route('user.profile', ['id' => 1])` | 等价于 `route()` 函数调用，编译为运行时 `route(name, params)`。`name` 对应 `Route.name(...)` 注册的别名（支持分组 `name(...)` 前缀拼接，如 `Route.prefix("admin").name("admin").group(...)` 内 `name("login")` → `admin.login`） |

### 表达式语法

`{{ }}` 输出指令与 `@if`、`@elseif` 条件指令中支持完整的 Blade 表达式语法，由表达式编译引擎（参见 [5.1 表达式编译引擎](#51-表达式编译引擎)）编译为 Java 代码：

| 语法 | 示例 | 说明 |
| --- | --- | --- |
| 变量引用 | `$name` | 引用上下文变量 |
| 对象属性 | `$user->name` | 反射获取属性 |
| 对象方法 | `$user->getName()` | 反射调用方法 |
| 数组访问 | `$item['key']` | 获取 Map 值 |
| 关联数组 | `['key' => value]` | 编译为 `Map.of(...)` |
| 字符串拼接 | `'a' . $b` | 编译为 Java `+` |
| 空合并 | `$a ?? $b` | 编译为 `nullCoalescing(...)` |
| Elvis | `$a ?: $b` | 编译为 `elvis(...)` |
| 三元 | `$a ? $b : $c` | 编译为 `toBoolean(...) ? ... : ...` |
| 静态方法 | `URL::asset('path')` | 编译为 `asset(...)` |
| Carbon 方法 | `Carbon::parse($date)` | 编译为 `carbonParse(...)` |
| 辅助函数 | `csrf_field()` | 编译为运行时 `csrf_field()`，返回隐藏 input（`{{ }}` 中按原样输出） |

### 控制结构指令

| 指令 | 语法 | 说明 |
| --- | --- | --- |
| 条件 | `@if($condition)` ... `@elseif($cond2)` ... `@else` ... `@endif` | 条件判断 |
| 循环 | `@foreach($items as $item)` ... `@endforeach` | 遍历集合 |
| 循环 | `@for(init; cond; update)` ... `@endfor` | 标准 for 循环 |
| 循环 | `@while($cond)` ... `@endwhile` | while 循环 |

### 模板继承指令

| 指令 | 语法 | 说明 |
| --- | --- | --- |
| 继承 | `@extends('layout')` | 指定父模板 |
| 区块定义 | `@section('name')` ... `@endsection` | 定义区块内容 |
| 区块简写 | `@section('name', 'value')` | 定义区块为简单字符串 |
| 区块输出 | `@yield('name')` | 在父模板中输出子模板定义的区块 |

### 组件指令

| 指令 | 语法 | 说明 |
| --- | --- | --- |
| 组件 | `@component('alert', ['type' => 'danger'])` ... `@endcomponent` | 渲染组件 |
| 插槽 | `@slot('header')` ... `@endslot` | 定义组件插槽 |

---

## 11. 使用示例

### 11.1 基本模板

模板文件 `templates/hello.blade.java`：

```blade
<h1>Hello, {{ $name }}!</h1>
<p>You have {{ $count }} messages.</p>
```

渲染：

```java
BladeEngine engine = new BladeEngine("templates");

Map<String, Object> vars = new HashMap<>();
vars.put("name", "Alice");
vars.put("count", 5);

String html = engine.render("hello", vars);
// <h1>Hello, Alice!</h1>
// <p>You have 5 messages.</p>
```

### 11.2 条件与循环

模板文件 `templates/users.blade.java`：

```blade
<h1>User List</h1>
@if($users.isEmpty())
    <p>No users found.</p>
@else
    <ul>
    @foreach($users as $user)
        <li>{{ $user }}</li>
    @endforeach
    </ul>
@endif
```

渲染：

```java
BladeEngine engine = new BladeEngine("templates");

Map<String, Object> vars = new HashMap<>();
vars.put("users", List.of("Alice", "Bob", "Charlie"));

String html = engine.render("users", vars);
```

### 11.3 模板继承

父模板 `templates/layout.blade.java`：

```blade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'Default Title')</title>
</head>
<body>
    <nav>Navigation</nav>
    <main>
        @yield('content')
    </main>
</body>
</html>
```

子模板 `templates/page.blade.java`：

```blade
@extends('layout')

@section('title', 'My Page')

@section('content')
    <h1>Welcome!</h1>
    <p>This is the page content.</p>
@endsection
```

渲染：

```java
BladeEngine engine = new BladeEngine("templates");
String html = engine.render("page", null);
// 输出完整的 HTML，title 为 "My Page"，content 为子模板定义的内容
```

### 11.4 组件

组件模板 `templates/alert.blade.java`：

```blade
<div class="alert alert-{{ $type }}">
    {{ $slot }}
</div>
```

使用组件的模板 `templates/message.blade.java`：

```blade
@component('alert', ['type' => 'danger'])
    @slot('default')
        Something went wrong!
    @endslot
@endcomponent
```

渲染：

```java
BladeEngine engine = new BladeEngine("templates");
String html = engine.render("message", null);
// <div class="alert alert-danger">
//     Something went wrong!
// </div>
```

### 11.5 在 HTTP 控制器中使用

通过 HTTP 模块的 `ResponseBuilder.view()` 返回视图响应：

```java
@GetMapping("/users")
public Object listUsers() {
    Map<String, Object> data = new HashMap<>();
    data.put("users", userService.findAll());
    return ResponseBuilder.view("users.list", data);
}
```

> `ResponseBuilder.view()` 内部使用 `BladeEngine` 渲染模板并包装为 HTTP 响应。

---

## 12. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `BladeEngine` | 部分线程安全 | `templateClassCache` 与 `templateInstanceCache` 使用 `ConcurrentHashMap`。模板实例的 `init()` 使用 double-checked locking（`synchronized`）保证单次初始化。但 `render()` 方法会修改 `BladeContext` 状态，同一模板实例的并发渲染需要外部同步 |
| `BladeCompiler` | 非线程安全 | 编译过程涉及 `MemoryFileManager` 与 `MemoryClassLoader` 的写入操作，应避免并发编译同一模板。建议在初始化阶段预编译或通过 `BladeEngine` 的缓存机制避免重复编译 |
| `BladeTemplate` | 单线程使用 | `context` 字段为实例变量，`render()` 会修改上下文状态。同一实例不应并发渲染。`BladeEngine` 通过实例缓存复用模板，但 `render()` 前会 `resetContext()`，因此不同请求串行渲染是安全的 |
| `BladeContext` | 非线程安全 | 使用 `HashMap`、`Stack` 等非线程安全容器，应在单线程内使用。每次 `render()` 前由 `BladeEngine` 重置 |
| `MemoryClassLoader` | 线程安全 | `compiledClasses` 使用 `ConcurrentHashMap`，`findClass` 通过 `defineClass` 加载（JVM 保证类加载的线程安全） |
| `MemoryFileManager` | 线程安全 | `generatedClasses` 使用 `ConcurrentHashMap` |
| `StringUtils` | 线程安全 | 无状态静态方法 |

> **重要提示**：`BladeEngine.render()` 方法在渲染前会调用 `template.resetContext()` 重置上下文，因此多个请求**串行**调用 `render()` 是安全的。但**并发**调用同一 `BladeEngine` 实例的 `render()` 方法可能导致上下文状态混乱，建议在高并发场景下为每个请求创建独立的 `BladeEngine` 实例，或使用外部同步机制。

---

## 13. 静态资源 URL 生成（@asset / asset）

`jblade` 提供 `@asset` 指令与 `asset()` 辅助函数，用于在 Blade 模板中生成静态资源 URL。**其语义与 `url()` 完全一致：仅根据传入路径拼接根路径，不附加任何固定的资源前缀**（既不会自动加 `/static`，也不会自动加 `/assets`）。

### 功能说明

| 项 | 说明 |
| --- | --- |
| 对齐 Laravel | `asset('css/app.css')` / `url('css/app.css')` 辅助函数 |
| 指令语法 | `@asset('css/app.css')` |
| 表达式写法 | `{{ asset('css/app.css') }}` |
| 运行时实现 | `asset("css/app.css")`（内部直接调用 `url()`） |
| URL 前缀 | 无（与 `url()` 一致） |
| 编译产物 | `write(writer, asset("css/app.css"))` / `echo(writer, asset("css/app.css"))` |

模板中写 `@asset('css/app.css')` 或 `{{ asset('css/app.css') }}`，`BladeCompiler` 统一编译为对运行时 `asset()` 方法的调用，输出与 `url('css/app.css')` 完全相同，例如：

- `asset('css/app.css')`      → `/css/app.css`
- `asset('/js/app.js')`       → `/js/app.js`
- `asset('images/logo.png')`  → `/images/logo.png`
- `asset('')`                 → `/`

### 使用示例

Blade 模板：

```blade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'Default')</title>
    <link rel="stylesheet" href="{{ asset('css/app.css') }}">
    <script src="@asset('js/app.js')"></script>
</head>
<body>
    <img src="{{ asset('images/logo.png') }}" alt="logo">
    @yield('content')
</body>
</html>
```

### 渲染结果示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>Default</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/app.js"></script>
</head>
<body>
    <img src="/images/logo.png" alt="logo">
</body>
</html>
```

### 与路由模块静态资源路由对接

`@asset` 生成的 URL（如 `/css/app.css`）由 HTTP 模块的 `StaticResourceRoute`（`com.weacsoft.jaravel.vendor.http.staticresource.StaticResourceRoute`）实际响应。**`asset()` 本身不附加任何前缀**，若希望资源 URL 带统一前缀（如 `/static/css/app.css`），请在路径中显式写出前缀，并保证该前缀与 `StaticResourceRoute` 的 `urlPrefix` 一致：

```blade
<link rel="stylesheet" href="@asset('static/css/app.css')">  {{-- → /static/css/app.css --}}
```

```yaml
jaravel:
  http:
    static-resource:
      enabled: true
      url-prefix: /static
      default-location: classpath:/static/
      cache-max-age: 3600
```

> **关键点**：`asset()` 等价于 `url()`，不会自动注入前缀。`/static` 这类前缀应由调用方在路径中显式给出，并通过 `StaticResourceRoute`（或 `router.serveStatic` 的 `urlPrefix`）实际托管对应资源。

---

## 14. 预编译功能

jblade 提供预编译能力，允许在开发阶段（有 JDK）将所有 Blade 模板预编译为字节码，生产环境仅需 JRE 即可运行，无需运行时编译。

### 14.1 设计理念

传统运行时编译模式依赖 `javax.tools.JavaCompiler`（仅 JDK 包含），生产环境必须安装完整 JDK。预编译模式将编译阶段前置到开发/构建阶段：

- **开发阶段**：使用 `BladePrecompiler` 或命令行工具 `BladePrecompilerMain` 将所有 `.blade.java` 模板编译为字节码，输出为打包文件或散乱 class 文件
- **生产环境**：通过 `BladeEngine.fromPrecompiledPackage()` 或 `BladeEngine.fromPrecompiledClasses()` 加载预编译产物，仅依赖 JRE

这样生产环境无需 JDK，减小部署体积，同时避免运行时编译开销。

预编译模式与运行时编译模式对比：

| 特性 | 运行时编译模式 | 预编译模式 |
| --- | --- | --- |
| 生产环境要求 | JDK 17+ | JRE 17+ |
| 运行时编译 | 是（首次渲染时） | 否（已预编译） |
| 部署产物 | `.blade.java` 模板文件 | `.jblade.zip` 打包文件或 `.class` 目录 |
| 启动速度 | 首次渲染需编译 | 直接加载字节码 |
| 适用场景 | 开发阶段、模板频繁修改 | 生产部署、CI/CD |

### 14.2 两种编译模式

`BladePrecompiler.CompileMode` 枚举定义两种输出模式：

```java
public enum CompileMode {
    PACKAGED,  // 打包为单个文件（.jblade.zip 或自定义后缀）
    CLASSES    // 散乱 class 文件到目录
}
```

| 模式 | 说明 | 适用场景 |
| --- | --- | --- |
| `PACKAGED` | 所有模板字节码与映射关系打包为单个 `.jblade.zip` 文件 | 生产部署，便于分发与版本管理 |
| `CLASSES` | 每个模板编译为独立的 `.class` 文件，输出到目录 | 调试或需要单独管理 class 文件的场景 |

### 14.3 BladePrecompiler —— 预编译工具

`com.weacsoft.jaravel.vendor.jblade.BladePrecompiler`

Blade 模板预编译工具。在开发阶段（有 JDK）将所有 Blade 模板预编译为字节码，支持打包模式（`PACKAGED`）和散乱 class 模式（`CLASSES`）两种输出。内部使用 `BladeCompiler.compileSource()` 编译模板内容，通过 `PrecompiledTemplateLoader` 保存产物。

#### 构造器

| 构造器签名 | 说明 |
| --- | --- |
| `BladePrecompiler(String templateDir, String suffix)` | 指定模板目录和文件后缀 |

#### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `int compileAll(String outputDir, CompileMode mode, String packageName, String fileSuffix)` | 预编译所有模板，返回编译的模板数量。`mode` 指定输出模式，`packageName` 仅 packaged 模式使用，`fileSuffix` 默认 `.jblade.zip` |
| `int compileAllToZip(String outputDir, String fileName)` | 便利方法：打包模式预编译，输出为指定文件名的 zip 包 |
| `int compileAllToClasses(String outputDir)` | 便利方法：散乱 class 模式预编译，输出到指定目录 |

#### 使用示例

```java
// 打包模式：预编译所有模板为单个 .jblade.zip 文件
BladePrecompiler precompiler = new BladePrecompiler("templates", ".blade.java");
int count = precompiler.compileAllToZip("precompiled", "templates.jblade.zip");
System.out.println("预编译了 " + count + " 个模板");

// 散乱 class 模式：预编译所有模板为独立 .class 文件
int count2 = precompiler.compileAllToClasses("precompiled/classes");

// 完整参数调用
int count3 = precompiler.compileAll("precompiled", CompileMode.PACKAGED, "myapp", ".jblade.zip");
```

### 14.4 PrecompiledTemplateLoader —— 预编译模板加载器

`com.weacsoft.jaravel.vendor.jblade.PrecompiledTemplateLoader`

预编译模板加载器，负责从打包文件或目录加载预编译的模板字节码，以及将字节码保存到打包文件或目录。`BladeEngine.fromPrecompiledPackage()` 和 `BladeEngine.fromPrecompiledClasses()` 内部使用此类加载预编译产物。

#### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `PrecompiledBundle loadFromPackage(String packagePath)` | 从打包文件（`.jblade.zip`）加载预编译模板，返回包含字节码与映射的 bundle |
| `PrecompiledBundle loadFromDirectory(String dirPath)` | 从目录加载预编译的散乱 `.class` 文件，返回 bundle |
| `void saveToPackage(String packagePath, Map<String,byte[]> bytecodes, Map<String,String> mapping)` | 将字节码与映射保存到打包文件 |
| `void saveToDirectory(String dirPath, Map<String,byte[]> bytecodes, Map<String,String> mapping)` | 将字节码与映射保存到目录（散乱 class 文件） |

#### PrecompiledBundle

`PrecompiledTemplateLoader.PrecompiledBundle` 是预编译模板包，包含类字节码与模板名到类名的映射：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `classBytecodes` | `Map<String, byte[]>` | 类名 → 字节码映射 |
| `templateToClassMapping` | `Map<String, String>` | 模板名 → 类名映射 |

#### 使用示例

```java
PrecompiledTemplateLoader loader = new PrecompiledTemplateLoader();

// 从打包文件加载
PrecompiledBundle bundle = loader.loadFromPackage("precompiled/templates.jblade.zip");

// 从目录加载
PrecompiledBundle bundle2 = loader.loadFromDirectory("precompiled/classes");

// 保存到打包文件
loader.saveToPackage("output/templates.jblade.zip", bytecodes, mapping);

// 保存到目录
loader.saveToDirectory("output/classes", bytecodes, mapping);
```

### 14.5 BladePrecompilerMain —— 命令行工具

`com.weacsoft.jaravel.vendor.jblade.BladePrecompilerMain`

预编译命令行工具入口。在开发阶段通过命令行将所有 Blade 模板预编译为字节码，支持打包模式与散乱 class 模式。

#### CLI 参数

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `--template-dir=<path>` | 是 | - | 模板文件目录 |
| `--suffix=<suffix>` | 否 | `.blade.java` | 模板文件后缀 |
| `--output-dir=<path>` | 是 | - | 输出目录 |
| `--mode=<mode>` | 否 | `packaged` | 编译模式：`packaged` 或 `classes` |
| `--package-name=<name>` | 否 | - | 包名（仅 packaged 模式） |
| `--file-suffix=<suffix>` | 否 | `.jblade.zip` | 打包文件后缀（仅 packaged 模式） |

#### 使用示例

```bash
# 打包模式：预编译所有模板为 .jblade.zip 文件
java -cp jblade.jar com.weacsoft.jaravel.vendor.jblade.BladePrecompilerMain \
  --template-dir=templates \
  --suffix=.blade.java \
  --output-dir=precompiled \
  --mode=packaged \
  --package-name=myapp \
  --file-suffix=.jblade.zip

# 散乱 class 模式：预编译所有模板为独立 .class 文件
java -cp jblade.jar com.weacsoft.jaravel.vendor.jblade.BladePrecompilerMain \
  --template-dir=templates \
  --suffix=.blade.java \
  --output-dir=precompiled/classes \
  --mode=classes
```

### 14.6 JRE-only 运行示例

预编译完成后，生产环境仅需 JRE 即可运行：

```java
// 从打包文件创建引擎（仅需 JRE，无需 JDK）
BladeEngine engine = BladeEngine.fromPrecompiledPackage("precompiled/templates.jblade.zip");
String html = engine.render("users.list", Map.of("users", userList));

// 从 class 目录创建引擎（仅需 JRE，无需 JDK）
BladeEngine engine = BladeEngine.fromPrecompiledClasses("precompiled/classes");
String html = engine.render("welcome", Map.of("name", "Alice"));
```

> **JDK 不可用时的错误提示**：在预编译模式下，若模板未预编译且 JDK 不可用，`compileAndCache()` 会抛出 `IllegalStateException`，错误信息包含解决方案提示（建议使用预编译模式）。

---

## 15. 内置辅助函数与中间件联动（CSRF / route）

`jblade` 提供两组与后端运行时强相关、但**由框架自动注册、开发者零配置即可使用**的辅助函数：`csrf_field()/csrf_token()/@csrf`（依赖 `jaravel-http` 的 `VerifyCsrfToken` 中间件）与 `route()` / `url()`（依赖 `jaravel-http` 的 `Router`）。本节讲清它们的使用逻辑与联动关系。

### 15.1 CSRF 防护：csrf_field() / csrf_token() / @csrf

CSRF 防护由「**中间件校验**」与「**模板输出令牌**」两部分组成，二者通过 `HttpSession` 中的同一份令牌（key = `csrf_token`）对齐。

#### 三个模板入口

| 模板写法 | 作用 | 运行时方法 | 输出 |
| --- | --- | --- | --- |
| `{{ csrf_field() }}` 或 `@csrf` | 输出完整隐藏域 | `csrf_field()` / `csrf()` | `<input type="hidden" name="_token" value="令牌">`（原样输出，不被 HTML 转义） |
| `{{ csrf_token() }}` | 仅输出令牌字符串 | `csrf_token()` | 令牌字符串（如 `a1b2c3...`） |

#### 启动链路（开箱即用，无需任何注册）

1. `jaravel-springboot` 的自动配置在启动时完成两件内置注册：
   - 向 `MiddlewareAliasRegistry` 注册别名 `"VerifyCsrfToken"`（指向 `VerifyCsrfToken.instance()`）。
   - 向 `BladeFunctions` 注册 `"csrf_token"` 函数：从当前请求 session 读取/生成令牌。
2. 开发者在路由组上挂接该中间件即可（见 `RouteServiceProvider`）：
   ```java
   Route.group(Map.of(Route.Group.MIDDLEWARE, new String[]{"VerifyCsrfToken"}), Web::register);
   ```

#### 启用 / 不启用的行为差异（关键）

`csrf_field()` / `@csrf` 的输出**严格取决于 `VerifyCsrfToken` 中间件是否应用于当前请求**：

- **已挂接（启用）**：`VerifyCsrfToken.handle` 运行时会做两件事——
  - 给请求打上「已启用」标记（`request` 属性 `__jaravel_csrf_enabled = true`）；
  - 若 session 中尚无令牌，则生成一个写入 `HttpSession`（key = `csrf_token`），并通过 `Set-Cookie` 下发（受 `addHttpOnlyCookie` 控制）。
  
  之后模板渲染时，`csrf_token()` 依据该标记返回非空令牌，`csrf_field()` 正常输出隐藏域。表单提交时，中间件对 POST/PUT/PATCH/DELETE 校验请求中的 `_token` 与 session 令牌是否一致。

- **未挂接（未启用）**：`handle` 不会被调用，请求上**没有**「已启用」标记，`csrf_token()` 返回空串，此时 `csrf_field()` / `@csrf` **返回空字符串、不输出任何 `<input>`**——等同该指令不存在。

  > 设计意图：若开发者没有在某组路由上启用 CSRF 中间件，却仍让模板输出隐藏域，既无用又易误导；因此框架选择「未启用即不输出」。

#### 校验与放行规则（`VerifyCsrfToken`）

| 情况 | 行为 |
| --- | --- |
| 请求方法为 GET / HEAD / OPTIONS | 自动放行（安全方法不修改状态） |
| 请求方法在 `except` 列表（默认 `api/`、`logout`、`logout/post`） | 自动放行 |
| 其它写操作（POST/PUT/PATCH/DELETE） | 校验 `_token` 与 session 令牌，不一致或缺失则返回 419（默认） |
| 令牌在 session 中缺失 | 视为首次访问，自动放行（中间件已写入令牌，下次请求起校验生效） |

#### 配置项（`application.yml`）

```yaml
jaravel:
  http:
    csrf:
      enabled: true              # 总开关（VerifyCsrfToken 自身的 enabled 字段）
      add-http-only-cookie: false # 是否将令牌写入 HttpOnly Cookie（默认 false，仅存 session）
      except:                    # 额外免校验 URI 前缀
        - /api/
        - /logout
```

#### 最小可工作示例

路由组（Web 已挂 `VerifyCsrfToken` 的页面模板）：

```blade
<form method="POST" action="{{ route('admin.login') }}">
    @csrf
    <input name="email">
    <input name="password" type="password">
    <button>登录</button>
</form>
```

渲染输出（`VerifyCsrfToken` 已启用时）：

```html
<form method="POST" action="/admin/login">
    <input type="hidden" name="_token" value="a1b2c3d4...">
    <input name="email">
    <input name="password" type="password">
    <button>登录</button>
</form>
```

若把该模板用于**未挂接** `VerifyCsrfToken` 的路由组，`@csrf` 渲染为空字符串，表单中不会出现隐藏域。

### 15.2 route() 与 url()：按路由名 / 按路径生成 URL

jblade 提供两个与 Laravel 对齐的 URL 辅助函数，语义与 Laravel 的 `route()` / `url()` 完全一致：

| 辅助函数 | 对齐 Laravel | 语义 |
| --- | --- | --- |
| `route(name)` / `route(name, params)` | `route('name')` | 按路由**别名**解析出 URL（路由须存在，否则回退为路径映射） |
| `url(path)` | `url('/path')` | 按**路径**生成 URL，**不校验路由是否存在** |

#### route()：按路由全名解析 URL（模板）

```blade
<a href="{{ route('admin.login') }}">登录</a>
<img src="{{ route('image.show', Map.of('id', 42)) }}">
```

#### url()：按路径生成 URL（模板，不校验存在）

```blade
<a href="{{ url('/admin/login') }}">登录</a>
<a href="{{ url('admin/login') }}">登录</a>   <!-- 自动补前导 / -->
```

`url()` 对已是绝对地址（含 `://`）或已以 `/` 开头的路径原样返回，其余自动补前导 `/`；空值返回 `/`。

#### 路由名如何形成

路由全名 = 分组累积前缀 + 本路由名，点号连接。例如：

```java
Route.prefix("admin").name("admin").group(() -> {
    Route.get("/login", ...).name("login.index");   // 全名: admin.login.index
    Route.post("/login", ...).name("login");         // 全名: admin.login
});
```

则 `route('admin.login')` → `/admin/login`，`route('admin.login.index')` → `/admin/login`。

#### 参数替换规则（route）

- 若 `params` 为 `Map`：按 `{key}` 占位符逐一代换，例如 `route('image.show', Map.of("id", 42))` 对 `Route.get("/img/{id}", ...)` → `/img/42`。
- 若 `params` 为单个值（非 Map）：替换路径中**第一个** `{...}` 占位符。
- 未匹配到路由名：回退为 `/` + 名称中点号替换为斜杠（如 `route('admin.login')` 回退 `/admin/login`），保证不抛错。
- 未提供第二参时 `params = null`，路径无占位符则原样输出。

#### 在 Java 中生成 URL（AppConfig.app().route()）

两个辅助函数在 Java 侧有完全一致的对应实现，封装在 `com.weacsoft.jaravel.vendor.route.RouteHelper` 门面（方法均为静态，对齐 Laravel 全局辅助函数）。通过 `AppConfig.app().route()` 即可流式调用：

```java
// route(别名)：按路由名解析 URL，对齐 Laravel route('admin.login')
String url1 = AppConfig.app().route().route("admin.login");
String url2 = AppConfig.app().route().route("user.show", Map.of("id", 1));

// url(路径)：单纯生成 URL，不校验是否存在，对齐 Laravel url('/admin/login')
String url3 = AppConfig.app().route().url("admin/login");   // -> "/admin/login"

// 静态全局调用（任意 Java 处可直接使用，等价于模板 route()/url()）
String url4 = RouteHelper.route("admin.login");
String url5 = RouteHelper.url("/admin/login");
```

**实现说明**：Java 不允许同一类中静态方法与实例方法签名相同，因此 `RouteHelper` 的方法为静态；`AppConfig.app().route()` 返回 `RouteHelper` 单例，`.route(...)` / `.url(...)` 通过「实例引用调用静态方法」（Java 合法）落到同一实现。启动时 `RouteServiceProvider` 会调用 `RouteHelper.setRouter(rootRouter)` 注入根路由器，故按名解析随时可用。

#### 别名注册（开箱即用）

`route()` 与 `url()` 均由 `jaravel-springboot` 自动配置注册到 `BladeFunctions`（名为 `"route"` 与 `"url"`），开发者无需任何注册即可在模板中使用；二者同样纳入「注册即自检」保证。

### 15.3 开箱即用与“零注册”保证

- `csrf_token`、`csrf_field`、`@csrf`、`route`、`url` 均由框架在启动时**自动注册并自检**：若任一注册未落地，自动配置会抛出 `IllegalStateException` 使应用启动失败（而不是悄悄留下“空 value / 空路由”的不可用状态）。
- 在请求上下文之外调用 `csrf_token()`（如离线渲染），框架记录 WARN 日志并返回空串，确保不会静默产生一个无用的空令牌。
- **开发者侧**：不需要、也不应该在应用层 `BladeEngineProvider` 或任何 Provider 中重新注册这些内置函数——它们已由框架托管。若你需要自定义函数，见第 16 节。

---

## 16. 自定义扩展：注册 Blade 函数与指令

除内置函数外，你可以向模板引擎注册**自定义 Blade 函数**与**自定义指令**，从而把任意 Java 逻辑暴露给 `.blade.java` 模板。

### 16.1 注册自定义 Blade 函数（BladeFunctions）

`com.weacsoft.jaravel.vendor.jblade.BladeFunctions` 是一个**全局静态注册表**，函数签名统一为 `java.util.function.Function<Object[], Object>`（参数为模板调用实参数组，返回值为输出对象，框架会自动 `echo`）。

#### API

| 方法 | 说明 |
| --- | --- |
| `BladeFunctions.register(String name, Function<Object[],Object> fn)` | 注册/覆盖一个函数。同名会被覆盖（最后注册者生效） |
| `BladeFunctions.has(String name)` | 判断函数是否已注册 |
| `BladeFunctions.callOrDefault(String name, Object def)` | 调用函数；未注册时返回 `def`（内置 `csrf_token()` 即走此兜底逻辑） |
| `BladeFunctions.call(String name, Object... args)` | 调用函数（未注册会抛异常） |
| `BladeFunctions.clear()` | 清空整个注册表（主要用于测试隔离） |

#### 示例：注册一个 `gravatar()` 函数

```java
import com.weacsoft.jaravel.vendor.jblade.BladeFunctions;
import java.util.function.Function;

BladeFunctions.register("gravatar", args -> {
    String email = String.valueOf(args[0]);
    String hash = md5(email.trim().toLowerCase());           // 你的哈希实现
    return "https://www.gravatar.com/avatar/" + hash;
});
```

模板中即可使用：

```blade
<img src="{{ gravatar($user.email) }}" alt="avatar">
```

> 参数通过 `args[0]`、`args[1]`… 按位置取；`args.length` 可判断可选参。返回值会被模板引擎 `echo` 输出；返回 `null` 会被当作空串。

### 16.2 注册自定义指令（BladeDirectives）

`com.weacsoft.jaravel.vendor.jblade.BladeDirectives` 允许注册**编译期指令**（`@xxx`），把一段 Blade 文本替换为自定义生成的 Java 源码。

#### API

| 方法 | 说明 |
| --- | --- |
| `BladeDirectives.register(String name, Function<String,String> handler)` | 注册一个指令处理器：`name` 为指令名，`handler` 接收指令体文本（如 `@datetime(...)` 中括号里的 `...`），返回要内联进模板类的 Java 源码字符串 |
| `BladeDirectives.has(String name)` | 判断指令是否已注册 |
| `BladeDirectives.get(String name)` | 取出处理器（未注册抛 `IllegalStateException`） |

#### 示例：注册一个 `@shout('hello')` 指令

```java
import com.weacsoft.jaravel.vendor.jblade.BladeDirectives;
import java.util.function.Function;

BladeDirectives.register("shout", body -> {
    // body = "'hello'" -> 生成 Java 源码：调用 String.toUpperCase()
    return "write(writer, String.valueOf(" + body + ").toUpperCase());";
});
```

模板：

```blade
@shout('hello')   {{-- 编译为 write(writer, "hello".toUpperCase()); -> 输出 HELLO --}}
```

> 指令处理器返回的是**模板类内的 Java 源码**（`write(writer, ...)` / `echo(writer, ...)`），不是最终 HTML。复杂指令可拼出多行代码，也可调用你在 `BladeFunctions` 中注册的函数。

### 16.3 在 Jaravel（Spring Boot）中注册

在 Jaravel 应用里，自定义函数/指令应在**应用启动阶段**注册一次（在任何请求渲染模板之前）。推荐做法是写一个继承 `ServiceProvider` 的组件，在 `boot()`（或 `register()`）中注册——框架的 `ProviderRegistry` 会在容器刷新时统一调用：

```java
package com.weacsoft.jaravel.app.provider;

import com.weacsoft.jaravel.vendor.core.provider.ServiceProvider;
import com.weacsoft.jaravel.vendor.jblade.BladeFunctions;
import com.weacsoft.jaravel.vendor.jblade.BladeDirectives;
import org.springframework.stereotype.Component;

@Component
public class BladeExtrasProvider extends ServiceProvider {

    @Override
    public void boot() {
        // 自定义 Blade 函数
        BladeFunctions.register("gravatar", args ->
                "https://www.gravatar.com/avatar/" + md5(String.valueOf(args[0])));

        // 自定义指令
        BladeDirectives.register("shout", body ->
                "write(writer, String.valueOf(" + body + ").toUpperCase());");
    }
}
```

注册时机说明：

- `BladeFunctions` / `BladeDirectives` 是**进程级静态注册表**，只要在首次渲染前注册一次即可全局生效；多次重复注册同名函数会被覆盖。
- 框架内置的 `csrf_token` / `route` 由 `jaravel-springboot` 自动配置注册。你的自定义函数使用**不同的名字**即可，二者互不干扰。
- 若你确实想**覆盖**某个内置函数（例如自定义 `csrf_token` 的来源），直接 `BladeFunctions.register("csrf_token", ...)` 即可覆盖，但这意味着你接手了令牌生成逻辑，**不推荐**——内置实现已与 `VerifyCsrfToken` 中间件同源联动。

### 16.4 内置函数一览与“不要重复注册”注意

下列函数由框架**自动注册**，开发者**不应**在应用层手动注册（否则属于重复注册，可能覆盖框架行为）：

| 函数名 | 来源模块 | 说明 | 是否依赖中间件/路由启用 |
| --- | --- | --- | --- |
| `csrf_field()` / `@csrf` | jblade（运行时 `csrf_field()`） | 输出 `<input type="hidden" name="_token" value="...">` | 是（`VerifyCsrfToken` 未启用时输出空串） |
| `csrf_token()` | jblade + springboot 注册源 | 返回令牌字符串 | 是（未启用时为空串） |
| `route(name[, params])` | jblade + springboot 注册源 | 按路由名解析 URL | 否（始终可用，未匹配回退为 `/` + 名称） |
| `asset(path)` / `@asset(path)` | jblade | 静态资源 URL（等价于 `url()`） | 否 |
| `url(path)` | jblade | URL 生成 | 否 |
| `session(key[, def])` | jblade | 读取 session 变量 | 否（无 session 时返回默认值/空） |
| `old(name[, def])` | jblade | 读取上次输入（old flash） | 否 |

只有**框架未提供、你自行扩展**的函数（如 `gravatar`、`shout` 等）才需要按 16.1 / 16.2 / 16.3 注册。

> 简言之：**内置的用就行，别再注册一遍；自己的自定义函数，用 `BladeFunctions.register` / `BladeDirectives.register` 在 `ServiceProvider.boot()` 里注册一次。**
