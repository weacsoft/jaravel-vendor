# wire AI-API Reference

> Module: `wire` | Package: `com.weacsoft.jaravel.vendor.wire` | Version: 0.1.1

## Overview
wire 模块是 Jaravel-Vendor 的全栈响应式 UI 框架，对齐 Laravel Livewire。它通过服务端渲染（`WireManager`）+ 前端局部更新（`wire.js`）实现「服务端渲染 + 前端局部更新」的开发模式。核心包含四个类：`WireService`（流式上下文，串联请求解析、默认值填充、action 分派、响应构建）、`WireResponse`（语义化响应构建器，覆盖 wire/update/redirect/error/of 全部场景）、`WireRequest`（从前端 POST 的 JSON 中解析 snapshot/action/params/sections）、`WireManager`（无状态工具类，负责 Wire 模式渲染、section 提取和快照编解码）。组件状态以 Base64 JSON（snapshot）在客户端流转，服务端无状态，天然支持水平扩展。

## Classes

### WireService
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wire`
- **Description**: Wire 流式上下文，把请求解析、默认值填充、action 处理、响应构建串联起来。设计理念是「控制器一行链式调用搞定，不写 if/switch」。通过 `from` 从请求解析上下文，通过 `once` 填充默认值，通过 `action` 注册处理器，最后通过 `responseWire` / `responseUpdate` / `responseOf` 生成响应。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `from` (static) | `Request request, String templateName, String updateUrl` | `WireService` | 第一步：从 HTTP 请求解析 Wire 上下文。自动解析 snapshot + action + params + sections，合并为 data |
| `of` (static) | `String templateName, String updateUrl, Map<String,Object> data` | `WireService` | 从已有数据创建上下文（用于初始页面渲染，不需要解析请求） |
| `once` | `String key, Object defaultValue` | `WireService` | 如果字段不存在则填入默认值（仅一次），返回 this（链式） |
| `action` | `String actionName, Consumer<WireService> handler` | `WireService` | 注册 action 处理器。当当前请求的 action 匹配时执行处理器；注册时不执行，调用 responseUpdate/responseOf/toData 时统一分派 |
| `set` | `String key, Object value` | `WireService` | 直接设置字段值（无条件覆盖） |
| `update` | `String key, Function<T,T> updater` | `WireService` | 函数式更新字段值（接收当前值，返回新值） |
| `remove` | `String key` | `WireService` | 删除字段 |
| `get` | `String key` | `Object` | 获取字段值 |
| `get` | `String key, Object defaultValue` | `Object` | 获取字段值，带默认值 |
| `getInt` | `String key` | `int` | 获取 int 类型字段值（安全转换，失败返回 0） |
| `getStr` | `String key` | `String` | 获取 String 类型字段值（null 返回空串） |
| `getList` | `String key` | `List<Object>` | 获取 List 类型字段值（返回可变 List；不存在则创建空 List 并填入；非 List 自动包装） |
| `getData` | 无 | `Map<String,Object>` | 获取原始 data Map（可直接操作） |
| `getAction` | 无 | `String` | 获取当前 action 名称 |
| `toData` | 无 | `Map<String,Object>` | 分派 action 处理器并返回最终的 data Map |
| `toSections` | 无 | `List<String>` | 获取要更新的 section 列表。如果请求中没有指定 sections，则使用模板的默认 section |
| `responseWire` | 无 | `Response` | 直接生成 Wire 初始页面响应（HTML）。等同于 `WireResponse.wire(templateName, data, updateUrl)` |
| `responseUpdate` | 无 | `Response` | 分派 action 处理器并生成 Wire 更新响应（JSON）。等同于 `WireResponse.update(templateName, data, sections)`，但自动分派 action |
| `responseOf` | 无 | `WireResponse` | 分派 action 处理器并返回全能构建器，可继续链式调用 `withRedirect` / `withDispatch` / `withError` |

#### Usage Example

```java
// 完整的流式 API 用法
public Response update(Request request) {
    return WireService.from(request, "wire-demo", "/api/wire/demo")
        .once("count", 0)                          // 没有就填默认值
        .once("message", "")
        .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
        .action("increment", c -> c.put("count", c.getInt("count") + 1))
        .action("decrement", c -> c.put("count", c.getInt("count") - 1))
        .action("reset", c -> { c.put("count", 0); c.put("message", ""); })
        .action("addItem", c -> {
            List<Object> items = c.getList("items");
            items.add("项目 " + (items.size() + 1));
        })
        .responseUpdate();
}

// 初始页面渲染
public Response page(Request request) {
    return WireService.from(request, "wire-demo", "/api/wire/demo")
        .once("count", 0)
        .once("message", "")
        .responseWire();
}

// 全能模式：更新 + 跳转 + 事件
public Response save(Request request) {
    return WireService.from(request, "wire-demo", "/api/wire/demo")
        .once("count", 0)
        .action("save", c -> {
            int newId = service.create(...);
            c.set("newId", newId);
        })
        .responseOf()
        .withRedirect("/dashboard", 1500)              // 1.5 秒后跳转
        .withDispatch("item-saved", Map.of("id", 42))
        .build();
}

// 直接操作数据
WireService ctx = WireService.from(request, "demo", "/api/wire/demo");
ctx.set("count", 10);
ctx.update("count", oldVal -> oldVal + 1);
ctx.remove("message");
int count = ctx.getInt("count");
String msg = ctx.getStr("message");
List<Object> items = ctx.getList("items");
```

---

### WireResponse
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wire`
- **Description**: Wire 响应构建器，Wire 控制器的统一响应入口。提供语义化的静态方法，覆盖 Wire 的全部响应场景：`wire`（初始页面渲染）、`update`（部分更新）、`redirect`（Wire 重定向，支持 delay）、`error`（错误响应）、`of`（全能构建器）。所有 Wire JSON 响应统一格式：`{sections, snapshot, effects, error}`。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `wire` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `Response` | 初始页面渲染：渲染模板 + 注入 Wire 资源（wire.js + snapshot + updateUrl），返回完整 HTML 页面 |
| `wire` (static) | `String templateName, Map<String,Object> data` | `Response` | 初始页面渲染（使用默认 update URL: `/wire/update`） |
| `update` (static) | `String templateName, Map<String,Object> data, List<String> sections` | `Response` | 部分更新响应：渲染指定 section 并返回 JSON。前端 wire.js 收到后自动替换对应 section 的 DOM 内容，并更新 snapshot。sections 为空时使用模板默认 section |
| `redirect` (static) | `String url` | `Response` | Wire 重定向：返回 JSON，前端 wire.js 自动执行 `window.location.href = url`。默认立即跳转 |
| `redirect` (static) | `String url, int delayMs` | `Response` | Wire 重定向（带延迟）：前端在延迟指定毫秒后跳转。`delayMs = 0` 表示立即跳转 |
| `error` (static) | `int status, String message` | `Response` | Wire 错误响应：返回指定状态码的 JSON。前端 wire.js 对 401 会自动跳转登录页；其他状态码打印到控制台 |
| `error` (static) | `int status, String message, String redirect` | `Response` | Wire 错误响应（带重定向 URL）。用于认证过期场景：返回 401 + redirect URL，前端自动跳转登录页 |
| `of` (static) | `String templateName, Map<String,Object> data, List<String> sections` | `WireResponse` | 全能构建器：渲染 sections + 生成 snapshot，可继续链式调用 `withRedirect` / `withDispatch` / `withError` / `build` |
| `of` (static) | 无 | `WireResponse` | 空构建器：不渲染任何 section，仅用于纯 redirect / error 场景 |
| `of` (static) | `Map<String,String> sections, Map<String,Object> data` | `WireResponse` | 传统方式：直接传入已渲染的 section HTML（向后兼容） |
| `of` (static) | `Map<String,String> sections, Map<String,Object> data, String redirectUrl` | `WireResponse` | 传统方式：section 更新 + 重定向（向后兼容） |
| `withRedirect` | `String url` | `WireResponse` | 添加重定向效果（立即跳转） |
| `withRedirect` | `String url, int delayMs` | `WireResponse` | 添加重定向效果（延迟跳转，`delayMs = 0` 立即） |
| `withDispatch` | `String eventName, Object eventData` | `WireResponse` | 添加 dispatch 事件效果（前端通过 `window.addEventListener` 监听） |
| `withError` | `int status, String message` | `WireResponse` | 设置错误状态（build 时返回非 200 的 JSON 响应） |
| `build` | 无 | `Response` | 构建最终的 HTTP Response |
| `getSections` | 无 | `Map<String,String>` | 获取已渲染的 section HTML（向后兼容） |
| `getSnapshot` | 无 | `String` | 获取 snapshot |
| `getEffects` | 无 | `Map<String,Object>` | 获取 effects |
| `toMap` | 无 | `Map<String,Object>` | 转为 Map（用于 JSON 序列化，向后兼容） |

#### Response Format

所有 Wire JSON 响应（update / redirect / error / of）统一格式：

```json
{
  "sections": {"content": "<div>...</div>"},
  "snapshot": "base64编码状态",
  "effects": {
    "redirect": {"url": "/login", "delay": 1500},
    "dispatch": [{"name": "event-name", "data": {...}}]
  },
  "error": {"status": 401, "message": "..."}
}
```

> `effects.redirect` 支持两种格式：字符串（`"/login"`，立即跳转）或对象（`{"url": "/login", "delay": 1500}`，延迟跳转）。前端 wire.js 会自动兼容处理。

#### Usage Example

```java
// 1. 初始页面渲染
public Response page(Request request) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("count", 0);
    return WireResponse.wire("counter", data, "/api/wire/counter");
}

// 2. 部分更新
public Response update(Request request) {
    WireRequest wireReq = WireRequest.from(request);
    Map<String, Object> data = wireReq.getMergedData();
    data.put("count", toInt(data.get("count")) + 1);
    return WireResponse.update("counter", data, wireReq.getSections());
}

// 3. 立即重定向
public Response save(Request request) {
    int newId = service.create(...);
    return WireResponse.redirect("/items/" + newId);
}

// 4. 延迟重定向（先显示提示，1.5 秒后跳转）
public Response save(Request request) {
    int newId = service.create(...);
    return WireResponse.redirect("/items/" + newId, 1500);
}

// 5. 错误响应（认证过期）
public Response update(Request request) {
    if (!Auth.check()) {
        return WireResponse.error(401, "Unauthorized", "/login");
    }
    if (!hasPermission()) {
        return WireResponse.error(403, "无权限执行此操作");
    }
    // ...
}

// 6. 全能模式：更新 + 跳转 + 事件
public Response update(Request request) {
    Map<String, Object> data = ...;
    List<String> sections = ...;
    return WireResponse.of("counter", data, sections)
        .withRedirect("/dashboard", 1500)
        .withDispatch("item-updated", Map.of("id", 42))
        .build();
}

// 7. 纯重定向（不更新 section）
return WireResponse.of()
    .withRedirect("/login")
    .build();
```

---

### WireRequest
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.wire`
- **Description**: Wire 更新请求，从前端 POST 的 JSON 中解析。请求格式包含 snapshot（Base64 编码的组件状态）、action（操作名称）、params（参数 Map）、sections（需要更新的 section 名列表）。

#### Request Format

```json
{
  "snapshot": "base64编码的组件状态",
  "action": "save",
  "params": {"title": "新标题", "content": "新内容"},
  "sections": ["content", "sidebar"]
}
```

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `WireRequest` | `String snapshot, String action, Map<String,Object> params, List<String> sections` | 构造方法 | 创建 WireRequest 实例 |
| `from` (static) | `Request request` | `WireRequest` | 从 Jaravel Request 解析 Wire 请求体。依次尝试 `request.input("wire_body")` → `request.get("wire_body")` → 序列化 `request.all()`。解析失败抛 RuntimeException |
| `fromJson` (static) | `String json` | `WireRequest` | 直接从 JSON 字符串解析 |
| `getSnapshot` | 无 | `String` | 获取 snapshot（Base64 编码） |
| `getAction` | 无 | `String` | 获取 action 名称 |
| `getParams` | 无 | `Map<String,Object>` | 获取 params（可能为空 Map） |
| `getSections` | 无 | `List<String>` | 获取需要更新的 section 名列表 |
| `getData` | 无 | `Map<String,Object>` | 从 snapshot 解码出原始数据 Map（调用 `WireManager.decodeSnapshot`） |
| `getMergedData` | 无 | `Map<String,Object>` | 将 params 合并到 snapshot 数据中（用于 wire:model 的属性更新） |

#### Usage Example

```java
// 从 HTTP 请求解析
WireRequest wireReq = WireRequest.from(request);

String snapshot = wireReq.getSnapshot();          // Base64 编码的状态
String action = wireReq.getAction();              // "save"
Map<String, Object> params = wireReq.getParams(); // {"title": "新标题", ...}
List<String> sections = wireReq.getSections();    // ["content", "sidebar"]

// 获取 snapshot 解码后的原始数据
Map<String, Object> data = wireReq.getData();

// 获取合并了 params 的数据（推荐，用于 wire:model 双向绑定）
Map<String, Object> merged = wireReq.getMergedData();

// 手动处理更新
public Response update(Request request) {
    WireRequest wireReq = WireRequest.from(request);
    Map<String, Object> data = wireReq.getMergedData();
    data.put("count", toInt(data.get("count")) + 1);
    return WireResponse.update("counter", data, wireReq.getSections());
}

// 从 JSON 字符串解析（用于测试或非 HTTP 场景）
WireRequest wireReq = WireRequest.fromJson(
    "{\"snapshot\":\"eyJjb3VudCI6MH0=\",\"action\":\"increment\",\"params\":{},\"sections\":[\"content\"]}"
);
```

---

### WireManager
- **Type**: class (utility, all static methods)
- **Package**: `com.weacsoft.jaravel.vendor.wire`
- **Description**: Wire 管理器：核心工具类，负责 Wire 模式的渲染、section 提取和快照编解码。无状态工具类，所有状态通过 snapshot 在客户端流转，服务端不需要维护组件实例，天然支持水平扩展。提供两种使用模式：无感模式（使用 `Response.wire` 渲染页面，`@section` 自动成为可更新区域）和显式模式（手动调用 `renderSections` 渲染指定 section）。

#### Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `WIRE_MODE_KEY` | `__wire_mode` | Wire 模式标记，设置到 BladeContext 中触发 `@yield` 的 section 包装 |
| `WIRE_UPDATE_URL_KEY` | `__wire_update_url` | Wire 更新 URL 标记，设置到 BladeContext 中供模板使用 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `setEngine` (static) | `BladeEngine engine` | `void` | 设置 BladeEngine 实例（由 ServiceProvider 或配置类调用） |
| `getEngine` (static) | 无 | `BladeEngine` | 获取 BladeEngine 实例（未设置抛 RuntimeException） |
| `renderForWire` (static) | `String templateName, Map<String,Object> data` | `String` | 以 Wire 模式渲染模板（完整页面）。设置 `__wire_mode = true`，使 `@yield` 输出被 `<div wire:section="name">` 包裹 |
| `renderSection` (static) | `String templateName, String sectionName, Map<String,Object> data` | `String` | 渲染指定 section 的内容（不含布局） |
| `renderSections` (static) | `String templateName, List<String> sectionNames, Map<String,Object> data` | `Map<String,String>` | 批量渲染多个 section（高效：只加载和初始化模板一次）。返回 section 名 → HTML 内容 |
| `getSectionNames` (static) | `String templateName` | `List<String>` | 获取模板中所有已注册的 section 名 |
| `encodeSnapshot` (static) | `Map<String,Object> data` | `String` | 将数据 Map 编码为 Base64 JSON 快照（自动过滤 `__wire` 前缀的内部字段） |
| `decodeSnapshot` (static) | `String base64` | `Map<String,Object>` | 从 Base64 JSON 快照解码出数据 Map（空串返回空 Map） |
| `injectWireAssets` (static) | `String html, String updateUrl, String snapshot` | `String` | 将 Wire 资源（snapshot + updateUrl + wire.js）注入到 HTML 的 `</body>` 前 |
| `renderWirePage` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `String` | 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源 |

#### Injected Wire Assets

`injectWireAssets` 在 `</body>` 标签前注入：

```html
<script type="application/json" wire:config
        data-wire-update="/api/wire/admin"
        wire:snapshot="base64snapshot"></script>
<script src="/static/wire.js"></script>
```

#### Usage Example

```java
// 1. 初始化（通常由 Starter 自动完成）
WireManager.setEngine(bladeEngine);

// 2. 完整的 Wire 初始渲染（等同于 WireResponse.wire 内部调用）
String html = WireManager.renderWirePage("counter", data, "/api/wire/counter");
return ResponseBuilder.html(html);

// 3. 分步渲染
String html = WireManager.renderForWire("counter", data);  // 渲染模板（Wire 模式）
String snapshot = WireManager.encodeSnapshot(data);        // 编码快照
String finalHtml = WireManager.injectWireAssets(html, "/api/wire/counter", snapshot);

// 4. 渲染指定 section（用于手动构建更新响应）
String contentHtml = WireManager.renderSection("counter", "content", data);

// 5. 批量渲染多个 section
Map<String, String> sections = WireManager.renderSections("counter",
    Arrays.asList("content", "sidebar"), data);
// sections = {"content": "<div>...</div>", "sidebar": "<div>...</div>"}

// 6. 获取模板的所有 section 名
List<String> names = WireManager.getSectionNames("counter");

// 7. 快照编解码
Map<String, Object> data = new LinkedHashMap<>();
data.put("count", 0);
data.put("message", "hello");
String snapshot = WireManager.encodeSnapshot(data);  // Base64 JSON
Map<String, Object> decoded = WireManager.decodeSnapshot(snapshot);  // 还原

// 8. 手动构建更新响应（显式模式）
public Response update(Request request) {
    WireRequest wireReq = WireRequest.from(request);
    Map<String, Object> data = wireReq.getMergedData();
    data.put("count", toInt(data.get("count")) + 1);

    List<String> sections = wireReq.getSections();
    if (sections.isEmpty()) {
        sections = WireManager.getSectionNames("counter");
    }
    Map<String, String> sectionHtml = WireManager.renderSections("counter", sections, data);
    String snapshot = WireManager.encodeSnapshot(data);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sections", sectionHtml);
    result.put("snapshot", snapshot);
    result.put("effects", new LinkedHashMap<>());
    return ResponseBuilder.json(result);
}
```

---

## Quick Reference

### 三步流程速查

```
第一步：创建上下文          第二步：处理数据            第三步：生成响应
WireService.from(...)   →   .once(...)             →   .responseWire()    // 初始页面
                        .action(...)                  .responseUpdate()  // 部分更新
                                                      .responseOf()      // 全能构建器
```

### 响应类型选择

| 场景 | 推荐方法 |
| --- | --- |
| 初始页面渲染（GET 请求） | `WireResponse.wire(template, data, updateUrl)` 或 `ctx.responseWire()` |
| 部分更新（POST 请求，wire.js 自动调用） | `WireResponse.update(template, data, sections)` 或 `ctx.responseUpdate()` |
| 处理中触发跳转 | `WireResponse.redirect(url)` 或 `WireResponse.redirect(url, delayMs)` |
| 错误响应 | `WireResponse.error(status, message)` 或 `WireResponse.error(status, message, redirect)` |
| 更新 + 跳转 + 事件组合 | `WireResponse.of(template, data, sections).withRedirect(...).withDispatch(...).build()` |
| 认证过期 | `WireResponse.error(401, "Unauthorized", "/login")` |

### wire.js 指令速查

| 指令 | 说明 |
| --- | --- |
| `wire:click="action"` | 点击触发 action |
| `wire:submit="action"` | 表单提交触发 action |
| `wire:change="action"` | 值变化触发 action |
| `wire:keydown.enter="action"` | 按键触发（支持 .enter/.escape/.tab/.space/.arrowup/.arrowdown） |
| `wire:model="field"` | 双向绑定（防抖 150ms） |
| `wire:model.live="field"` | 双向绑定（实时） |
| `wire:model.lazy="field"` | 双向绑定（延迟到 change） |
| `wire:section="name"` | 标记可更新的 section |
| `wire:target="section1,section2"` | 指定更新的 section |
| `wire:loading` | 加载状态元素 |
| `wire:update="/url"` | 覆盖 update URL |
| `wire:param-xxx="value"` | 为 action 附加参数 |

---

版本: 0.1.1
