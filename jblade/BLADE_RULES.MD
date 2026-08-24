# jblade 模板语法规则全集（BLADE_RULES.MD）

> 本文档是 jaravel 框架 `jblade` 模块（`com.weacsoft.jaravel.vendor.jblade`）的**权威语法规则规范**，
> 面向 IntelliJ IDEA 插件开发者，涵盖 `.jblade` 模板文件的编辑提示、跳转、校验所需的全部语义。
>
> **最后更新**：2026-08-06 | 对应源码：`jaravel-vendor/jblade/src/main/java/.../jblade/`（63 个 .java 文件）
>
> 生成依据：源码精读 + `README.md`（1543 行）+ 演示项目 22 个 `.jblade` + 测试模板 20 个。

---

## 目录

1. [文件约定](#1-文件约定)
2. [模板名 → 文件路径映射](#2-模板名--文件路径映射)
3. [基础语法结构](#3-基础语法结构)
4. [输出与变量](#4-输出与变量)
5. [布局继承（Section 体系）](#5-布局继承section-体系)
6. [流程控制指令](#6-流程控制指令)
7. [循环与 $loop](#7-循环与-loop)
8. [子视图与组件](#8-子视图与组件)
9. [杂项指令](#9-杂项指令)
10. [表达式编译规则](#10-表达式编译规则)
11. [运行时辅助函数（PHP 风格）](#11-运行时辅助函数php-风格)
12. [自定义扩展机制](#12-自定义扩展机制)
13. [PJAX / Wire 模板约定](#13-pjax--wire-模板约定)
14. [配置项](#14-配置项)
15. [编译缓存与热更新](#15-编译缓存与热更新)
16. [预编译模式](#16-预编译模式)
17. [校验规则清单（插件诊断依据）](#17-校验规则清单插件诊断依据)
18. [反直觉/易错点清单](#18-反直觉易错点清单)
19. [IDE Ctrl+Click 跳转规范](#19-ide-ctrlclick-跳转规范)
20. [插件开发检查清单](#20-插件开发检查清单)


---

## 1. 文件约定

| 项 | 约定 |
|---|---|
| 文件后缀 | `.jblade`（`BladeCompiler.DEFAULT_SUFFIX`，可在 yml 中覆盖） |
| 文件编码 | UTF-8 |
| 典型存放位置 | `src/main/resources/templates/` 或运行时进程 CWD 下的 `templates/` |
| IDE 识别 | `.jblade` 是**独立后缀**，IntelliJ IDEA 不会自动识别为 Java 或 HTML。插件应将其注册为 **"Blade 模板"** 语言文件类型，**以 HTML 语法树为主**，再叠加 Blade 指令的自定义高亮 |
| 语言本质 | **HTML 为主，Blade 指令嵌入其中**。模板文件是 HTML 结构 + PHP 风格表达式（`{{ }}`、`{!! !!}`）+ Blade 指令（`@extends`、`@foreach` 等）。不是 Java 源码，编译前会被 BladeCompiler 完全替换为生成的 Java 类 |
| 包声明 | 编译生成的 Java 类**不含 `package` 声明**（默认包）。模板源码中也不应出现 `package` 关键字 |
| 不可写入的内容 | 模板文件**不是**合法 Java 源码，任何模板内的 Java import / class 声明均无效 |

**插件文件关联约定**：

- 插件应把 `.jblade` 文件注册为 **"Blade 模板"** 语言文件类型，**以 HTML 语法树为主**（支持 HTML 标签折叠、属性提示、标签补全等），再叠加 Blade 指令的自定义高亮与导航。
- 模板文件中 `{{ }}`、`{!! !!}`、`{{-- --}}`、`@xxx` 等 Blade 语法**不被 HTML 或 Java 语法树识别**，插件需自行解析并在适当位置插入高亮/导航/补全。


## 2. 模板名 → 文件路径映射

模板名是**点分命名**的字符串，编译器会将其映射为文件系统路径。

### 2.1 映射公式

```
templatePath = templateDir + File.separator + templateName.replace(".", File.separator) + suffix
```

**示例**（`templateDir = "templates"`，`suffix = ".jblade"`）：

| 模板名 | 文件系统路径（Windows） | classpath 资源路径 |
|---|---|---|
| `welcome` | `templates\welcome.jblade` | `templates/welcome.jblade` |
| `docs.index` | `templates\docs\index.jblade` | `templates/docs/index.jblade` |
| `layouts.mdui.pageinator` | `templates\layouts\mdui\pageinator.jblade` | `templates/layouts/mdui/pageinator.jblade` |

### 2.2 查找顺序

`BladeCompiler.resolveTemplateStream()` 按以下顺序查找模板：

1. **文件系统**：`./resources/<templateDir>/<path>`（相对于**进程 CWD**，不是 `src/main/resources/`）
2. **classpath**：`classpath:<templateDir>/<path>`（Spring `ClassPathResource`，对应 `src/main/resources/templates/...`）

**插件跳转目标**：优先解析 `<contentRoot>/src/main/resources/<template-dir>/...`；若进程以项目根为 CWD，同时支持 `resources/<template-dir>/...`。

### 2.3 点号的特殊含义

- `templateName.replace(".", File.separator)` 是**字面量全量替换**，替换全部 `.`
- **模板名/文件名不能出现额外的点**（除分隔目录与后缀外），否则会被误解析
- `BladeEngine` 在接收模板名前会**无条件删除所有 `'` 和 `"`**：
  ```java
  templateName = templateName.replace("'", "").replace("\"", "");
  ```
  因此 `@extends('layouts.app')` 和 `@extends("layouts.app")` 完全等价。

### 2.4 反向扫描（扫描目录下所有模板名）

`BladeEngine.scanTemplateNames()` 返回所有可用模板名列表：

```java
String rel = baseDir.toURI().relativize(f.toURI()).getPath();  // URI 统一用 '/'
String name = rel.substring(0, rel.length() - suffix.length())
              .replace('/', '.');
```

---

## 3. 基础语法结构

### 3.1 Token 类型

编译器识别 4 种 token：

| Token 类型 | 常量 | 说明 |
|---|---|---|
| `N_TEXT` | `0` | 普通文本（原样输出） |
| `N_ECHO` | `1` | `{{ ... }}` 输出（HTML 转义） |
| `N_RAW_ECHO` | `2` | `{!! ... !!}` 输出（原样） |
| `N_DIRECTIVE` | `3` | `@xxx(...)` 指令 |

### 3.2 注释 `{{-- ... --}}`

- 格式：`{{-- 多行文本 --}}`
- 编译时被**完全忽略**，不输出任何内容
- 与 HTML 注释 `<!-- ... -->` 不同：Blade 注释在**服务端**消失，前端不可见
- 插件应把 `{{-- ... --}}` 识别为"模板注释"节点，不参与任何执行语义

### 3.3 转义序列

| 语法 | 语义 | 生成目标 |
|---|---|---|
| `@@xxx` | 输出字面量 `@xxx` | 文本中的 `@` |
| `@{{ expr }}` | 输出字面量 `{{ expr }}` | 文本中的 `{{ }}` |
| `@{!! expr !!}` | 输出字面量 `{!! expr !!}` | 文本中的 `{!! !!}` |
| `@verbatim ... @endverbatim` | 块内所有 `{{ }}` / `@xxx` **原样输出** | 整个块转为纯文本 |

**重要**：`@verbatim` 块内**仍处理** `{{-- --}}` 注释，也会剥离首行换行。

### 3.4 指令参数的括号约定

指令格式：`@directive(args)`。括号与指令名之间允许**空格/制表符**。

**必带参数指令**（无 `(...)` 则视为普通文本原样输出）：

| 指令 | 原因 |
|---|---|
| `extends` / `section` / `yield` / `hasSection` / `sectionMissing` | 必须有参数，无括号视为文本 |
| `if` / `elseif` / `unless` / `isset` | 同上 |
| `foreach` / `forelse` / `for` / `while` | 同上 |
| `include` / `includeIf` / `includeWhen` / `includeUnless` | 同上 |
| `component` / `slot` | 同上 |
| `json` / `method` / `route` / `asset` | 同上 |

**注意**：`@csrf`、`@endphp`、`@stop`、`@show` 等**不带参数**的指令，无括号时仍按指令处理。


## 4. 输出与变量

### 4.1 转义输出 `{{ expr }}`

- 语法：`{{ PHP 表达式 }}`（花括号两侧允许空白）
- 行为：计算表达式，**HTML 转义**后输出
- 转义规则（`BladeTemplate.e()`）：
  | 字符 | 替换 |
  |---|---|
  | `&` | `&amp;` |
  | `<` | `&lt;` |
  | `>` | `&gt;` |
  | `"` | `&quot;` |
  | `'` | `&#039;` |
- 若值是 `Htmlable` 接口的实现，则直接调用 `toHtml()` 不转义（如分页器 `links()` 返回 HTML 时）
- 若值为 `null`，输出**空串**

### 4.2 原样输出 `{!! expr !!}`

- 语法：`{!! PHP 表达式 !!}`
- 行为：计算表达式，**直接输出**（不转义）
- 典型用法：输出已渲染 HTML 片段、`{{ csrf_field() }}`（返回 `<input>` 标签）

### 4.3 变量引用

模板通过 `Map<String, Object> variables` 注入变量，通过 `$varName` 引用：

```java
// 控制器中
Map<String, Object> data = new HashMap<>();
data.put("title", "首页");
data.put("users", List.of("Alice", "Bob"));
return ResponseBuilder.view("index", data);
```

```blade
<!-- 模板中 -->
<title>@yield('title', 'jaravel')</title>
@foreach($users as $user)
    <li>{{ $user }}</li>
@endforeach
```

**变量名规则**：
- 以 `$` 开头，后跟字母或 `_`，后接字母/数字/下划线
- 例：`$user`、`$items`、`$_token`
- 在 `@foreach($items as $item)` 中，`$item` 是**局部变量**，仅在循环体内有效

### 4.4 变量访问

| 语法 | 运行时调用 | 说明 |
|---|---|---|
| `$var` | `v("var")` → `context.getVariable("var")` | 上下文变量 |
| `$var->prop` | `getProperty(var, "prop")` | 反射获取字段 |
| `$var->method(args)` | `invokeMethod(var, "method", args...)` | 反射调用方法 |
| `$var['key']` | `getMapValue(var, "key")` | Map/数组下标 |
| `$var?->method()` | `getProperty(var, "method")` + null 安全 | 若属性为 null 则返回 null |

---

## 5. 布局继承（Section 体系）

### 5.1 机制概述

jblade 的布局继承机制与 Laravel 完全等价，支持**不限层级的多重继承**。

核心概念：

- **布局（Layout）**：被 `@extends` 引用的模板，定义 `@yield` 区域
- **子模板**：引用布局的模板，通过 `@section ... @endsection` 填充区域
- **Section 注册**：在 `init()` 阶段由编译器生成的代码注册
- **Section 渲染**：在 `render()` 阶段由 `@yield` 调用注册好的 Section 内容输出

### 5.2 指令清单

| 指令 | 参数要求 | 语义 |
|---|---|---|
| `@extends('name')` | **必须字面量** | 声明父模板；必须是模板第一行指令（无文本前导） |
| `@section('name')` | **必须字面量**（块形式） | 开始块定义一个 section |
| `@section('name', expr)` | 第二参为任意表达式 | **内联**注册 section，无块体，编译期直接注册字符串 |
| `@endsection` / `@stop` | 无参数 | 结束 section（`@stop` 等价 `@endsection`） |
| `@show` | 无参数 | 结束并**立即输出**当前 section（含父内容合并与 `@parent` 替换） |
| `@append` | 无参数 | 结束 section，以**追加**模式注册（内容追加到已有同名 section 末尾） |
| `@overwrite` | 无参数 | 结束 section，以**覆盖**模式注册（忽略已有内容） |
| `@parent` | 无参数 | 插入父模板同名 section 的内容（只能在块内使用） |
| `@yield('name')` | 第一参必填，第二参可选 | 输出 section 内容；若不存在则输出默认值或空串 |
| `@yield('name', 'default')` | — | 提供默认值 |
| `@hasSection('name')` | — | 判断 section 是否存在；生成 `if (hasSection("name")) { ... }` 块 |
| `@sectionMissing('name')` | — | 判断 section 是否缺失；生成 `if (sectionMissing("name")) { ... }` 块 |

### 5.3 Section 注册模式详解

三种模式的底层实现（`BladeTemplate.registerSection*`）：

| 指令 | 注册方法 | 行为 |
|---|---|---|
| `@endsection`（默认） | `registerSection(name, content)` | 子模板优先 + `@parent` 占位符合并（`extendSection`） |
| `@append` | `registerSectionAppend(name, content)` | 追加到已有同名 section 末尾 |
| `@overwrite` | `registerSectionOverwrite(name, content)` | 覆盖已有同名 section |

**@parent 占位符机制**（`BladeContext.PARENT_PLACEHOLDER = "@__jblade_parent__@"`）：
- 子模板的 section 内容中若包含 `@parent`，编译时生成占位字符串
- 渲染时，父模板注册同名 section 会先调 `extendSection`，把子内容中的占位符替换为父内容
- 最终 `yieldSection` 时再剥离残留的占位符

### 5.4 多级继承示例

```blade
<!-- layouts/base.jblade -->
<html>
<head><title>@yield('title', '默认标题')</title></head>
<body>
  <nav>@yield('nav')</nav>
  <main>@yield('content')</main>
</body>
</html>
```

```blade
<!-- layouts/mdui.jblade -->
@extends('layouts.base')
@section('nav')
  @parent
  <a href="/extra">扩展导航</a>
@endsection
```

```blade
<!-- page/index.jblade -->
@extends('layouts.mdui')
@section('title', '首页')
@section('content')
  <h1>你好世界</h1>
@endsection
```

**输出顺序**：`layouts.base` → `layouts/mdui`（nav 合并）→ `page/index`（content 覆盖）。

## 6. 流程控制指令

### 6.1 条件指令

| 指令 | 参数 | 编译目标 |
|---|---|---|
| `@if (expr)` | 任意表达式 | `if (toBoolean(expr)) {` |
| `@elseif (expr)` | 任意表达式 | `} else if (toBoolean(expr)) {` |
| `@else` | 无 | `} else {` |
| `@endif` | 无 | `}` |
| `@unless (expr)` | 任意表达式 | `if (!(toBoolean(expr))) {` |
| `@endunless` | 无 | `}` |
| `@isset ($a, $b)` | 一个或多个变量 | `if (isset(a) && isset(b)) {` |
| `@endisset` | 无 | `}` |
| `@empty ($var)` | 单个表达式 | `if (empty(var)) {` |
| `@endempty` | 无 | `}` |
| `@auth` | 无 | `if (toBoolean(BladeFunctions.callOrDefault("auth_check", false))) {` |
| `@endauth` | 无 | `}` |
| `@guest` | 无 | `if (!toBoolean(BladeFunctions.callOrDefault("auth_check", false))) {` |
| `@endguest` | 无 | `}` |

**条件求值规则**：
- `toBoolean()` 语义：`null` → `false`；`Boolean` → 直接返回；`Number` → `intValue != 0`；`String` → 非空且不为 `"0"`；`Collection/Map/Array` → 非空；其他 → `true`
- 简单变量引用 `$flag` 自动包装为 `toBoolean(v("flag"))`（条件上下文）

### 6.2 循环指令

#### @foreach

```blade
@foreach ($collection as $item)        // 值循环
@foreach ($collection as $key => $item) // 键值循环
@endforeach
```

编译为：
```java
List<Object[]> __it0 = toPairs(collection);
LoopHelper __loop0 = getContext().pushLoop(__it0.size());
int __i0 = 0;
for (Object[] __e0 : __it0) {
    __loop0.advance(__i0++);
    setVar("key", __e0[0]);  // 若有关键名
    setVar("item", __e0[1]);
    // ... body ...
}
getContext().popLoop();
```

**@foreach 的格式要求**：正则 `^(.+?)\s+as\s+(?:\$(\w+)\s*=>\s*)?\$(\w+)\s*$`，key 部分可选。

#### @forelse

```blade
@forelse ($collection as $item)
    <!-- 非空时执行 -->
@empty
    <!-- 空时执行 -->
@endforelse
```

### 6.3 @for 指令

```blade
@for ($i = 0; $i < 10; $i++)
    ...
@endfor
```

参数格式：分号分隔的三段（init; cond; update）。

### 6.4 @while 指令

```blade
@while (条件)
    ...
@endwhile
```

### 6.5 @continue / @break

```blade
@continue   // 无条件 continue
@break      // 无条件 break
@continue expr   // 条件 continue
@break expr        // 条件 break
```

编译为：`if (toBoolean(expr)) { continue; }` 或 `if (true) { continue; }`。


## 7. 循环与 $loop

### 7.1 $loop 变量

在 `@foreach` / `@forelse` 循环体内，模板可通过 `$loop` 访问当前迭代状态。

| 属性 | 类型 | 语义 |
|---|---|---|
| `$loop->index` | `int` | 当前迭代索引，从 0 开始 |
| `$loop->iteration` | `int` | 当前迭代次数，从 1 开始 |
| `$loop->remaining` | `int` | 剩余迭代次数（已知总数时）；否则返回 -1 |
| `$loop->count` | `int` | 集合元素总数；未知时为 -1 |
| `$loop->first` | `boolean` | 是否为第一次迭代 |
| `$loop->last` | `boolean` | 是否为最后一次迭代（已知总数时；未知时为 false） |
| `$loop->odd` | `boolean` | 是否为奇数次迭代 |
| `$loop->even` | `boolean` | 是否为偶数次迭代 |
| `$loop->depth` | `int` | 嵌套深度，从 1 开始 |
| `$loop->parent` | `LoopHelper` | 父级循环对象（最外层为 null） |

**访问方式**：`$loop->index`、`$loop->first` 等属性通过反射调用 `prop(name)` 获取。

### 7.2 $loop 的作用域

- 仅在 `@foreach` / `@forelse` 循环**块内部**有效
- 嵌套循环会压栈/出栈（`pushLoop` / `popLoop`）
- 循环外的 `$loop` 为 null

## 8. 子视图与组件

### 8.1 @include 子视图

```blade
@include('template.name')                                    // 仅渲染
@include('template.name', ['key' => $value])                 // 附加数据（覆盖同名变量）
@includeIf('template.name', ['key' => $value])               // 模板存在时才渲染
@includeWhen($cond, 'template.name', ['key' => $value])      // 条件为真时渲染
@includeUnless($cond, 'template.name', ['key' => $value])    // 条件为假时渲染
```

**行为**：
- 子视图共享父模板的全部变量（`context.getVariables()` 浅拷贝）
- 第二参数 Map 中的变量会覆盖同名父变量
- 子视图有独立的继承链（可有自己的 `@extends` / `@section`）

### 8.2 @component 组件

```blade
@component('components.card')
    @slot('title') 标题内容 @endslot
    默认插槽内容
@endcomponent

@component('components.card', ['level' => 4])
    @slot('title') 标题 @endslot
@endcomponent
```

**编译行为**：
- 组件模板名就是普通模板名，**不自动加 `components.` 前缀**
- 默认插槽 key 为 `"default"`
- 具名插槽 key 为 `<slotName>` 和 `"$" + <slotName>`（兼容旧版编译产物）
- 组件上下文自动注入 `$slot`（默认插槽内容，`HtmlString` 类型，不二次转义）

**插槽的转义语义**：
- 插槽内容包装为 `HtmlString`（`Htmlable`），`{{ $slot }}` 时**原样输出**，不二次转义
- 通过 `['foo' => $bar]` 传入的数据仍是普通值，`{{ $foo }}` **正常转义**

## 9. 杂项指令

### 9.1 指令速查表

| 指令 | 参数 | 语义 |
|---|---|---|
| `@php($x = 1)` | 内联 PHP 语句 | 生成 Java 语句 |
| `@php ... @endphp` | 块形式 PHP | 块内容原样生成 Java 语句 |
| `@csrf` | 无 | 输出 CSRF 隐藏字段；需 `VerifyCsrfToken` 中间件 |
| `@method('PUT')` | HTTP 方法名 | 输出 `_method` 隐藏字段 |
| `@json($var)` | 任意表达式 | 输出 JSON 字符串（不转义） |
| `@route('name')` | 路由别名 | 调用 `route("name")` |
| `@route('name', ['id' => 1])` | — | 带参数的路由 URL |
| `@asset('css/app.css')` | 资源路径 | 调用 `asset()` -> `url()` -> `/css/app.css` |

### 9.2 内联 PHP @php

```blade
@php($x = 1; $y = $x + 1)
@php
    $items = collect(['a', 'b']);
    $total = $items->count();
@endphp
```

### 9.3 CSRF 与 HTTP 方法

- `@csrf` 生成隐藏字段 `<input type="hidden" name="_token" value="...">`
- `@method('PUT')` 生成隐藏字段 `<input type="hidden" name="_method" value="PUT">`
- 两者均依赖 `VerifyCsrfToken` 中间件；若中间件未启用，`csrf()` 返回空串

### 9.4 @json

```blade
<script>var data = @json($array);</script>
```

编译为 `echoRaw(writer, json_encode(array))`，不转义（输出原始 JSON）。

## 10. 表达式编译规则

### 10.1 表达式上下文

编译器区分两种上下文：

| 上下文 | 方法 | 用途 | 特殊处理 |
|---|---|---|---|
| 条件上下文 | `compileConditionExpression` | `@if` / `@elseif` | 简单变量自动包装 `toBoolean()` |
| 输出上下文 | `compileOutputExpression` | `{{ }}` | 直接输出值，不包装 |

### 10.2 运算符优先级（从高到低）

```
->  ?->  []  ()        // 属性/方法/下标/调用
!   -   +   ++  --     // 一元
*   /   %              // 乘除取模
+   -   .              // 加减拼接
<   >   <=  >=         // 比较
==  !=  <>  === !==    // 相等
&&  and               // 逻辑与
||  or                // 逻辑或
? :                    // 三元
?:                     // Elvis
??                     // 空合并
=   .=  +=  -=  *=  /= // 赋值
```

### 10.3 内置函数映射

| 表达式 | 编译目标 | 说明 |
|---|---|---|
| `route('name')` | `route("name")` | 路由别名 → URL（依赖 BladeFunctions 注册） |
| `asset('path')` / `{{ asset('path') }}` | `asset("path")` → `url("path")` → `/path` | **不附加任何前缀** |
| `URL::asset('path')` | `asset("path")` | 同上 |
| `Carbon::parse($date)` | `carbonParse(date)` | 解析日期 |
| `Carbon::today()` / `Carbon::now()` | `carbonToday()` | 当前日期 |
| `csrf_field()` | 运行时方法，输出 `<input>` | 原样输出（不转义） |
| `csrf_token()` | 运行时方法，返回 token 字符串 | — |
| `isset($a)` | `isset(a)` | 判断变量是否已设置 |
| `empty($var)` | `empty(var)` | 空值检查 |
| `count($items)` | `count(items)` | 计数 |
| `intval($x)` | `intval(x)` | 转整数 |
| `json_encode($x)` | `json_encode(x)` | JSON 编码 |
| `number_format($x)` | `number_format(x)` | 数字格式化 |
| `strtoupper/strtolower/ucfirst/trim/strlen($s)` | 同 Java 方法 | — |
| `e($x)` | `e(x)` | HTML 转义（等价 `{{ $x }}`） |
| `old('key', 'default')` | `old("key", "default")` | 旧输入值 |
| `session('key')` | `session("key")` | 获取 session |
| `sprintf('%s', $x)` | `sprintf("%s", x)` | 格式化字符串 |
| `str_replace('a', 'b', $s)` | `str_replace("a", "b", s)` | 字符串替换 |
| `implode(',', $arr)` | `implode(",", arr)` | 数组连接 |
| `ceil($x)` / `floor($x)` | `ceil(x)` / `floor(x)` | 取整 |
| `__($key)` / `trans($key)` | `callOrDefault("__", key, key)` | 翻译（若未注册则返回 key） |
| `myFunc($a, $b)` | `fn("myFunc", a, b)` | 调用 BladeFunctions 动态注册函数 |

### 10.4 数组字面量

| 语法 | 编译目标 |
|---|---|
| `[1, 2, 3]` | `list(1L, 2L, 3L)` |
| `['a' => 1, 'b' => 2]` | `map("a", 1L, "b", 2L)` |
| `[1, 2,]`（尾逗号） | 允许，忽略尾逗号 |
| `array(1, 2)` | 等价 `[1, 2]` |

### 10.5 字符串插值

单引号 `'text'`：直接转为 Java 双引号 `"text"`，支持 `\'` `\\` 转义。

双引号 `"text $var {$expr}"`：
- `$var` → `v("var")`
- `{$expr}` → 内嵌表达式（递归翻译）
- 最终转为 `concat("text", v("var"), expr)` 或纯字面量

## 11. 运行时辅助函数（PHP 风格）

`BladeTemplate` 提供一系列 Java 辅助方法，编译时自动调用。

| 方法 | 对齐 PHP | 说明 |
|---|---|---|
| `route(name)` | `route('name')` | 按路由别名生成 URL（未注册时降级为 `/name`） |
| `route(name, params)` | `route('name', ['key' => val])` | 带参路由 URL |
| `asset(path)` | `asset('path')` | 静态资源 URL（与 `url()` 等价） |
| `url(path)` | `url('path')` | 生成 URL（`null/空 → "/"`；以 `/` 开头 → 原样） |
| `session(key)` | `session('key')` | 获取 session 值 |
| `old(key)` / `old(key, default)` | `old('key', 'default')` | 获取旧输入值 |
| `csrf_field()` | `csrf_field()` | CSRF 隐藏字段（依赖 `VerifyCsrfToken` 中间件） |
| `csrf_token()` | `csrf_token()` | CSRF token 字符串 |
| `getProperty(obj, name)` | `$obj->name` | 反射获取属性 |
| `invokeMethod(obj, name, args)` | `$obj->method(args)` | 反射调用方法 |
| `getMapValue(obj, key)` | `$obj['key']` | Map/数组下标访问 |
| `elvis(a, b)` | `$a ?: $b` | Elvis 运算符 |
| `nullCoalescing(a, b)` | `$a ?? $b` | 空合并运算符 |
| `concat(parts...)` | `'a' . 'b'` | 字符串拼接 |
| `empty(obj)` | `empty($var)` | 空值检查（null/空串/空集合/0） |
| `intval(obj)` | `intval($var)` | 转整数 |
| `json_encode(obj)` | `json_encode($var)` | JSON 编码 |
| `count(obj)` | `count($var)` | 计数（null→0；Collection/Map/Array→size；Number→intValue；其他→1） |
| `sprintf(fmt, args...)` | `sprintf(...)` | 格式化字符串 |
| `str_replace(search, replace, subject)` | `str_replace(...)` | 字符串替换 |
| `implode(glue, obj)` | `implode(...)` | 数组连接 |
| `ceil(val)` / `floor(val)` | `ceil/floor` | 取整 |
| `carbonParse(date)` | `Carbon::parse($date)` | 解析日期为 `LocalDateTime` |
| `carbonToday()` | `Carbon::today()` | 当前日期（`LocalDate`） |
| `carbonYear(date)` | `Carbon::today()->year` | 年份 |
| `plus/minus/mul/div(a, b)` | `+/-/*/` | 算术运算 |
| `eq/neq/identical/notIdentical(a, b)` | `==/!=/===/!==` | 相等比较 |
| `lt/gt/lte/gte(a, b)` | `<`/`>`/`<=`/`>=` | 大小比较 |
| `toBoolean(obj)` | — | 对象转布尔 |
| `toPairs(obj)` | — | 对象转 `List<Object[]>`（供 foreach 使用） |
| `setVar(name, value)` | — | 设置模板变量 |
| `v(name)` | — | 获取模板变量（`ctx.getVariable(name)`） |
| `hasSection(name)` | — | 判断 section 是否存在 |
| `sectionMissing(name)` | — | 判断 section 是否缺失 |
| `renderComponent(writer, name, data, slots)` | — | 渲染组件 |
| `includeTemplate(writer, name, data)` | — | 渲染子视图 |
| `evalDirective(name, args)` | — | 执行动态指令 |
| `evalCondition(name, args)` | — | 求值条件指令 |
| `e(value)` | `e($value)` | HTML 转义 |
| `echo(writer, value)` | — | 转义输出 |
| `echoRaw(writer, value)` | — | 原样输出 |

### 11.1 特殊行为

- `e()` 对 `Htmlable` 的处理：实现 `Htmlable` 接口的对象直接调 `toHtml()`，不转义
- `count(null)` 返回 0（PHP 语义）
- `toPairs()` 对 null 返回空列表


## 12. 自定义扩展机制

### 12.1 BladeFunctions（动态函数）

```java
// 注册自定义函数
BladeFunctions.register("upper", args -> String.valueOf(args[0]).toUpperCase());
BladeFunctions.register("route", args -> Route.url((String) args[0]));
BladeFunctions.merge(Map.of("myFunc", args -> "result"));
```

模板中：`{{ upper($name) }}`、`{{ myFunc($a, $b) }}`。

**未注册函数**：编译时生成 `fn("name", args...)`；**运行时**若未注册则抛 `IllegalStateException("jblade 模板函数未注册: name...")`。

### 12.2 BladeDirectives（自定义指令）

两类扩展：

#### 条件指令 `@xxx ... @endxxx`
```java
BladeDirectives.condition("admin", args -> auth.isAdmin());
```
模板：`@admin ... @endadmin`，编译为 `if (evalCondition("admin")) { ... }`。

#### 输出指令 `@xxx(args)`
```java
BladeDirectives.directive("datetime", args -> new SimpleDateFormat("yyyy-MM-dd").format(args[0]));
```
模板：`@datetime($date)`，编译为 `write(writer, evalDirective("datetime", date))`。

### 12.3 @RegisterDirective 注解驱动

在 `@Configuration` 类中：
```java
@Configuration
public class DirectiveConfig {
    @RegisterDirective("datetime")
    public BladeDirectives.Handler datetime() { ... }

    @RegisterDirective(value = "admin", condition = true)
    public BladeDirectives.Condition admin(AuthManager auth) { ... }
}
```
由 `BladeDirectiveRegistrar` 在 `SpringBootRouteAutoConfiguration` 中扫描注册。

**编译期必须已知**指令类型（条件 vs 输出），因此必须在模板编译前注册。


## 13. PJAX / Wire 模板约定

### 13.1 PJAX 分段标记

当 `BladeEngine.renderPjax()` 渲染时，会设置 `context.setVariable("__pjax_mode", true)`，
使 `@yield` 输出附加 HTML 注释分段标记：

```
<!--pjax:start:NAME-->...内容...<!--pjax:end:NAME-->
```

**模板编写约定**：布局模板中需要被 PJAX 无感替换的区域用 `@yield('区域名')` 标识，
前端按区域名 diff 并替换。

`renderPjax` 的返回值 `PjaxRenderResult` 包含：
- `html`：整页 HTML
- `meta`：`TemplateRegionMeta`（`parentTemplate`、`yieldNames`、`sectionNames`）
- `title`：从 `<title>` 区域抽取的标题

### 13.2 Wire 分段标记

当 context 中存在 `__wire_mode` 真值时，`@yield` 附加 wire 标记：

```
<!--wire:section-start:NAME-->...内容...<!--wire:section-end:NAME-->
```

### 13.3 wire_outlet() 辅助函数

wire 模块在启动时注册：
```java
BladeFunctions.register("wire_outlet", args -> WireOutlet.outletTag(request));
```

模板中：`{!! wire_outlet() !!}` 输出 `<div id="..." wire:outlet data-wire-outlet="..."></div>`。
WireOutlet 中间件检测到已有 `wire:outlet` 标记时不重复注入。

### 13.4 Wire 组件模板约定

- 组件模板可使用 `{{ $wireId }}` 和 `{{ $wireName }}` 变量（由 `WireComponentRenderer` 注入）
- 生命周期脚本写在 `<script wire:lifecycle>...</script>` 内，渲染器会**抽离**到 payload 的 `script` 字段
- 前端 `new Function` 逐实例求值，实现同页多实例隔离
- 模板加载位置：默认 `WireOutlet` 注入到 `</body>` 前；也可模板内 ` {!! wire_outlet() !!}` 指定

## 14. 配置项

全部配置 key 前缀：`jaravel.view.*`

| 配置 key | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `jaravel.view.template-dir` | String | `templates` | 模板根目录（classpath 相对路径） |
| `jaravel.view.suffix` | String | `.jblade` | 模板文件后缀 |
| `jaravel.view.default` | String | `""` | 激活的 View 实现名；空则按注解/兜底判定 |
| `jaravel.view.asset-url-prefix` | String | `/static` | 静态资源 URL 前缀（**不影响 `@asset`**，见 §11） |

**发布配置**：`artisan vendor:publish --tag=jblade` 生成 `JbladeConfig`，内含上述 4 个 key。

**不存在的配置**：
- **无**缓存开关（一级内存缓存始终启用）
- **无**热重载/文件监听配置
- **无**编译模式切换配置（只能代码切换）

## 15. 编译缓存与热更新

### 15.1 缓存架构

| 缓存层 | 实现 | 生命周期 | 是否可配置 |
|---|---|---|---|
| 一级（内存） | `ConcurrentHashMap<String, Class<?>>` | JVM 进程内 | **始终启用，不可关闭** |
| 二级（可选） | `CacheStore`（Redis/Array 等） | 由实现决定 | 引入 cache 模块自动启用 |

缓存 key：
- 一级：`templateName`（已剥离引号）
- 二级：`"jblade:template:" + templateName`

### 15.2 缓存控制 API

| 方法 | 作用 |
|---|---|
| `BladeEngine.clearCache()` | 清除一级 + 二级（逐个 forget，不调用 flush）+ 实例缓存 |
| `BladeEngine.clearTemplate(name)` | 仅清除指定模板的一级 + 二级 + 实例 |
| `BladeEngine.clearTemplateInstanceCache()` | 仅清除实例缓存 |
| `BladeEngine.clearRegionMeta()` | 清除 PJAX 区域元数据缓存 |

### 15.3 Artisan 命令

| 命令 | 作用 |
|---|---|
| `view:cache` | 编译全部模板并写入缓存，同时生成预编译包 `storage/framework/views/templates.jblade.zip` |
| `view:clear` | 清除全部模板编译缓存与 PJAX 区域元数据 |

### 15.4 重要事实

- **无热重载**：模板文件修改后**不会自动重新编译**，必须手动调用 `clearCache()` 或执行 `view:clear`
- **无 mtime 校验**：缓存判断只看 key，不比较文件时间戳
- **实例缓存**：每次 `loadTemplate()` 都会 `newInstance()` 新实例（不共享状态）

## 16. 预编译模式

### 16.1 设计目的

运行时编译（`javax.tools.JavaCompiler`）需要 JDK。预编译模式允许**生产环境仅使用 JRE**。

### 16.2 编译模式

| CompileMode | 产物 |
|---|---|
| `PACKAGED` | 单个 `.jblade.zip` 文件（内含 manifest.txt + 按包结构的 `.class`） |
| `CLASSES` | 散乱 `.class` 文件输出到目录 + `manifest.txt` |

### 16.3 manifest.txt 格式

```
# jblade precompiled template manifest
# format: templateName=className
docs.index=Blade_docs_index
layouts.app=Blade_layouts_app
```

- UTF-8；空行与 `#` 开头视为注释
- `=` 分隔，key/value 各自 trim
- manifest 可缺失（映射为空）

### 16.4 类名生成规则

```java
"Blade_" + templateName.replace(File.separator, "_")
                       .replace("/", "_")
                       .replace("\\", "_")
                       .replace(".", "_")
                       .replace("-", "_");
```

| 模板名 | 生成类名 |
|---|---|
| `welcome` | `Blade_welcome` |
| `docs.index` | `Blade_docs_index` |
| `layouts.mdui.page-inator` | `Blade_layouts_mdui_page_inator` |

### 16.5 加载工厂方法

```java
// 从打包文件
BladeEngine engine = BladeEngine.fromPrecompiledPackage("path/to/templates.jblade.zip");

// 从 class 目录
BladeEngine engine = BladeEngine.fromPrecompiledClasses("path/to/classes");
```

预编译模式下 `templateDir` 为空串，**不查询文件系统**。

### 16.6 预编译 CLI 工具

```bash
java -cp ... BladePrecompilerMain \
  --template-dir=templates \
  [--suffix=.jblade] \
  [--output-dir=precompiled] \
  [--mode=packaged|classes] \
  [--package-name=templates] \
  [--file-suffix=.jblade.zip]
```

退出码：失败或发现 0 个模板 → `exit(1)`。

### 16.7 ViewCache

`ViewCache.rebuild()` 流程：
1. 清缓存（一级 + 二级 + regionMeta）
2. 扫描所有模板名（`scanTemplateNames()`）
3. 逐个 `loadTemplate()` 触发编译并缓存
4. 输出预编译包到 `storage/framework/views/templates.jblade.zip`

**插件应忽略的生成目录/文件**：
```
**/target/**                       (Maven 复制产物)
**/storage/framework/views/**      (view:cache 产物)
**/precompiled/**                  (CLI 默认输出)
**/*.jblade.zip
**/manifest.txt
```

## 17. 校验规则清单（插件诊断依据）

插件可做编译期静态校验，触发时输出以下错误消息（与运行期完全一致）：

### 17.1 语法结构错误

| 错误类型 | 触发条件 | 错误消息模板 |
|---|---|---|
| 空模板 | 源内容为空 | `源代码不能为空` |
| 未闭合的指令 | 存在未匹配的 `@xxx ... @endxxx` | `模板 [name] 存在未闭合的 @kind('name')` |
| 未闭合的控制块 | frames 非空 | `模板 [name] 存在未闭合的控制指令 @type` |
| 表达式编译失败 | `PhpExpressionTranslator` 抛异常 | `模板 [name] 表达式编译失败: expr`（附 cause） |
| 循环嵌套不匹配 | `@endforelse` 与 `@foreach`/`@forelse` 不匹配 | `模板 [name] @endforelse 与 @type 不匹配` |
| @for 参数错误 | 非三段式 | `模板 [name] @for 参数格式错误: args` |
| @foreach 格式错误 | 不满足正则 | `模板 [name] @foreach 参数格式错误: args` |
| 循环控制外 continue/break | （无校验，允许） | — |

### 17.2 指令位置错误

| 错误类型 | 触发条件 | 错误消息模板 |
|---|---|---|
| @parent 位置错误 | 不在 `@section` 块内 | `模板 [name] @parent 只能出现在 @section 内` |
| @slot 位置错误 | 不在 `@component` 块内 | `模板 [name] @slot 只能出现在 @component 内` |
| 缺少对应开始指令 | `@endxxx` 找不到匹配的 `@xxx` | `模板 [name] @xxx 没有对应的开始指令` |
| 缺少对应 section | `@endsection/@stop/@append/@overwrite/@show` 无 `@section` | `模板 [name] @name 没有对应的 @section` |
| 缺少对应 component | `@endcomponent` 无 `@component` | `模板 [name] @endcomponent 没有对应的 @component` |
| 缺少对应 slot | `@endslot` 无 `@slot` | `模板 [name] @endslot 没有对应的 @slot` |

### 17.3 参数类型错误

| 错误类型 | 触发条件 | 错误消息模板 |
|---|---|---|
| 名称参数非字面量 | `@extends/@section/@component/@slot` 传入非字符串字面量 | `模板 [name] directive 的名称参数必须是字符串字面量: arg` |
| 表达式语法错误 | `PhpExpressionTranslator` 各类语法错误 | `jblade 表达式语法错误：...，表达式: source` |
| 函数参数不足 | 内置函数调用参数不够 | `jblade：函数 name 需要 count 个参数` |

### 17.4 运行时错误（渲染期）

| 错误类型 | 触发条件 | 错误消息模板 |
|---|---|---|
| 模板继承循环 | 继承链出现环 | `模板继承出现循环: visited -> parentName` |
| init 失败 | `BladeTemplate.init()` 抛异常 | `模板 init 失败: name` |
| Section 渲染失败 | `registerSection` 的 Consumer 抛异常 | `渲染 section 失败: name` |
| 子视图/组件缺引擎 | `engine` 为 null | `BladeEngine not set for template` |
| 函数未注册 | 调用未注册的 BladeFunctions | `jblade 模板函数未注册: name。请通过 BladeFunctions.register(...) 注册。` |

### 17.5 PHP 表达式语法校验

| 错误类型 | 触发条件 | 错误消息模板 |
|---|---|---|
| 期望操作符 | `parse` 过程中期望 `op` 实际收到 `peek.text` | `jblade 表达式语法错误：期望 'op'，实际 'peek.text'，表达式: source` |
| 多余内容 | 顶层表达式解析完后仍有剩余 token | `jblade 表达式语法错误：多余内容 'peek.text'，表达式: source` |
| 前缀 ++/-- 后必须为变量 | 非变量 | `jblade 表达式语法错误：++/-- 后必须是变量，表达式: source` |
| `->` 后必须为成员名 | 非 `T_IDENT` | `jblade 表达式语法错误：-> 后必须是属性/方法名，表达式: source` |
| 后缀 `++/--` 只支持变量 | 非 `v("...")` 形式 | `jblade 表达式语法错误：++/-- 只支持变量，表达式: source` |
| `::` 后必须为成员名 | 非 `T_IDENT` | `jblade 表达式语法错误：:: 后必须是成员名，表达式: source` |
| 意外的 token | `parsePrimary` 遇到无法识别的 token | `jblade 表达式语法错误：意外的 'text'，表达式: source` |

### 17.6 未知的 @xxx 不是错误

**重要**：未注册的 `@xxx`（如 CSS 的 `@media`、JS 中的 `@` 文本）会被**原样输出**，不报错误。
插件**不应**将未知 `@directive` 报为语法错误。

## 18. 反直觉/易错点清单

### 18.1 模板名中的点号

- `templateName.replace(".", File.separator)` 是**字面量全量替换**
- 模板名 `foo.bar.baz` → 路径 `foo/bar/baz.jblade`
- **禁止**在模板名/文件名中使用额外的点（除目录分隔外）

### 18.2 @asset 不使用 BladeAssetHelper

**重要更正**：`@asset('path')` **不**调用 `BladeAssetHelper.url()`，等价于 `url('path')`。

- `jaravel.view.asset-url-prefix` 配置项**实质上是死配置**（除非应用自行注册 `BladeFunctions.register("asset", ...)`）
- `@asset('css/app.css')` → `/css/app.css`（**不加 `/static` 前缀**）
- `BladeAssetHelper` 的 Javadoc 是过时的文档

### 18.3 文件系统查找根是进程 CWD

- 第一优先查找路径是 `./resources/<templateDir>/...`（相对于**进程 CWD**）
- 不是 `src/main/resources/templates/`
- 这在 IDE 开发时可能产生困惑：模板文件必须在项目根下的 `resources/templates/` 或 classpath 中

### 18.4 无热重载/文件监听

- 一级内存缓存**始终启用、不可关闭**
- **没有任何热重载机制**
- 模板文件修改后必须手动调用 `clearCache()` 或执行 `artisan view:clear`
- 插件不应假设模板改动会自动生效

### 18.5 模板名引号剥离

- `BladeEngine` 在接收模板名前会**无条件删除所有 `'` 和 `"`**
- `@extends('layouts.app')` 和 `@extends("layouts.app")` 完全等价
- 插件在做跳转时应按此语义处理引号

### 18.6 BladeEngine 没有 share()/make()/exists()

- **不存在** `BladeEngine.share(...)` 方法
- **不存在** `BladeEngine.make(...)` 方法（渲染入口只有 `render`/`renderSection`/`renderSections`/`renderPjax`）
- `exists(...)` 只在 `BladeView`（实现 `View` 接口）上存在，`BladeEngine` 上叫 `templateExists(String)`
- 变量注入的唯一途径是 `render(name, Map)` 的 `variables` 参数

### 18.7 @parent 占位符机制

- `@parent` 在子模板中**不直接展开**父内容
- 而是生成占位字符串 `@__jblade_parent__@`
- 渲染时父模板注册同名 section 会先调 `extendSection`，把占位符替换为父内容
- 最终 `yieldSection` 时再剥离残留的占位符

### 18.8 Section 注册的优先级

三种模式（`@endsection` / `@append` / `@overwrite`）行为不同：

| 模式 | 行为 |
|---|---|
| `@endsection`（默认） | 子模板优先 + `@parent` 占位符合并（`extendSection`） |
| `@append` | 追加到已有同名 section 末尾 |
| `@overwrite` | 覆盖已有同名 section |

### 18.9 未知的 @xxx 不是错误

- 未注册的 `@xxx`（如 CSS 的 `@media`、JS 中的 `@` 文本）会被**原样输出**
- 插件**不应**将未知 `@directive` 报为语法错误

### 18.10 插件应忽略的生成目录/文件

```
**/target/**                       (Maven 会把 .jblade 复制到 target/classes/templates/)
**/storage/framework/views/**      (view:cache 产物)
**/precompiled/**                  (CLI 默认输出)
**/*.jblade.zip
**/manifest.txt                    (仅在上述产物目录内)
```

### 18.11 控制器渲染模板的标准写法

```java
// 正确写法
Map<String, Object> data = new HashMap<>();
data.put("title", "首页");
data.put("users", List.of("Alice", "Bob"));
return ResponseBuilder.view("index", data);

// 模板中引用
{{ $title }}
@foreach($users as $user)
    {{ $user }}
@endforeach
```

### 18.12 $loop 变量的访问限制

- `$loop` 只能在 `@foreach` / `@forelse` 块内部访问
- 通过反射调用 `prop(name)` 获取属性
- 嵌套循环会压栈/出栈（`pushLoop` / `popLoop`）

### 18.13 PJAX 模式标记

- `renderPjax()` 设置 `context.setVariable("__pjax_mode", true)`
- `@yield` 输出附加 HTML 注释分段标记 `<!--pjax:start:NAME-->...<!--pjax:end:NAME-->`
- `renderSection()` 可设置 `__wire_mode` 输出 wire 标记

### 18.14 wire_outlet() 辅助函数

- wire 模块在启动时注册 `BladeFunctions.register("wire_outlet", ...)`
- 模板中 ` {!! wire_outlet() !!}` 输出 `<div wire:outlet ...>` 加载位置容器
- WireOutlet 中间件检测到已有 `wire:outlet` 标记时不重复注入

### 18.15 编译生成的 Java 类结构

每个模板编译为：
```java
public class Blade_<templateName> extends BladeTemplate {
    @Override
    public void init() { ... }  // 注册 section 渲染器
    @Override
    public void render(Writer writer) throws Exception { ... }
}
```

- 不含 `package` 声明（默认包）
- 自动导入：`com.weacsoft.jaravel.vendor.jblade.*`, `java.io.*`, `java.util.*`, `java.util.function.*`
## 19. IDE Ctrl+Click 跳转规范

插件需要支持在 `.jblade` 模板中按住 **Ctrl（或 Cmd）+ 点击** 跳转到目标文件。以下是所有支持跳转的指令及目标解析规则。

### 19.1 跳转指令总览

| 指令 | 参数位置 | 跳转目标 | 说明 |
|---|---|---|---|
| `@extends('name')` | 第 1 参 | 父模板文件 | 跳转到继承的布局模板 |
| `@include('name')` | 第 1 参 | 子视图文件 | 跳转到被包含的视图 |
| `@includeIf('name', ...)` | 第 1 参 | 子视图文件 | 同上 |
| `@includeWhen(cond, 'name', ...)` | 第 2 参 | 子视图文件 | 同上 |
| `@includeUnless(cond, 'name', ...)` | 第 2 参 | 子视图文件 | 同上 |
| `@component('name', ...)` | 第 1 参 | 组件模板文件 | 跳转到组件模板 |
| `@yield('name')` | 无跳转 | — | 显示父模板中可用的 section 列表 |
| `@section('name')` | 无跳转 | — | 显示当前布局中定义的 section |

### 19.2 @extends 跳转规则

**语法**：`@extends('template.name')` 或 `@extends("template.name")`

**跳转目标解析**：
1. 提取模板名（去除引号）
2. 按 §2.1 映射公式转换为文件系统路径
3. 在以下位置查找目标文件：
   - `<contentRoot>/src/main/resources/<template-dir>/template/name.jblade`
   - 若项目根为 CWD，同时支持 `resources/<template-dir>/...`
4. 高亮显示：跳转到该文件

**编辑器提示**：
- 提供 **模板名补全**：扫描 `<template-dir>/` 下所有 `.jblade` 文件，列出可用模板名
- 支持 **部分匹配**：输入 `layout` 时显示 `layouts.app`、`layouts.base` 等
- 标注 **继承链层级**：在补全结果中标注当前模板继承自哪个父模板

### 19.3 @include 跳转规则

**语法**：
```blade
@include('template.name')
@include('template.name', ['key' => $value])
@includeIf('template.name', ['key' => $value])
@includeWhen($cond, 'template.name', ['key' => $value])
@includeUnless($cond, 'template.name', ['key' => $value])
```

**跳转目标解析**：
- `@include` / `@includeIf`：第 1 参是模板名 → 跳转目标
- `@includeWhen` / `@includeUnless`：第 2 参是模板名 → 跳转目标

**编辑器提示**：
- 同样提供模板名补全
- 若传入变量（如 `@include($variable)`），**无法静态分析**，不显示跳转

### 19.4 @component 跳转规则

**语法**：
```blade
@component('components.card')
@component('components.card', ['level' => 4])
```

**跳转目标解析**：
- 第 1 参是组件模板名 → 跳转目标文件
- **注意**：组件名不带 `components.` 前缀，直接使用模板名

### 19.5 跳转目标文件定位算法

```
1. 提取模板名（去除前后引号）
2. 将 '.' 替换为 File.separator
3. 拼接后缀：path + suffix（默认 .jblade）
4. 在以下位置按顺序查找：
   a. <contentRoot>/src/main/resources/<template-dir>/<path>.jblade
   b. <contentRoot>/resources/<template-dir>/<path>.jblade
   c. classpath: <template-dir>/<path>.jblade
5. 返回找到的第一个文件路径
```

**插件实现注意**：
- 模板名 `foo.bar.baz` → 文件 `foo/bar/baz.jblade`
- 后缀可能通过 `jaravel.view.suffix` 配置，插件应读取配置或默认 `.jblade`
- 若找不到目标文件，**不报错**，仅不显示跳转（与运行时行为一致）

### 19.6 @yield 悬停提示

当鼠标悬停在 `@yield('section_name')` 上时，插件应：
1. 向上查找最近的 `@extends` 指令，获取父模板名
2. 解析父模板文件
3. 扫描父模板中的所有 `@section('section_name')` 或 `@section('section_name', expr)`
4. 在悬停提示中列出：
   - 已定义的 section 名称
   - 所在文件的行号

### 19.7 跳转失败处理

以下情况插件应**静默忽略**（不报错）：

| 场景 | 原因 |
|---|---|
| 模板名不是字面量 | 如 `@include($var)`，无法静态分析 |
| 目标文件不存在 | 模板尚未创建或路径配置不同 |
| 模板名含运行时变量 | 如 `@include('partials.' + $type)` |
| 指令在 `@verbatim` 块内 | 不执行，是纯文本 |

---

## 20. 插件开发检查清单

基于 §17 校验规则和 §19 跳转规范，插件应实现以下能力：

### 20.1 语法高亮
- [ ] Blade 注释 `{{-- --}}` 使用注释颜色
- [ ] `{{ }}` / `{!! !!}` 使用表达式颜色
- [ ] `@directive` 使用关键字颜色
- [ ] HTML 标签保持 HTML 语法树高亮

### 20.2 智能补全
- [ ] `@extends('` 后列出所有可用模板名
- [ ] `@include('` 后列出所有可用模板名
- [ ] `@component('` 后列出所有可用模板名
- [ ] `@yield('` 后列出父模板中已定义的 section 名
- [ ] `@section('` 后列出当前模板中已定义的 section 名

### 20.3 导航跳转
- [ ] Ctrl+Click `@extends('name')` → 跳转到父模板文件
- [ ] Ctrl+Click `@include('name')` → 跳转到子视图文件
- [ ] Ctrl+Click `@component('name')` → 跳转到组件模板文件
- [ ] 悬停 `@yield('name')` → 显示父模板中已定义的 section 列表

### 20.4 错误检查
- [ ] `@if` 缺少 `@endif`
- [ ] `@foreach` 缺少 `@endforeach`
- [ ] `@section` 缺少 `@endsection`
- [ ] `@component` 缺少 `@endcomponent`
- [ ] `@slot` 不在 `@component` 块内
- [ ] `@extends` 参数非字面量

### 20.5 文件关联
- [ ] `.jblade` 文件类型关联为 **"Blade 模板"**
- [ ] 默认语言模式为 **HTML**（主语言）
- [ ] Blade 指令叠加为嵌入式语言

---

