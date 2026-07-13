# jblade 模块

> Jaravel-Vendor 的模板引擎模块，提供 Laravel Blade 风格的模板编译与渲染。支持 `{{ }}` 输出、`@if/@foreach/@for/@while` 控制结构、`@extends/@section/@yield` 模板继承、`@component` 组件等特性，通过内存编译（`MemoryClassLoader`）将模板编译为 Java 类后执行。包名统一为 `com.weacsoft.jaravel.vendor.jblade`。

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

> 运行环境要求：JDK 17+（需使用 JDK 而非 JRE，因为依赖 `javax.tools.JavaCompiler`），Spring Boot 3.2.5（Spring 6.x）。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor
├── jblade
│   ├── BladeEngine              // 模板引擎（入口，含 CompiledTemplateData 内部类）
│   ├── BladeCompiler            // 模板编译器（Blade -> Java 源码 -> 字节码，含表达式编译引擎）
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

> **缓存说明**：`cacheStore` 参数为 `CacheStore`（cache 模块的实例接口），可为 `null`。为 `null` 时仅使用一级内存缓存，不影响功能。引入 cache 模块后传入 `CacheStore` 实例即可启用二级缓存（跨进程共享字节码）。

> **后缀说明**：默认使用 `.blade.java` 后缀。采用该后缀可使常见 IDE（如 IntelliJ IDEA）将模板文件识别为 Java 相关文件，从而在模板内提供部分代码提示与语法高亮。

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `String render(String templateName, Map<String, Object> variables)` | 渲染模板，注入变量，返回 HTML 字符串 |
| `String render(String templateName)` | 渲染模板（无变量） |
| `BladeTemplate loadTemplate(String templateName)` | 加载（编译 + 缓存）模板，返回 `BladeTemplate` 实例。内部按"查一级 → 查二级 → 编译 → 回填缓存"流程执行 |
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

### 编译流程

```
compile(templateName)
        │
        ├── 1. 读取模板文件：classpath:templateDir/templateName(suffix)
        ├── 2. generateClassName(templateName) -> "Blade_" + name（如 Blade_users_list）
        ├── 3. generateJavaCode(className, content) -> 生成 Java 源码
        ├── 4. 提取包名（从生成的源码中解析）
        ├── 5. 获取系统 JavaCompiler
        ├── 6. 创建 MemoryFileManager + SourceCodeJavaFileObject
        ├── 7. 执行编译任务（task.call()）
        ├── 8. 将编译后的字节码存入 MemoryClassLoader
        └── 9. 返回类全名
```

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
| 辅助函数 `csrf_field()` | 空字符串 | `csrf_field()` → `""` |
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
| `protected String route(String name, Map<String, Object> params)` | 生成带参数的路由 URL，对齐 PHP `route('name', ['key' => value])` |
| `protected String asset(String path)` | 生成静态资源 URL，对齐 PHP `asset('path')` |
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
| `asset(path)` | `asset('path')` | 生成静态资源 URL |
| `url(path)` | `url('path')` | 生成 URL |
| `session(key)` | `session('key')` | 获取 session 值 |
| `old(key)` | `old('key')` | 获取旧输入值 |
| `csrf_field()` | `csrf_field()` | CSRF 表单字段（当前返回空字符串占位） |
| `csrf_token()` | `csrf_token()` | CSRF token（当前返回空字符串占位） |

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
| 注释 | `{{-- 注释内容 --}}` | 注释，编译时移除 |

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
| 辅助函数 | `csrf_field()` | 编译为空字符串占位 |

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

## 13. 静态资源 URL 生成（@asset）

`jblade` 提供 `@asset` 指令，用于在 Blade 模板中生成静态资源 URL，对齐 Laravel 的 `asset()` 辅助函数。该指令由 `BladeAssetHelper` 类提供运行时实现，将资源相对路径拼接为带 URL 前缀的完整路径，常用于引用 CSS、JS、图片等静态文件。

### 功能说明

| 项 | 说明 |
| --- | --- |
| 对齐 Laravel | `asset('css/app.css')` 辅助函数 |
| 指令语法 | `@asset('css/app.css')` |
| 运行时实现 | `BladeAssetHelper.url("css/app.css")` |
| 默认 URL 前缀 | `/static`（可通过 `BladeAssetHelper.setUrlPrefix()` 修改） |
| 编译产物 | `write(writer, BladeAssetHelper.url("css/app.css"))` |

模板中写 `@asset('css/app.css')` 时，`BladeCompiler` 在 `processRenderDirectives` 方法的 switch 语句中识别 `asset` case，将其编译为对 `BladeAssetHelper.url("css/app.css")` 的调用，运行时输出形如 `/static/css/app.css` 的 URL。

### 使用示例

Blade 模板（如 `templates/layout.blade.java`）：

```blade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'Default')</title>
    <link rel="stylesheet" href="@asset('css/app.css')">
    <script src="@asset('js/app.js')"></script>
</head>
<body>
    <img src="@asset('images/logo.png')" alt="logo">
    @yield('content')
</body>
</html>
```

### 渲染结果示例

假设 URL 前缀为 `/static`，上述模板渲染结果：

```html
<!DOCTYPE html>
<html>
<head>
    <title>Default</title>
    <link rel="stylesheet" href="/static/css/app.css">
    <script src="/static/js/app.js"></script>
</head>
<body>
    <img src="/static/images/logo.png" alt="logo">
</body>
</html>
```

### BladeAssetHelper 配置说明

`BladeAssetHelper`（`com.weacsoft.jaravel.vendor.jblade.BladeAssetHelper`）是一个静态工具类，通过静态字段持有当前 URL 前缀。应在应用启动时（通常由自动装配完成）调用 `setUrlPrefix()` 配置前缀：

```java
import com.weacsoft.jaravel.vendor.jblade.BladeAssetHelper;

// 设置 URL 前缀（自动补齐首部 / 并去除尾部 /）
BladeAssetHelper.setUrlPrefix("/static");

// 生成资源 URL
String cssUrl = BladeAssetHelper.url("css/app.css");  // "/static/css/app.css"
String jsUrl  = BladeAssetHelper.url("/js/app.js");   // "/static/js/app.js"（开头的 / 会被去除）
```

| 方法签名 | 说明 |
| --- | --- |
| `static void setUrlPrefix(String prefix)` | 设置 URL 前缀，自动确保以 `/` 开头、不以 `/` 结尾；传入 null 或空字符串时保持原值 |
| `static String getUrlPrefix()` | 获取当前 URL 前缀（默认 `/static`） |
| `static String url(String path)` | 将资源相对路径拼接为完整 URL（如 `url("css/app.css")` → `/static/css/app.css`）；path 开头的 `/` 会被自动去除 |

> **默认前缀**：未调用 `setUrlPrefix()` 时，`BladeAssetHelper` 使用默认前缀 `/static`。

### 与路由模块 StaticResourceRoute 的配合使用

`@asset` 指令生成的 URL 需要由 HTTP 模块的 `StaticResourceRoute`（`com.weacsoft.jaravel.vendor.http.staticresource.StaticResourceRoute`）实际响应静态文件。两者通过**相同的 URL 前缀**对接：

1. **路由侧**：通过 `router.serveStatic()` 注册静态资源路由，指定 URL 前缀与资源目录。
2. **模板侧**：通过 `BladeAssetHelper.setUrlPrefix()` 设置相同的前缀。
3. **对接**：模板中 `@asset('css/app.css')` 生成 `/static/css/app.css`，请求到达后被 `StaticResourceRoute` 拦截，从资源目录加载 `css/app.css` 文件返回。

```java
import com.weacsoft.jaravel.vendor.jblade.BladeAssetHelper;

// 1. 路由侧：注册静态资源路由，URL 前缀为 /static
router.serveStatic("/static", "classpath:/static/", 3600);

// 2. 模板侧：设置相同的 URL 前缀（应与上面 serveStatic 的前缀一致）
BladeAssetHelper.setUrlPrefix("/static");

// 3. 模板中使用 @asset('css/app.css') → /static/css/app.css
//    请求 /static/css/app.css 由 StaticResourceRoute 处理，返回 classpath:/static/css/app.css
```

或通过配置自动注册（HTTP 模块）：

```yaml
jaravel:
  http:
    static-resource:
      enabled: true
      url-prefix: /static
      default-location: classpath:/static/
      cache-max-age: 3600
```

> **关键点**：`BladeAssetHelper` 的 URL 前缀必须与 `StaticResourceRoute` 的 `urlPrefix` 保持一致，否则 `@asset` 生成的 URL 将无法被静态资源路由命中。`StaticResourceRoute` 支持多目录回退查找（按顺序匹配第一个命中的文件）。
