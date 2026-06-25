# jblade AI-API Reference

> Module: `jblade` | Package: `com.weacsoft.jaravel.vendor.jblade` | Version: 0.1.0

## Overview
jblade 模块是 Laravel Blade 风格的 Java 模板引擎。它将 `.jblade` 模板文件编译为 Java 类（通过内存编译器），支持模板继承（@extends/@yield/@section）、组件（@component/@slot）、控制结构（@if/@foreach/@for）、变量输出（{{ }}）以及丰富的 Blade 表达式语法（对象方法/属性访问、数组访问、字符串拼接、空合并运算符等）。编译后的模板类继承 BladeTemplate，运行时通过 BladeEngine 渲染。

## Classes & Interfaces

### BladeEngine
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板引擎，负责模板编译、缓存和渲染。支持可选的 Cache 驱动进行模板类缓存，支持模板继承（父模板自动加载并合并 section）。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladeEngine` | `String templateDir` | 构造方法 | 指定模板目录 |
| `BladeEngine` | `String templateDir, Cache cache` | 构造方法 | 指定模板目录和缓存驱动 |
| `BladeEngine` | `String templateDir, String suffix, Cache cache, MemoryClassLoader memoryClassLoader` | 构造方法 | 完整参数构造 |
| `render` | `String templateName, Map<String, Object> variables` | `String` | 渲染模板，传入变量，返回渲染结果字符串 |
| `render` | `String templateName` | `String` | 渲染模板（无变量） |
| `loadTemplate` | `String templateName` | `BladeTemplate` | 编译并加载模板，返回模板实例 |
| `clearCache` | 无 | `void` | 清除模板类缓存和实例缓存 |
| `clearTemplateInstanceCache` | 无 | `void` | 清除模板实例缓存 |
| `getMemoryClassLoader` | 无 | `MemoryClassLoader` | 获取内存类加载器 |
| `getCache` | 无 | `Cache` | 获取缓存驱动 |
| `isUseCache` | 无 | `boolean` | 是否启用缓存 |
| `getTemplateInstanceCacheSize` | 无 | `int` | 获取模板实例缓存大小 |

#### Usage Example
```java
// 基本用法
BladeEngine engine = new BladeEngine("templates");
String html = engine.render("user.profile", Map.of(
    "user", user,
    "title", "用户资料"
));

// 带缓存
BladeEngine engine = new BladeEngine("templates", cacheDriver);
String html = engine.render("welcome", Map.of("name", "Alice"));

// 在控制器中使用（配合 ResponseBuilder）
return ResponseBuilder.view("user.profile", Map.of("user", user));
```

---

### BladeCompiler
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.jblade`
- **Description**: Blade 模板编译器，将 `.jblade` 模板文件编译为 Java 源代码并通过内存编译器加载。支持 Blade 语法：@extends、@section/@endsection、@yield、@if/@elseif/@else/@endif、@foreach/@endforeach、@for/@endfor、@component/@endcomponent、@slot/@endslot、{{ }} 变量输出、{{-- --}} 注释。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader` | 构造方法 | 指定模板目录和类加载器（默认后缀 .jblade） |
| `BladeCompiler` | `String templateDir, MemoryClassLoader classLoader, String suffix` | 构造方法 | 指定模板目录、类加载器和文件后缀 |
| `compile` | `String templateName` | `String` | 编译模板，返回生成的类全限定名 |

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
- `$var->method()` - 对象方法调用
- `$var->property` - 对象属性访问
- `$var['key']` - 数组/Map 访问
- `'string' . $var` - 字符串拼接
- `$a ?? $b` - 空合并运算符
- `$a ?: $b` - Elvis 运算符

#### Usage Example
```java
MemoryClassLoader classLoader = new MemoryClassLoader();
BladeCompiler compiler = new BladeCompiler("templates", classLoader, ".jblade");
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
- **Description**: 编译后模板的抽象基类。每个 `.jblade` 模板编译后生成一个继承此类的 Java 类。提供模板渲染基础设施和大量 PHP/Laravel 辅助方法（对齐 Blade 模板中使用的 PHP 函数）。

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
// 模板文件 user.list.jblade:
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

### ExpiryMap
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils`
- **Description**: 带过期时间的 HashMap，继承 HashMap 并为每个 key 设置有效期。过期后自动清除。
- **Extends**: `java.util.HashMap<K, V>`

#### Methods (主要)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ExpiryMap` | 无 | 构造方法 | 默认过期时间 2 秒 |
| `ExpiryMap` | `long defaultExpiryTime` | 构造方法 | 指定默认过期时间（毫秒） |
| `ExpiryMap` | `int initialCapacity, long defaultExpiryTime` | 构造方法 | 指定初始容量和过期时间 |
| `getInstance` | 无 | `ExpiryMap<String, String>` | 获取单例实例 |
| `put` | `K key, V value` | `V` | 存入键值对（使用默认过期时间） |
| `put` | `K key, V value, long expiryTime` | `V` | 存入键值对（指定过期时间） |
| `get` | `Object key` | `V` | 获取值（过期返回 null） |
| `isInvalid` | `Object key` | `Object` | 检查键是否过期（null=不存在, -1=已过期, 其他=值） |

#### Usage Example
```java
ExpiryMap<String, String> cache = new ExpiryMap<>(5000); // 5秒过期
cache.put("token", "abc123");
Thread.sleep(3000);
String value = cache.get("token"); // "abc123"
Thread.sleep(3000);
String expired = cache.get("token"); // null
```

---

### StringUtils
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils`
- **Description**: 字符串命名风格转换工具，支持下划线与驼峰互转。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `underlineToCamelCase` | `String underlineStr` | `String` | 下划线转小驼峰（user_name -> userName） |
| `camelCaseToUnderline` | `String camelCaseStr` | `String` | 小驼峰转下划线（userName -> user_name） |
| `underlineToPascalCase` | `String underlineStr` | `String` | 下划线转大驼峰（user_name -> UserName） |
| `pascalCaseToUnderline` | `String pascalCaseStr` | `String` | 大驼峰转下划线（UserName -> user_name） |
| `camelCaseToPascalCase` | `String camelCaseStr` | `String` | 小驼峰转大驼峰（userName -> UserName） |
| `pascalCaseToCamelCase` | `String pascalCaseStr` | `String` | 大驼峰转小驼峰（UserName -> userName） |

#### Usage Example
```java
String camel = StringUtils.underlineToCamelCase("user_name"); // "userName"
String underline = StringUtils.camelCaseToUnderline("userName"); // "user_name"
String pascal = StringUtils.underlineToPascalCase("user_name"); // "UserName"
```
