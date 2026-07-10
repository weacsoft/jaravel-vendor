# jblade AI-API Reference

> Module: `jblade` | Package: `com.weacsoft.jaravel.vendor.jblade` | Version: 0.1.1

## Overview
jblade 模块是 Laravel Blade 风格的 Java 模板引擎。它将 `.blade.java` 模板文件编译为 Java 类（通过内存编译器），支持模板继承（@extends/@yield/@section）、组件（@component/@slot）、控制结构（@if/@foreach/@for）、变量输出（{{ }}）以及丰富的 Blade 表达式语法（对象方法/属性访问、数组访问、字符串拼接、空合并运算符等）。编译后的模板类继承 BladeTemplate，运行时通过 BladeEngine 渲染。

> **依赖说明**：jblade 通过 Maven 依赖 `utils` 模块复用内存编译基础设施（`MemoryClassLoader`、`MemoryFileManager`、`SourceCodeJavaFileObject` 等，包名 `com.weacsoft.jaravel.vendor.utils.memory.*`）。这些类不再在 jblade 模块中重复定义，由 utils 模块统一提供。`BladeCompiler` 和 `BladeEngine` 直接使用 utils 模块中的 `MemoryClassLoader` 进行模板类的内存编译与加载。

## Classes & Interfaces

### BladeEngine
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板引擎，负责模板编译、缓存和渲染。采用两级缓存机制：一级为内存 `ConcurrentHashMap`（缓存 `Class<?>`，始终启用），二级为可选的 `CacheStore`（缓存编译后字节码 `byte[]`，跨进程共享）。仅在一二级缓存均未命中时才执行 `BladeCompiler.compile()`，避免重复编译。支持模板继承（父模板自动加载并合并 section）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladeEngine` | `String templateDir` | 构造方法 | 指定模板目录，默认后缀 `.blade.java`，无二级缓存 |
| `BladeEngine` | `String templateDir, String suffix` | 构造方法 | 指定模板目录和文件后缀 |
| `BladeEngine` | `String templateDir, CacheStore cacheStore` | 构造方法 | 指定模板目录和二级缓存 store，默认后缀 `.blade.java` |
| `BladeEngine` | `String templateDir, MemoryClassLoader classLoader` | 构造方法 | 指定模板目录和类加载器，默认后缀 `.blade.java` |
| `BladeEngine` | `String templateDir, String suffix, CacheStore cacheStore` | 构造方法 | 指定模板目录、文件后缀和二级缓存 store |
| `BladeEngine` | `String templateDir, CacheStore cacheStore, MemoryClassLoader classLoader` | 构造方法 | 指定模板目录、二级缓存 store 和类加载器，默认后缀 `.blade.java` |
| `BladeEngine` | `String templateDir, String suffix, CacheStore cacheStore, MemoryClassLoader classLoader` | 构造方法 | 完整参数构造（全参构造器，其余重载均委托到此） |
| `render` | `String templateName, Map<String, Object> variables` | `String` | 渲染模板，传入变量，返回渲染结果字符串 |
| `render` | `String templateName` | `String` | 渲染模板（无变量） |
| `loadTemplate` | `String templateName` | `BladeTemplate` | 加载（编译 + 缓存）模板，返回模板实例。流程：查一级缓存 → 查二级缓存 → 编译 → 回填缓存 |
| `clearCache` | 无 | `void` | 清除所有缓存：一级缓存（Class）+ 二级缓存（按 key 逐个 `forget`）+ 实例缓存。不会调用 `flush()` 清空整个 store，避免影响其他模块 |
| `clearTemplate` | `String templateName` | `void` | 清除指定模板的所有缓存（一级 Class + 二级 CacheStore + 实例），不影响其他模板 |
| `clearTemplateInstanceCache` | 无 | `void` | 仅清除模板实例缓存 |
| `getMemoryClassLoader` | 无 | `MemoryClassLoader` | 获取内存类加载器 |
| `getCacheStore` | 无 | `CacheStore` | 获取二级缓存 store（可能为 `null`） |
| `isUseCacheStore` | 无 | `boolean` | 是否启用了二级缓存（`CacheStore` 非 null 时为 true） |
| `getTemplateInstanceCacheSize` | 无 | `int` | 获取模板实例缓存大小 |
| `getClassCacheSize` | 无 | `int` | 获取一级缓存中的模板数量（`Class<?>` 缓存大小） |
| `getSuffix` | 无 | `String` | 获取模板文件后缀（默认 `.blade.java`） |

#### Usage Example
```java
// 基本用法（仅一级内存缓存）
BladeEngine engine = new BladeEngine("templates");
String html = engine.render("user.profile", Map.of(
    "user", user,
    "title", "用户资料"
));

// 启用二级缓存（CacheStore，跨进程共享字节码）
// 通过 CacheManager 按名称解析 store
CacheStore cacheStore = cacheManager.store("redis");
BladeEngine engine = new BladeEngine("templates", ".blade.java", cacheStore);
String html = engine.render("welcome", Map.of("name", "Alice"));

// 判断是否启用二级缓存、查看一级缓存大小
if (engine.isUseCacheStore()) {
    System.out.println("二级缓存 store: " + engine.getCacheStore());
}
System.out.println("一级缓存模板数: " + engine.getClassCacheSize());

// 在控制器中使用（配合 ResponseBuilder）
return ResponseBuilder.view("user.profile", Map.of("user", user));
```

---

### BladeEngine.CompiledTemplateData
- **Type**: static nested class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Implements**: `java.io.Serializable`
- **Description**: 编译模板数据的序列化包装类，用于在 `CacheStore` 二级缓存中存储编译后的模板字节码与类名。`BladeEngine` 编译模板后将 `className` 与 `byte[]` 包装为本对象存入二级缓存；加载时从缓存读取并还原字节码到 `MemoryClassLoader`。

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `className` | `String` | 编译后的类全名 |
| `bytecode` | `byte[]` | 编译后的字节码 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `CompiledTemplateData` | `String className, byte[] bytecode` | 构造方法 | 创建包装对象 |
| `getClassName` | 无 | `String` | 获取类全名 |
| `getBytecode` | 无 | `byte[]` | 获取字节码 |

#### Usage Example
```java
// BladeEngine 内部使用方式（通常无需手动调用）
CompiledTemplateData data = new CompiledTemplateData(className, bytecode);
cacheStore.put("jblade:template:users.list", data, 0);  // 0 表示永不过期

// 加载时
Object cached = cacheStore.get("jblade:template:users.list");
if (cached instanceof CompiledTemplateData) {
    CompiledTemplateData d = (CompiledTemplateData) cached;
    memoryClassLoader.getCompiledClasses().put(d.getClassName(), d.getBytecode());
}
```

---

### BladeCompiler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板编译器，将 `.blade.java` 模板文件编译为 Java 源代码并通过内存编译器加载。支持 Blade 语法：@extends、@section/@endsection、@yield、@if/@elseif/@else/@endif、@foreach/@endforeach、@for/@endfor、@component/@endcomponent、@slot/@endslot、@asset（静态资源 URL 生成，运行时委托 `BladeAssetHelper.url()`）、{{ }} 变量输出、{{-- --}} 注释。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader` | 构造方法 | 指定模板目录和类加载器（默认后缀 .blade.java） |
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader, String suffix` | 构造方法 | 指定模板目录、类加载器和文件后缀 |
| `compile` | `String templateName` | `String` | 编译模板，返回生成的类全限定名 |
| `getSuffix` | 无 | `String` | 获取模板文件后缀（默认 `.blade.java`） |

#### Constants

| Constant | Type | Description |
|--------|------|-------------|
| `DEFAULT_SUFFIX` | `String` | 默认模板文件后缀，值为 `.blade.java` |

#### Supported Blade Syntax
- `{{ $variable }}` - 变量输出
- `{{-- comment --}}` - 注释
- `@extends('layout')` - 模板继承
- `@section('name') ... @endsection` - 定义区块
- `@yield('name')` - 输出区块
- `@if(condition)` / `@elseif(cond)` / `@else` / `@endif` - 条件判断
- `@foreach($items as $item)` / `@endforeach` - 循环
- `@for(init; cond; update)` / `@endfor` - for 循环
- `@component('name', [params])` / `@endcomponent` - 组件
- `@slot('name')` / `@endslot` - 组件插槽
- `@asset('css/app.css')` - 静态资源 URL 生成，编译为 `BladeAssetHelper.url("css/app.css")`
- `$var->method()` - 对象方法调用
- `$var->property` - 对象属性访问
- `$var['key']` - 数组/Map 访问
- `'string' . $var` - 字符串拼接
- `$a ?? $b` - 空合并运算符
- `$a ?: $b` - Elvis 运算符

#### Usage Example
```java
MemoryClassLoader classLoader = new MemoryClassLoader();
BladeCompiler compiler = new BladeCompiler("templates", classLoader, ".blade.java");
String className = compiler.compile("user.profile");
// className = "Blade_user_profile"
```

---

### BladeContext
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 模板渲染上下文，管理变量、区块（sections）、区块渲染器、组件数据和插槽。在模板渲染期间持有所有运行时状态。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setVariable` | `String name, Object value` | `void` | 设置模板变量 |
| `getVariable` | `String name` | `Object` | 获取模板变量 |
| `getVariables` | 无 | `Map<String, Object>` | 获取所有变量 |
| `setSection` | `String name, String content` | `void` | 设置区块内容 |
| `getSection` | `String name` | `String` | 获取区块内容 |
| `setSectionRenderer` | `String name, Consumer<Writer> renderer` | `void` | 设置区块渲染器 |
| `getSectionRenderer` | `String name` | `Consumer<Writer>` | 获取区块渲染器 |
| `setParentTemplate` | `String parentTemplate` | `void` | 设置父模板名 |
| `getParentTemplate` | 无 | `String` | 获取父模板名 |
| `reset` | 无 | `void` | 重置上下文所有状态 |
| `startSection` / `endSection` | `String name` | `void` | 开始/结束区块定义 |
| `startComponent` / `endComponent` | `String componentName` | `void` | 开始/结束组件 |
| `setComponentData` / `getComponentData` | `String key, Object value` | `void`/`Object` | 设置/获取组件数据 |
| `startSlot` / `endSlot` | `String slotName` | `void` | 开始/结束插槽 |

#### Usage Example
```java
BladeContext context = template.getContext();
context.setVariable("title", "用户列表");
context.setVariable("users", userList);
context.setSection("header", "<h1>用户管理</h1>");
```

---

### BladeTemplate
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 编译后模板的抽象基类。每个 `.blade.java` 模板编译后生成一个继承此类的 Java 类。提供模板渲染基础设施和大量 PHP/Laravel 辅助方法（对齐 Blade 模板中使用的 PHP 函数）。

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `init` | 无 | `void` | 初始化模板（抽象方法，编译器生成实现） |
| `render` | `Writer writer` | `void` | 渲染模板到 Writer（抽象方法） |
| `render` | 无 | `String` | 渲染模板并返回字符串 |
| `getContext` | 无 | `BladeContext` | 获取渲染上下文 |
| `setContext` | `BladeContext context` | `void` | 设置渲染上下文 |
| `setEngine` | `BladeEngine engine` | `void` | 设置引擎引用 |
| `isInitialized` / `setInitialized` | `boolean` | `boolean`/`void` | 初始化状态 |
| `resetContext` | 无 | `void` | 重置上下文和初始化状态 |

#### PHP Helper Methods (protected，供编译后的模板调用)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `route` | `String name` | `String` | 生成路由 URL |
| `route` | `String name, Map<String, Object> params` | `String` | 生成带参数的路由 URL |
| `asset` | `String path` | `String` | 生成静态资源 URL |
| `url` | `String path` | `String` | 生成 URL |
| `session` | `String key` | `Object` | 获取 session 值 |
| `old` | `String key` | `String` | 获取旧输入值（占位） |
| `csrf_field` / `csrf_token` | 无 | `String` | CSRF 字段/token（占位） |
| `getProperty` | `Object obj, String name` | `Object` | 反射获取对象属性 |
| `getMapValue` | `Object obj, String key` | `Object` | 获取 Map 值 |
| `invokeMethod` | `Object obj, String method, Object... args` | `Object` | 反射调用对象方法 |
| `elvis` | `Object a, Object b` | `Object` | Elvis 运算符 |
| `nullCoalescing` | `Object a, Object b` | `Object` | 空合并运算符 |
| `empty` | `Object obj` | `boolean` | 空值检查（对齐 PHP empty()） |
| `intval` | `Object obj` | `int` | 转整数（对齐 PHP intval()） |
| `count` | `Object obj` | `int` | 计数（对齐 PHP count()） |
| `json_encode` | `Object obj` | `String` | JSON 编码（对齐 PHP json_encode()） |
| `sprintf` | `String format, Object... args` | `String` | 格式化字符串 |
| `str_replace` | `String search, String replace, String subject` | `String` | 字符串替换 |
| `implode` | `String glue, Object obj` | `String` | 数组连接（对齐 PHP implode()） |
| `concat` | `Object... parts` | `String` | 字符串拼接 |
| `carbonParse` | `Object date` | `LocalDateTime` | Carbon 日期解析 |
| `carbonToday` | 无 | `LocalDate` | 获取当前日期 |
| `toBoolean` | `Object value` | `boolean` | 转布尔值 |
| `write` | `Writer writer, String/Object content` | `void` | 写入输出 |

#### Usage Example
```java
// 模板文件 user.list.blade.java:
// @extends('layouts.app')
// @section('content')
//   <h1>{{ $title }}</h1>
//   @foreach($users as $user)
//     <p>{{ $user->name }} ({{ $user->email }})</p>
//   @endforeach
// @endsection

// 渲染
BladeEngine engine = new BladeEngine("templates");
String html = engine.render("user.list", Map.of(
    "title", "用户列表",
    "users", userService.findAll()
));
```

---

### BladeAssetHelper
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板静态资源辅助类，`@asset` 指令的运行时实现。通过静态字段持有 URL 前缀，将资源相对路径拼接为完整 URL。模板中 `@asset('css/app.css')` 经 `BladeCompiler` 编译后调用 `BladeAssetHelper.url("css/app.css")`，输出 `/static/css/app.css`。对齐 Laravel 的 `asset()` 辅助函数。

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `urlPrefix` | `String`（private static） | 静态资源 URL 前缀，默认值 `/static` |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setUrlPrefix` | `String prefix` | `void` | 设置 URL 前缀，自动确保以 `/` 开头且不以 `/` 结尾；传入 null 或空字符串时保持原值 |
| `getUrlPrefix` | 无 | `String` | 获取当前 URL 前缀（默认 `/static`） |
| `url` | `String path` | `String` | 生成静态资源 URL，将资源相对路径拼接到前缀后（如 `url("css/app.css")` → `/static/css/app.css`）；path 开头的 `/` 会被自动去除；path 为 null/空时返回前缀本身 |

#### Usage Example
```java
import com.weacsoft.jaravel.vendor.jblade.BladeAssetHelper;

// 应用启动时配置 URL 前缀
BladeAssetHelper.setUrlPrefix("/static");

// 生成资源 URL
BladeAssetHelper.url("css/app.css");    // "/static/css/app.css"
BladeAssetHelper.url("/js/app.js");     // "/static/js/app.js"
```

Blade 模板中使用 `@asset` 指令：

```blade
<link rel="stylesheet" href="@asset('css/app.css')">
<script src="@asset('js/app.js')"></script>
<img src="@asset('images/logo.png')">
```

渲染结果（前缀为 `/static`）：

```html
<link rel="stylesheet" href="/static/css/app.css">
<script src="/static/js/app.js"></script>
<img src="/static/images/logo.png">
```

> 配合 HTTP 模块的 `StaticResourceRoute` 使用：`BladeAssetHelper` 的 URL 前缀须与 `StaticResourceRoute` 的 `urlPrefix` 一致，`@asset` 生成的 URL 才能被静态资源路由命中并返回对应文件。
