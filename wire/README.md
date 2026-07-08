# wire 模块

> Jaravel-Vendor 的全栈响应式 UI 框架模块，实现 Laravel Livewire 风格的服务端渲染 + 前端局部更新。包名统一为 `com.weacsoft.jaravel.vendor.wire`，包含 `WireService`、`WireResponse`、`WireRequest`、`WireManager` 四个核心类，以及一个零依赖的前端运行时 `wire.js`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 目录结构](#2-目录结构)
- [3. 依赖信息](#3-依赖信息)
- [4. 类总览](#4-类总览)
- [5. WireService 流式 API](#5-wireservice-流式-api)
  - [5.1 三步流程](#51-三步流程)
  - [5.2 from / of —— 创建上下文](#52-from--of--创建上下文)
  - [5.3 once —— 默认值填充](#53-once--默认值填充)
  - [5.4 action —— 注册处理器](#54-action--注册处理器)
  - [5.5 set / update / remove —— 直接操作数据](#55-set--update--remove--直接操作数据)
  - [5.6 getInt / getStr / getList —— 类型化读取](#56-getint--getstr--getlist--类型化读取)
  - [5.7 responseWire / responseUpdate / responseOf —— 生成响应](#57-responsewire--responseupdate--responseof--生成响应)
  - [5.8 toData / toSections —— 获取中间结果](#58-todata--tosections--获取中间结果)
- [6. WireResponse 语义化响应](#6-wireresponse-语义化响应)
  - [6.1 wire —— 初始页面渲染](#61-wire--初始页面渲染)
  - [6.2 update —— 部分更新](#62-update--部分更新)
  - [6.3 redirect —— Wire 重定向（支持 delay）](#63-redirect--wire-重定向支持-delay)
  - [6.4 error —— 错误响应](#64-error--错误响应)
  - [6.5 of —— 全能构建器](#65-of--全能构建器)
  - [6.6 withRedirect / withDispatch / withError / build](#66-withredirect--withdispatch--witherror--build)
  - [6.7 响应格式](#67-响应格式)
- [7. WireRequest 请求解析](#7-wirerequest-请求解析)
- [8. WireManager 管理器](#8-wiremanager-管理器)
- [9. wire.js 前端运行时](#9-wirejs-前端运行时)
  - [9.1 wire:click / wire:submit / wire:change / wire:keydown](#91-wireclick--wiresubmit--wirechange--wirekeydown)
  - [9.2 wire:model / wire:model.live / wire:model.lazy](#92-wiremodel--wiremodellive--wiremodellazy)
  - [9.3 wire:section 机制](#93-wiresection-机制)
  - [9.4 snapshot 机制](#94-snapshot-机制)
  - [9.5 wire:target / wire:loading / wire:update](#95-wiretarget--wireloading--wireupdate)
- [10. 认证过期无感重定向](#10-认证过期无感重定向)
- [11. 完整控制器示例](#11-完整控制器示例)
- [12. 线程安全说明](#12-线程安全说明)

---

## 1. 模块概述

`wire` 模块对齐 Laravel Livewire，是一种全栈响应式 UI 框架，核心理念是「服务端渲染 + 前端局部更新」：

| Livewire 特性 | wire 对应实现 | 说明 |
| --- | --- | --- |
| 组件状态（snapshot） | `WireManager.encodeSnapshot / decodeSnapshot` | 组件状态以 Base64 JSON 编码在客户端流转，服务端无状态 |
| 部分更新（diff） | `WireResponse.update` + `wire.js replaceSection` | 仅返回变化的 section HTML，前端局部替换 DOM |
| wire:model 双向绑定 | `wire.js bindModel` | 默认防抖 150ms；`.live` 实时同步；`.lazy` 延迟到 change |
| wire:click / wire:submit | `wire.js bindClick / bindSubmit` | 自动扫描 `wire:` 属性并绑定事件 |
| wire:redirect | `WireResponse.redirect` | 返回 JSON，前端自动 `window.location.href` 跳转 |
| Livewire 的 snapshot 机制 | snapshot 注入到 `<script wire:config>` | 渲染时注入，更新时回传 |

**核心设计原则**：服务端无状态。`WireManager` 是工具类，所有组件状态通过 `snapshot`（Base64 JSON）在客户端 `<script wire:config>` 中流转。服务端不需要维护组件实例，天然支持水平扩展。

**两种使用模式**：

1. **流式模式（推荐）**：使用 `WireService.from(...)` 链式调用，一行代码完成「解析请求 → 填充默认值 → 注册 action → 生成响应」。
2. **显式模式**：在控制器中手动调用 `WireRequest.from`、`WireManager.renderSections`、`WireResponse.update` 等方法，灵活组合。

---

## 2. 目录结构

```
com.weacsoft.jaravel.vendor.wire
├── WireService          // 流式上下文：串联请求解析、默认值填充、action 分派、响应构建
├── WireResponse         // 语义化响应构建器：wire/update/redirect/error/of
├── WireRequest          // 请求解析：从前端 POST 的 JSON 中解析 snapshot/action/params/sections
└── WireManager          // 核心工具类：Wire 模式渲染、section 提取、snapshot 编解码、资源注入

resources/static/
└── wire.js              // 前端运行时：事件绑定、局部更新、双向绑定、认证过期处理（零依赖）
```

---

## 3. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>wire</artifactId>
    <version>0.1.1</version>
</dependency>
```

### 模块依赖

| 依赖模块 | scope | 用途 |
| --- | --- | --- |
| `jblade` | compile | Blade 模板引擎，用于渲染模板与 section（`WireManager` 内部调用 `BladeEngine`） |
| `http` | compile | 提供 `Request` / `Response` / `ResponseBuilder`，Wire 请求解析与响应构建基础 |

### 传递依赖

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `com.fasterxml.jackson.core:jackson-databind` | compile | JSON 解析（snapshot 编解码、Wire 请求体解析） |
| `org.springframework:spring-webmvc` | compile | `MultipartFile` 等基础类型 |

> 运行环境要求：JDK 17+，Spring Boot 3.2.5（Jakarta Servlet）。使用前需通过 `WireManager.setEngine(bladeEngine)` 注入 Blade 引擎实例（通常由 `springboot` 模块的 Starter 自动完成）。

---

## 4. 类总览

| 类 | 职责 | 典型用法 |
| --- | --- | --- |
| `WireService` | 流式上下文，把请求解析、默认值填充、action 处理、响应构建串联起来 | `WireService.from(request, "demo", "/api/wire/demo").once(...).action(...).responseUpdate()` |
| `WireResponse` | 语义化响应构建器，Wire 控制器的统一响应入口 | `WireResponse.wire(...)` / `WireResponse.update(...)` / `WireResponse.redirect(...)` |
| `WireRequest` | 从前端 POST 的 JSON 中解析 Wire 请求（snapshot + action + params + sections） | `WireRequest.from(request)` |
| `WireManager` | 核心工具类，负责 Wire 模式渲染、section 提取和快照编解码 | `WireManager.renderWirePage(...)` / `WireManager.encodeSnapshot(...)` |

---

## 5. WireService 流式 API

`com.weacsoft.jaravel.vendor.wire.WireService`

Wire 流式上下文，把请求解析、默认值填充、action 处理、响应构建串联起来。设计理念是「控制器一行链式调用搞定，不写 if/switch」。

### 5.1 三步流程

```
第一步：填充请求        第二步：进行各种处理        第三步：生成响应
WireService.from(...)   .once(...)                 .responseWire()
                        .action(...)               .responseUpdate()
                                                   .responseOf()
```

### 5.2 from / of —— 创建上下文

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `from` (static) | `Request request, String templateName, String updateUrl` | `WireService` | 第一步：从 HTTP 请求解析 Wire 上下文。自动解析 snapshot + action + params + sections，合并为 data |
| `of` (static) | `String templateName, String updateUrl, Map<String,Object> data` | `WireService` | 从已有数据创建上下文（用于初始页面渲染，不需要解析请求） |

```java
// 从请求解析（用于 update 接口）
WireService ctx = WireService.from(request, "wire-demo", "/api/wire/demo");

// 从已有数据创建（用于 page 接口，或非请求场景）
Map<String, Object> data = new LinkedHashMap<>();
data.put("count", 0);
WireService ctx = WireService.of("wire-demo", "/api/wire/demo", data);
```

### 5.3 once —— 默认值填充

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `once` | `String key, Object defaultValue` | `WireService` | 如果字段不存在则填入默认值（仅一次），返回 this（链式） |

`once` 是幂等的：第一次请求时字段不存在，填入默认值；后续更新请求中 snapshot 已带该字段，不会覆盖。这正是「初始默认值」与「保留用户修改」的关键。

```java
ctx.once("count", 0)
   .once("message", "")
   .once("items", Arrays.asList("苹果", "香蕉", "橙子"));
```

### 5.4 action —— 注册处理器

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `action` | `String actionName, Consumer<WireService> handler` | `WireService` | 注册 action 处理器。当当前请求的 action 匹配时执行处理器；内部用 Map 存储，注册时不执行，调用 `responseUpdate()` / `responseOf()` / `toData()` 时统一分派 |

```java
ctx.action("increment", c -> c.put("count", c.getInt("count") + 1))
   .action("decrement", c -> c.put("count", c.getInt("count") - 1))
   .action("reset", c -> { c.put("count", 0); c.put("message", ""); })
   .action("addItem", c -> {
       List<Object> items = c.getList("items");
       items.add("项目 " + (items.size() + 1));
   });
```

处理器接收 `WireService` 自身（即 `c`），可直接调用 `put` / `getInt` / `getList` 等方法操作 data。找不到匹配的 action 时静默跳过。

### 5.5 set / update / remove —— 直接操作数据

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `set` | `String key, Object value` | `WireService` | 直接设置字段值（无条件覆盖） |
| `update` | `String key, Function<T,T> updater` | `WireService` | 函数式更新：接收当前值，返回新值 |
| `remove` | `String key` | `WireService` | 删除字段 |

```java
ctx.set("count", 10);
ctx.update("count", oldVal -> oldVal + 1);
ctx.remove("message");
```

### 5.6 getInt / getStr / getList —— 类型化读取

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `get` | `String key` | `Object` | 获取字段值 |
| `get` | `String key, Object defaultValue` | `Object` | 获取字段值，带默认值 |
| `getInt` | `String key` | `int` | 获取 int 类型字段值（安全转换，失败返回 0） |
| `getStr` | `String key` | `String` | 获取 String 类型字段值（null 返回空串） |
| `getList` | `String key` | `List<Object>` | 获取 List 类型字段值（返回可变 List；不存在则创建空 List 并填入；非 List 自动包装） |
| `getData` | 无 | `Map<String,Object>` | 获取原始 data Map（可直接操作） |
| `getAction` | 无 | `String` | 获取当前 action 名称 |

```java
int count = ctx.getInt("count");                  // 安全转换
String msg = ctx.getStr("message");               // null → ""
List<Object> items = ctx.getList("items");        // 不存在则自动创建
items.add("新项目");                                // 直接修改，引用即 data 中的 List
```

> `getList` 的智能包装：若字段是 `Arrays.asList(...)` 等不可变 List，会自动转为可变 `ArrayList` 并回填，避免 `UnsupportedOperationException`。

### 5.7 responseWire / responseUpdate / responseOf —— 生成响应

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `responseWire` | 无 | `Response` | 直接生成 Wire 初始页面响应（HTML）。等同于 `WireResponse.wire(templateName, data, updateUrl)` |
| `responseUpdate` | 无 | `Response` | 分派 action 处理器并生成 Wire 更新响应（JSON）。等同于 `WireResponse.update(templateName, data, sections)`，但自动分派 action |
| `responseOf` | 无 | `WireResponse` | 分派 action 处理器并返回全能构建器，可继续链式调用 `withRedirect` / `withDispatch` / `withError` |

```java
// 方式 A：直接返回更新响应
return ctx.responseUpdate();

// 方式 B：先取数据再自己构建
Map<String, Object> data = ctx.toData();
List<String> sections = ctx.toSections();
return WireResponse.update("wire-demo", data, sections);

// 方式 C：初始页面渲染
return ctx.responseWire();

// 方式 D：全能模式（更新 + 跳转 + 事件）
return ctx.responseOf()
    .withRedirect("/dashboard", 1500)
    .withDispatch("item-updated", Map.of("id", 42))
    .build();
```

### 5.8 toData / toSections —— 获取中间结果

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `toData` | 无 | `Map<String,Object>` | 分派 action 处理器并返回最终的 data Map |
| `toSections` | 无 | `List<String>` | 获取要更新的 section 列表。如果请求中没有指定 sections，则使用模板的默认 section（通过 `WireManager.getSectionNames` 获取） |

```java
Map<String, Object> data = ctx.toData();
List<String> sections = ctx.toSections();
return WireResponse.update("wire-demo", data, sections);
```

---

## 6. WireResponse 语义化响应

`com.weacsoft.jaravel.vendor.wire.WireResponse`

Wire 响应构建器，Wire 控制器的统一响应入口。提供语义化的静态方法，覆盖 Wire 的全部响应场景。

### 6.1 wire —— 初始页面渲染

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `wire` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `Response` | 初始页面渲染：渲染模板 + 注入 Wire 资源（wire.js + snapshot + updateUrl）。返回完整 HTML 页面 |
| `wire` (static) | `String templateName, Map<String,Object> data` | `Response` | 初始页面渲染（使用默认 update URL: `/wire/update`） |

返回的 HTML 包含：
- 模板渲染结果（带 `wire:section` 标记）
- `<script type="application/json" wire:config data-wire-update="..." wire:snapshot="...">` 配置
- `<script src="/static/wire.js">` 前端运行时

```java
public Response page(Request request) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("count", 0);
    return WireResponse.wire("counter", data, "/api/wire/counter");
}
```

### 6.2 update —— 部分更新

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `update` (static) | `String templateName, Map<String,Object> data, List<String> sections` | `Response` | 部分更新响应：渲染指定 section 并返回 JSON。前端 wire.js 收到后自动替换对应 section 的 DOM 内容，并更新 snapshot |

`sections` 为空时使用模板默认 section（通过 `WireManager.getSectionNames` 获取）。

```java
public Response update(Request request) {
    WireRequest wireReq = WireRequest.from(request);
    Map<String, Object> data = wireReq.getMergedData();
    data.put("count", toInt(data.get("count")) + 1);
    return WireResponse.update("counter", data, wireReq.getSections());
}
```

### 6.3 redirect —— Wire 重定向（支持 delay）

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `redirect` (static) | `String url` | `Response` | Wire 重定向：返回 JSON，前端 wire.js 自动执行 `window.location.href = url`。默认立即跳转 |
| `redirect` (static) | `String url, int delayMs` | `Response` | Wire 重定向（带延迟）：前端在延迟指定毫秒后跳转。`delayMs = 0` 表示立即跳转 |

**重点说明**：`redirect` 与传统 HTTP 302 重定向不同，它返回的是 JSON 响应（HTTP 200），由前端 wire.js 读取 `effects.redirect` 字段后执行 `window.location.href`。这种设计使得重定向可以与 section 更新、dispatch 事件组合使用。

`redirect(url, delayMs)` 的延迟参数典型场景：**保存成功后先显示提示消息，延迟 1~2 秒再跳转**。前端会通过 `setTimeout` 延迟执行跳转。

```java
// 立即跳转
return WireResponse.redirect("/items/" + newId);

// 1.5 秒后跳转（先显示"保存成功"提示）
return WireResponse.redirect("/items/" + newId, 1500);
```

### 6.4 error —— 错误响应

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `error` (static) | `int status, String message` | `Response` | Wire 错误响应：返回指定状态码的 JSON。前端 wire.js 对 401 会自动跳转登录页；其他状态码打印到控制台 |
| `error` (static) | `int status, String message, String redirect` | `Response` | Wire 错误响应（带重定向 URL）。用于认证过期场景：返回 401 + redirect URL，前端自动跳转登录页 |

```java
if (!Auth.check()) {
    return WireResponse.error(401, "Unauthorized", "/login");
}
if (!hasPermission()) {
    return WireResponse.error(403, "无权限执行此操作");
}
```

### 6.5 of —— 全能构建器

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `of` (static) | `String templateName, Map<String,Object> data, List<String> sections` | `WireResponse` | 全能构建器：渲染 sections + 生成 snapshot，可继续链式调用 `withRedirect` / `withDispatch` / `withError` / `build` |
| `of` (static) | 无 | `WireResponse` | 空构建器：不渲染任何 section，仅用于纯 redirect / error 场景 |
| `of` (static) | `Map<String,String> sections, Map<String,Object> data` | `WireResponse` | 传统方式：直接传入已渲染的 section HTML（向后兼容） |
| `of` (static) | `Map<String,String> sections, Map<String,Object> data, String redirectUrl` | `WireResponse` | 传统方式：section 更新 + 重定向（向后兼容） |

```java
// 全能模式：更新 + 跳转 + 事件
return WireResponse.of("counter", data, sections)
    .withRedirect("/dashboard", 1500)
    .withDispatch("item-updated", Map.of("id", 42))
    .build();

// 纯重定向（不更新 section）
return WireResponse.of()
    .withRedirect("/login")
    .build();
```

### 6.6 withRedirect / withDispatch / withError / build

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `withRedirect` | `String url` | `WireResponse` | 添加重定向效果（立即跳转） |
| `withRedirect` | `String url, int delayMs` | `WireResponse` | 添加重定向效果（延迟跳转，`delayMs = 0` 立即） |
| `withDispatch` | `String eventName, Object eventData` | `WireResponse` | 添加 dispatch 事件效果（前端通过 `window.addEventListener` 监听） |
| `withError` | `int status, String message` | `WireResponse` | 设置错误状态（build 时返回非 200 的 JSON 响应） |
| `build` | 无 | `Response` | 构建最终的 HTTP Response |
| `getSections` | 无 | `Map<String,String>` | 获取已渲染的 section HTML（向后兼容） |
| `getSnapshot` | 无 | `String` | 获取 snapshot |
| `getEffects` | 无 | `Map<String,Object>` | 获取 effects |
| `toMap` | 无 | `Map<String,Object>` | 转为 Map（用于 JSON 序列化，向后兼容） |

```java
return ctx.responseOf()
    .withRedirect("/dashboard", 1500)          // 1.5 秒后跳转
    .withDispatch("item-updated", Map.of("id", 42))  // 派发事件
    .withError(403, "无权限")                    // 设置错误（可选）
    .build();
```

### 6.7 响应格式

所有 Wire JSON 响应（update / redirect / error / of）统一格式：

```json
{
  "sections": {"content": "<div>...</div>"},   // 可选：section 名 → HTML 内容
  "snapshot": "base64编码状态",                  // 可选：新的组件状态快照
  "effects": {                                  // 可选：副作用
    "redirect": {"url": "/login", "delay": 1500},
    "dispatch": [{"name": "event-name", "data": {...}}]
  },
  "error": {"status": 401, "message": "..."}     // 可选，仅 error 响应
}
```

> `effects.redirect` 支持两种格式：字符串（`"/login"`，立即跳转）或对象（`{"url": "/login", "delay": 1500}`，延迟跳转）。前端 wire.js 会自动兼容处理。

---

## 7. WireRequest 请求解析

`com.weacsoft.jaravel.vendor.wire.WireRequest`

Wire 更新请求，从前端 POST 的 JSON 中解析。请求格式：

```json
{
  "snapshot": "base64编码的组件状态",
  "action": "save",
  "params": {"title": "新标题", "content": "新内容"},
  "sections": ["content", "sidebar"]
}
```

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `from` (static) | `Request request` | `WireRequest` | 从 Jaravel Request 解析 Wire 请求体。依次尝试 `request.input("wire_body")` → `request.get("wire_body")` → 序列化 `request.all()` |
| `fromJson` (static) | `String json` | `WireRequest` | 直接从 JSON 字符串解析 |
| `getSnapshot` | 无 | `String` | 获取 snapshot（Base64 编码） |
| `getAction` | 无 | `String` | 获取 action 名称 |
| `getParams` | 无 | `Map<String,Object>` | 获取 params（可能为空 Map） |
| `getSections` | 无 | `List<String>` | 获取需要更新的 section 名列表 |
| `getData` | 无 | `Map<String,Object>` | 从 snapshot 解码出原始数据 Map |
| `getMergedData` | 无 | `Map<String,Object>` | 将 params 合并到 snapshot 数据中（用于 wire:model 的属性更新） |

`from` 解析顺序说明：前端 wire.js 以 `wire_body=<JSON>` 的 form-urlencoded 形式 POST，因此优先读取 `wire_body` 字段；若不存在（如直接 JSON 请求），则将 `request.all()` 序列化为 JSON 再解析。解析失败抛 `RuntimeException("解析 Wire 请求失败")`。

```java
WireRequest wireReq = WireRequest.from(request);
String action = wireReq.getAction();             // "save"
Map<String, Object> data = wireReq.getMergedData();  // snapshot + params 合并
List<String> sections = wireReq.getSections();   // ["content", "sidebar"]
```

---

## 8. WireManager 管理器

`com.weacsoft.jaravel.vendor.wire.WireManager`

Wire 管理器：核心工具类，负责 Wire 模式的渲染、section 提取和快照编解码。**无状态工具类**，所有状态通过 snapshot 在客户端流转，服务端不需要维护组件实例，天然支持水平扩展。

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `WIRE_MODE_KEY` | `__wire_mode` | Wire 模式标记，设置到 BladeContext 中触发 `@yield` 的 section 包装 |
| `WIRE_UPDATE_URL_KEY` | `__wire_update_url` | Wire 更新 URL 标记，设置到 BladeContext 中供模板使用 |

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `setEngine` (static) | `BladeEngine engine` | `void` | 设置 BladeEngine 实例（由 ServiceProvider 或配置类调用） |
| `getEngine` (static) | 无 | `BladeEngine` | 获取 BladeEngine 实例（未设置抛异常） |
| `renderForWire` (static) | `String templateName, Map<String,Object> data` | `String` | 以 Wire 模式渲染模板（完整页面）。设置 `__wire_mode = true`，使 `@yield` 输出被 `<div wire:section="name">` 包裹 |
| `renderSection` (static) | `String templateName, String sectionName, Map<String,Object> data` | `String` | 渲染指定 section 的内容（不含布局） |
| `renderSections` (static) | `String templateName, List<String> sectionNames, Map<String,Object> data` | `Map<String,String>` | 批量渲染多个 section（高效：只加载和初始化模板一次） |
| `getSectionNames` (static) | `String templateName` | `List<String>` | 获取模板中所有已注册的 section 名 |
| `encodeSnapshot` (static) | `Map<String,Object> data` | `String` | 将数据 Map 编码为 Base64 JSON 快照（自动过滤 `__wire` 前缀的内部字段） |
| `decodeSnapshot` (static) | `String base64` | `Map<String,Object>` | 从 Base64 JSON 快照解码出数据 Map（空串返回空 Map） |
| `injectWireAssets` (static) | `String html, String updateUrl, String snapshot` | `String` | 将 Wire 资源（snapshot + updateUrl + wire.js）注入到 HTML 的 `</body>` 前 |
| `renderWirePage` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `String` | 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源 |

注入的 Wire 资源结构：

```html
<script type="application/json" wire:config
        data-wire-update="/api/wire/admin"
        wire:snapshot="base64snapshot"></script>
<script src="/static/wire.js"></script>
```

> `encodeSnapshot` 会自动过滤所有以 `__wire` 开头的内部字段（如 `__wire_mode`、`__wire_update_url`），避免内部状态污染客户端 snapshot。

---

## 9. wire.js 前端运行时

`resources/static/wire.js` 是 Laravel Livewire 风格的部分更新前端运行时，零外部依赖、自包含。

**核心功能**：
- 自动扫描 `wire:` 属性并绑定事件（`wire:click`、`wire:submit`、`wire:model`、`wire:change`、`wire:keydown`）
- 支持自定义 update URL（`wire:update` 属性或 `data-wire-update` 配置）
- section 级局部更新（仅替换 `[wire:section="name"]` 的内容）
- `wire:model` 双向绑定（默认防抖 150ms，`wire:model.live` 实时同步，`wire:model.lazy` 延迟到 blur）
- `wire:loading` 加载状态显示/隐藏
- `wire:target` 指定要更新的 section
- 认证过期自动跳转登录页（401 + redirect）

### 9.1 wire:click / wire:submit / wire:change / wire:keydown

| 指令 | 触发事件 | 说明 |
| --- | --- | --- |
| `wire:click="actionName"` | click | 点击触发，`e.preventDefault()` 后发送请求 |
| `wire:submit="actionName"` | submit | 表单提交触发，自动收集 `FormData` 作为 params |
| `wire:change="actionName"` | change | 元素值变化触发 |
| `wire:keydown="actionName"` | keydown | 按键触发；支持修饰符 `wire:keydown.enter="..."`、`.escape`、`.tab`、`.space`、`.arrowup`、`.arrowdown` |

**传递参数**：通过 `wire:param-<name>="value"` 属性为 action 附加参数，前端会收集所有 `wire:param-*` 属性合并到 params 中。

```html
<button wire:click="increment" wire:param-id="42">+1</button>

<form wire:submit="save">
    <input name="title">
    <button type="submit">保存</button>
</form>

<input wire:keydown.enter="search">
```

### 9.2 wire:model / wire:model.live / wire:model.lazy

| 指令 | 触发时机 | 说明 |
| --- | --- | --- |
| `wire:model="field"` | input（防抖 150ms） | 默认双向绑定，输入时防抖 150ms 后发送 `$sync` 请求 |
| `wire:model.live="field"` | input（实时） | 实时同步，每次输入立即发送请求（无防抖） |
| `wire:model.lazy="field"` | change | 延迟同步，失去焦点或值变化时才发送请求 |

`wire:model` 实现了真正的双向绑定：前端输入会以 `params = {field: value}` 的形式发送 `$sync` action 到服务端，服务端更新数据后返回新的 section HTML，前端替换 DOM 并**自动恢复焦点和光标位置**（通过 `saveFocus` / `restoreFocus`）。

```html
<!-- 防抖 150ms 同步 -->
<input wire:model="message">

<!-- 实时同步（每次输入都请求） -->
<input wire:model.live="keyword">

<!-- 延迟到失去焦点 -->
<input wire:model.lazy="title">
```

支持的输入类型：text、checkbox（返回 `checked` 布尔值）、select-multiple（返回选中值数组）、其他（返回 `value`）。

### 9.3 wire:section 机制

`wire:section` 标记可局部更新的区域，支持两种标记方式：

**方式 1：元素属性**
```html
<div wire:section="content">
    {{-- 这部分内容会被局部替换 --}}
</div>
```

**方式 2：HTML 注释标记**
```html
<!--wire:section-start:content-->
    <div>这部分内容会被局部替换</div>
<!--wire:section-end:content-->
```

前端收到更新响应后，`replaceSection` 会根据 section 名定位元素/注释，替换其内容，然后调用 `rebindSection` 重新绑定新 DOM 中的 `wire:` 事件。替换前后会通过 `saveFocus` / `restoreFocus` 保存并恢复焦点和光标位置，确保输入框更新后用户体验连贯。

**指定更新的 section**：通过 `wire:target="section1,section2"` 指定当前请求只更新某些 section；未指定时更新所有 section。

### 9.4 snapshot 机制

snapshot 是 Wire 的状态载体，采用 **Base64(JSON)** 编码：

1. **初始渲染**：`WireResponse.wire` 调用 `WireManager.encodeSnapshot(data)` 将初始数据编码为 Base64，注入到 `<script wire:config wire:snapshot="...">`。
2. **更新请求**：前端 wire.js 读取 `wire:snapshot` 属性，作为 `snapshot` 字段 POST 到服务端。
3. **服务端解码**：`WireRequest.getData()` 调用 `WireManager.decodeSnapshot` 还原为 data Map。
4. **更新响应**：`WireResponse.update` 重新 `encodeSnapshot` 新状态返回，前端 `handleResponse` 更新 `component.snapshot` 和 `wire:snapshot` 属性。

这种设计使服务端完全无状态，组件状态随请求往返，天然支持水平扩展与多实例部署。

### 9.5 wire:target / wire:loading / wire:update

| 指令 | 说明 |
| --- | --- |
| `wire:target="section1,section2"` | 指定当前请求只更新这些 section；未指定时更新所有 section。可放在触发元素或其父级 |
| `wire:loading` | 加载状态元素。请求发送时显示，响应返回后隐藏 |
| `wire:loading(target="actionName")` | 仅当指定 action 触发时显示 |
| `wire:update="/custom/url"` | 覆盖当前元素的 update URL（向上查找最近的 `wire:update`） |

---

## 10. 认证过期无感重定向

Wire 实现了认证过期的「无感」重定向体验：当用户在 Wire 交互过程中 session 过期，前端会自动跳转到登录页，登录成功后回到之前的页面。

### 工作流程

```
用户操作触发 Wire 请求
        │
        ▼
中间件检测到 session 过期
        │
        ├── 返回 401 + JSON {message, redirect: "/login"}
        │   或返回 302 重定向（非 API 路径）
        │
        ▼
wire.js fetch 拦截
        │
        ├── response.status === 401
        │   → 读取 errData.redirect（默认 /login）
        │   → redirectToLogin(loginUrl)
        │
        ├── response.type === 'opaqueredirect'（manual 模式下的 302）
        │   → redirectToLogin('/login')
        │
        ▼
redirectToLogin(loginUrl)
        │
        ├── 携带当前页面 URL 作为 redirect 参数
        │   window.location.href = loginUrl + '?redirect=' + encodeURIComponent(currentUrl)
        │
        ├── 避免重复重定向（若已在登录页则不跳）
        │
        ▼
登录成功后，应用读取 redirect 参数回到原页面
```

### 关键设计点

1. **`fetch` 使用 `redirect: 'manual'`**：不自动跟随重定向，由 wire.js 手动处理，避免 302 被浏览器吞掉。
2. **401 优先读 `redirect` 字段**：中间件返回的 401 JSON 中可携带 `redirect` 字段指定登录页 URL。
3. **携带回跳地址**：`redirectToLogin` 会将当前页面 URL 编码后作为 `redirect` 参数附加到登录页 URL，登录成功后可回跳。
4. **防重复**：若当前已在登录页则不再跳转，避免死循环。

### 中间件示例

认证中间件对 Wire 请求返回 401 + redirect：

```java
@Component
public class AuthMiddleware implements Middleware {
    @Override
    public Response handle(Request request, NextFunction next) {
        if (!Auth.check()) {
            // 对 Wire 请求返回 401 + redirect，前端自动跳转
            return WireResponse.error(401, "Unauthorized", "/login");
        }
        return next.apply(request);
    }
}
```

---

## 11. 完整控制器示例

一个完整的计数器 + 列表示例，展示 `WireService` 流式 API 的典型用法：

```java
package com.weacsoft.jaravel.controller;

import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;
import com.weacsoft.jaravel.vendor.wire.WireService;
import com.weacsoft.jaravel.vendor.wire.WireResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class WireDemoController {

    /**
     * 初始页面渲染（GET 请求）
     */
    public Response page(Request request) {
        return WireService.from(request, "wire-demo", "/api/wire/demo")
            .once("count", 0)
            .once("message", "")
            .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
            .responseWire();
    }

    /**
     * Wire 更新接口（POST 请求，由 wire.js 自动调用）
     */
    public Response update(Request request) {
        return WireService.from(request, "wire-demo", "/api/wire/demo")
            .once("count", 0)
            .once("message", "")
            .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
            .action("increment", c -> c.put("count", c.getInt("count") + 1))
            .action("decrement", c -> c.put("count", c.getInt("count") - 1))
            .action("reset", c -> {
                c.put("count", 0);
                c.put("message", "");
            })
            .action("addItem", c -> {
                List<Object> items = c.getList("items");
                items.add("项目 " + (items.size() + 1));
            })
            .action("removeItem", c -> {
                List<Object> items = c.getList("items");
                if (!items.isEmpty()) items.remove(items.size() - 1);
            })
            .action("save", c -> {
                // 保存后跳转到详情页（1.5 秒延迟，先显示提示）
                // 通过 responseOf + withRedirect 实现
            })
            .responseUpdate();
    }

    /**
     * 保存后跳转（演示 redirect delay）
     */
    public Response save(Request request) {
        return WireService.from(request, "wire-demo", "/api/wire/demo")
            .once("count", 0)
            .action("save", c -> {
                int newId = 42; // service.create(...);
                c.set("newId", newId);
            })
            .responseOf()
            .withRedirect("/items/42", 1500)   // 1.5 秒后跳转
            .withDispatch("item-saved", Map.of("id", 42))
            .build();
    }
}
```

对应模板 `wire-demo.blade.php`：

```blade
@extends('layouts.app')

@section('content')
    <div>
        <h2>计数器：{{ $count }}</h2>
        <button wire:click="increment">+1</button>
        <button wire:click="decrement">-1</button>
        <button wire:click="reset">重置</button>

        <input wire:model="message" placeholder="输入消息">
        <p>当前消息：{{ $message }}</p>

        <ul>
            @foreach($items as $item)
                <li>{{ $item }}</li>
            @endforeach
        </ul>
        <button wire:click="addItem">添加项目</button>
        <button wire:click="removeItem">删除最后</button>
        <button wire:click="save">保存并跳转</button>
    </div>
@endsection
```

路由注册：

```java
Router router = new Router();
router.get("/wire/demo", demoController::page);
router.post("/api/wire/demo", demoController::update);
```

---

## 12. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `WireService` | **单请求隔离** | 每次请求通过 `from` / `of` 创建新实例，内部 `data` / `sections` / `actionHandlers` 为请求私有。不应跨请求共享同一个 `WireService` |
| `WireResponse` | **单响应隔离** | 每次调用静态工厂方法创建新实例，构建器状态为本次响应私有。`build()` 后不应再修改 |
| `WireRequest` | **单请求隔离** | 每次请求通过 `from` 创建新实例，字段 `final`，不可变 |
| `WireManager` | **线程安全** | 无状态工具类，所有方法为静态方法。`engine` 静态字段在启动阶段单次写入后只读。`ObjectMapper` 为静态 final 线程安全。可在并发请求间安全复用 |
| `wire.js` | 单组件隔离 | 前端运行时，每个 `wire:config` 对应一个 component 实例，`boundElements` Set 防止重复绑定 |

> `WireService` / `WireResponse` / `WireRequest` 设计为「用完即弃」的请求级对象，不可跨请求复用。`WireManager` 是无状态工具类，可安全地在并发环境下调用。

---

## 关键概念

- **Snapshot（快照）**：组件状态的 Base64 JSON 编码，在客户端与服务端之间流转，使服务端无状态。
- **Section（区块）**：模板中可独立更新的区域，通过 `wire:section="name"` 标记。
- **Action（动作）**：前端触发的操作名称（如 `increment`、`save`），服务端通过 `WireService.action` 注册处理器。
- **Effects（副作用）**：响应中除 section 更新外的附加效果，如 `redirect`、`dispatch` 事件。

## 配置

wire 模块本身不读取外部配置文件，但需要通过 `WireManager.setEngine(bladeEngine)` 注入 Blade 引擎实例。这通常由 `springboot` 模块的 Starter 在应用启动时自动完成。

```java
// 手动初始化（通常不需要，Starter 会自动处理）
WireManager.setEngine(bladeEngine);
```

静态资源 `wire.js` 通过 `http` 模块的 `Router.serveStatic` 服务：

```java
router.serveStatic("/static", "classpath:/static/", 3600);
```

---

版本: 0.1.1
