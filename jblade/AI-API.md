# jblade AI-API Reference

> Module: `jblade` | Package: `com.weacsoft.jaravel.vendor.jblade` | Version: 0.1.2

## Overview
jblade 模块是 Laravel Blade 风格的 Java 模板引擎。它将 `.blade.java` 模板文件编译为 Java 类（通过内存编译器），支持模板继承（@extends/@yield/@section）、组件（@component/@slot）、控制结构（@if/@foreach/@for）、变量输出（{{ }}）以及丰富的 Blade 表达式语法（对象方法/属性访问、数组访问、字符串拼接、空合并运算符等）。编译后的模板类继承 BladeTemplate，运行时通过 BladeEngine 渲染。

jblade 支持两种运行模式：
- **运行时编译模式（默认）**：在运行时通过 `javax.tools.JavaCompiler` 将 `.blade.java` 模板编译为字节码并加载，需要完整的 JDK 环境。
- **预编译模式**：在开发阶段（有 JDK）使用 `BladePrecompiler` 将所有模板预编译为字节码，输出为打包文件（`.jblade.zip`）或散乱 `.class` 文件。生产环境（仅 JRE）通过 `BladeEngine.fromPrecompiledPackage()` 或 `BladeEngine.fromPrecompiledClasses()` 加载预编译产物，无需 JDK，无需运行时编译。

> **依赖说明**：jblade 通过 Maven 依赖 `utils` 模块复用内存编译基础设施（`MemoryClassLoader`、`MemoryFileManager`、`SourceCodeJavaFileObject` 等，包名 `com.weacsoft.jaravel.vendor.utils.memory.*`）。这些类不再在 jblade 模块中重复定义，由 utils 模块统一提供。`BladeCompiler` 和 `BladeEngine` 直接使用 utils 模块中的 `MemoryClassLoader` 进行模板类的内存编译与加载。

## Classes & Interfaces

### BladeEngine
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板引擎，负责模板编译、缓存和渲染。采用两级缓存机制：一级为内存 `ConcurrentHashMap`（缓存 `Class<?>`，始终启用），二级为可选的 `CacheStore`（缓存编译后字节码 `byte[]`，跨进程共享）。仅在一二级缓存均未命中时才执行 `BladeCompiler.compile()`，避免重复编译。支持模板继承（父模板自动加载并合并 section）。除构造器外，还提供两个静态工厂方法 `fromPrecompiledPackage()` 和 `fromPrecompiledClasses()`，支持从预编译产物加载模板（JRE-only 运行）。在预编译模式下，`loadTemplate()` 优先从已加载字节码获取 `Class`；`compileAndCache()` 在 JDK 不可用时会抛出包含解决方案提示的 `IllegalStateException`。

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
| `fromPrecompiledPackage` | `String packagePath` | `static BladeEngine` | **工厂方法**：从预编译打包文件（`.jblade.zip`）创建引擎，仅需 JRE，无需 JDK。内部通过 `PrecompiledTemplateLoader.loadFromPackage()` 加载字节码 |
| `fromPrecompiledClasses` | `String classesDir` | `static BladeEngine` | **工厂方法**：从预编译 class 目录创建引擎，仅需 JRE，无需 JDK。内部通过 `PrecompiledTemplateLoader.loadFromDirectory()` 加载字节码 |
| `render` | `String templateName, Map<String, Object> variables` | `String` | 渲染模板，传入变量，返回渲染结果字符串 |
| `render` | `String templateName` | `String` | 渲染模板（无变量） |
| `loadTemplate` | `String templateName` | `BladeTemplate` | 加载（编译 + 缓存）模板，返回模板实例。流程：查一级缓存 → 查二级缓存 → 编译 → 回填缓存。**预编译模式**下优先从已加载的字节码获取 `Class`，无需运行时编译 |
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

JRE-only 运行（预编译模式）：

```java
// 从预编译打包文件创建引擎（仅需 JRE，无需 JDK）
BladeEngine engine = BladeEngine.fromPrecompiledPackage("precompiled/templates.jblade.zip");
String html = engine.render("user.profile", Map.of("user", user));

// 或从预编译 class 目录创建引擎
BladeEngine engine2 = BladeEngine.fromPrecompiledClasses("precompiled/classes");
String html2 = engine2.render("welcome", Map.of("name", "Alice"));
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
- **Description**: Blade 模板编译器，将 `.blade.java` 模板文件编译为 Java 源代码并通过内存编译器加载。支持 Blade 语法：@extends、@section/@endsection、@yield、@if/@elseif/@else/@endif、@foreach/@endforeach、@for/@endfor、@component/@endcomponent、@slot/@endslot、@asset（静态资源 URL 生成，运行时委托 `BladeAssetHelper.url()`）、{{ }} 变量输出、{{-- --}} 注释。`compile()` 方法已重构：分离文件读取与编译逻辑，新增 `compileSource(templateName, content)` 方法支持直接编译模板内容（供 `BladePrecompiler` 使用）。新增 `getClassLoader()` getter 暴露内部类加载器。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader` | 构造方法 | 指定模板目录和类加载器（默认后缀 .blade.java） |
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader, String suffix` | 构造方法 | 指定模板目录、类加载器和文件后缀 |
| `compile` | `String templateName` | `String` | 编译模板，返回生成的类全限定名 |
| `compileSource` | `String templateName, String content` | `String` | **新增**：直接编译模板内容（跳过文件读取），返回类全限定名。供 `BladePrecompiler` 预编译时使用 |
| `getSuffix` | 无 | `String` | 获取模板文件后缀（默认 `.blade.java`） |
| `getClassLoader` | 无 | `MemoryClassLoader` | **新增**：获取内部类加载器，供预编译工具提取编译后的字节码 |

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

---

## 预编译功能

jblade 提供预编译能力，允许在开发阶段（有 JDK）将所有 Blade 模板预编译为字节码，生产环境仅需 JRE 即可运行，无需运行时编译。

### 设计理念

传统运行时编译模式依赖 `javax.tools.JavaCompiler`（仅 JDK 包含），生产环境必须安装完整 JDK。预编译模式将编译阶段前置到开发/构建阶段：

- **开发阶段**：使用 `BladePrecompiler` 或命令行工具 `BladePrecompilerMain` 将所有 `.blade.java` 模板编译为字节码，输出为打包文件或散乱 class 文件
- **生产环境**：通过 `BladeEngine.fromPrecompiledPackage()` 或 `BladeEngine.fromPrecompiledClasses()` 加载预编译产物，仅依赖 JRE

这样生产环境无需 JDK，减小部署体积，同时避免运行时编译开销。

### 两种编译模式

```java
public enum CompileMode {
    PACKAGED,  // 打包为单个文件（.jblade.zip 或自定义后缀）
    CLASSES    // 散乱 class 文件到目录
}
```

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| `PACKAGED` | 所有模板字节码与映射关系打包为单个 `.jblade.zip` 文件 | 生产部署，便于分发与版本管理 |
| `CLASSES` | 每个模板编译为独立的 `.class` 文件，输出到目录 | 调试或需要单独管理 class 文件的场景 |

---

### BladePrecompiler

- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板预编译工具。在开发阶段（有 JDK）将所有 Blade 模板预编译为字节码，支持打包模式（`PACKAGED`）和散乱 class 模式（`CLASSES`）两种输出。内部使用 `BladeCompiler.compileSource()` 编译模板内容，通过 `PrecompiledTemplateLoader` 保存产物。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladePrecompiler` | `String templateDir, String suffix` | 构造方法 | 指定模板目录和文件后缀 |
| `compileAll` | `String outputDir, CompileMode mode, String packageName, String fileSuffix` | `int` | 预编译所有模板，返回编译的模板数量。`mode` 指定输出模式，`packageName` 仅 packaged 模式使用，`fileSuffix` 默认 `.jblade.zip` |
| `compileAllToZip` | `String outputDir, String fileName` | `int` | 便利方法：打包模式预编译，输出为指定文件名的 zip 包 |
| `compileAllToClasses` | `String outputDir` | `int` | 便利方法：散乱 class 模式预编译，输出到指定目录 |

#### Usage Example

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

---

### BladePrecompiler.CompileMode

- **Type**: enum (nested in BladePrecompiler)
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 预编译输出模式枚举。

#### Constants

| Constant | Description |
|----------|-------------|
| `PACKAGED` | 打包为单个文件（`.jblade.zip` 或自定义后缀） |
| `CLASSES` | 散乱 class 文件到目录 |

---

### PrecompiledTemplateLoader

- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 预编译模板加载器，负责从打包文件或目录加载预编译的模板字节码，以及将字节码保存到打包文件或目录。`BladeEngine.fromPrecompiledPackage()` 和 `BladeEngine.fromPrecompiledClasses()` 内部使用此类加载预编译产物。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `loadFromPackage` | `String packagePath` | `PrecompiledBundle` | 从打包文件（`.jblade.zip`）加载预编译模板，返回包含字节码与映射的 bundle |
| `loadFromDirectory` | `String dirPath` | `PrecompiledBundle` | 从目录加载预编译的散乱 `.class` 文件，返回 bundle |
| `saveToPackage` | `String packagePath, Map<String,byte[]> bytecodes, Map<String,String> mapping` | `void` | 将字节码与映射保存到打包文件 |
| `saveToDirectory` | `String dirPath, Map<String,byte[]> bytecodes, Map<String,String> mapping` | `void` | 将字节码与映射保存到目录（散乱 class 文件） |

#### Usage Example

```java
// 从打包文件加载
PrecompiledBundle bundle = loader.loadFromPackage("precompiled/templates.jblade.zip");

// 从目录加载
PrecompiledBundle bundle2 = loader.loadFromDirectory("precompiled/classes");

// 保存到打包文件
loader.saveToPackage("output/templates.jblade.zip", bytecodes, mapping);

// 保存到目录
loader.saveToDirectory("output/classes", bytecodes, mapping);
```

---

### PrecompiledTemplateLoader.PrecompiledBundle

- **Type**: static nested class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 预编译模板包，包含类字节码与模板名到类名的映射。由 `PrecompiledTemplateLoader` 的加载方法返回，供 `BladeEngine` 在预编译模式下使用。

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `classBytecodes` | `Map<String, byte[]>` | 类名 → 字节码映射 |
| `templateToClassMapping` | `Map<String, String>` | 模板名 → 类名映射 |

---

### BladePrecompilerMain

- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: 预编译命令行工具入口。在开发阶段通过命令行将所有 Blade 模板预编译为字节码，支持打包模式与散乱 class 模式。

#### CLI Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--template-dir=<path>` | 是 | - | 模板文件目录 |
| `--suffix=<suffix>` | 否 | `.blade.java` | 模板文件后缀 |
| `--output-dir=<path>` | 是 | - | 输出目录 |
| `--mode=<mode>` | 否 | `packaged` | 编译模式：`packaged` 或 `classes` |
| `--package-name=<name>` | 否 | - | 包名（仅 packaged 模式） |
| `--file-suffix=<suffix>` | 否 | `.jblade.zip` | 打包文件后缀（仅 packaged 模式） |

#### Usage Example

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

#### JRE-only 运行示例

预编译完成后，生产环境仅需 JRE 即可运行：

```java
// 从打包文件创建引擎（仅需 JRE）
BladeEngine engine = BladeEngine.fromPrecompiledPackage("precompiled/templates.jblade.zip");
String html = engine.render("users.list", Map.of("users", userList));

// 从 class 目录创建引擎（仅需 JRE）
BladeEngine engine = BladeEngine.fromPrecompiledClasses("precompiled/classes");
String html = engine.render("welcome", Map.of("name", "Alice"));
```
