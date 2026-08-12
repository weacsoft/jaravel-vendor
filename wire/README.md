# wire 模块

> Jaravel-Vendor 的全栈响应式 UI 框架模块，实现 Laravel Livewire 风格的服务端渲染 + 前端局部更新。包名统一为 `com.weacsoft.jaravel.vendor.wire`。
>
> **推荐使用 `WireController` 抽象基类**（Livewire 风格），旧的 `WireService` 流式 API 已废弃（`WireResponse` 保留为兼容层）。

---

## 快速开始：WireController（推荐）

WireController 是类似 Laravel Livewire 的全页组件基类。继承它并实现 `render()` 即可获得完整的 Wire 能力。

### 最小示例

```java
public class AdminController extends WireController {
    public Admin setting;

    @Override
    protected WireView render() {
        // 纠正后写法:render 只声明模板 + 额外数据,「不」调用 .bladeExtends(getLayout())
        // 布局由框架按 getLayout()/getWireLayout() 在渲染期通过 WireParentOverride 外部套用,
        // render 内不要再套一次,否则会与框架冲突。
        return wireView("mdui.admin.admin.item", Map.of("setting", setting));
    }

    @Override
    protected void mount(Request request) {        // 注意:参数是 Request,不是 Map
        if (request.has("id"))
            setting = Admin.self().find(request.get("id", "")).toObject();
        else setting = new Admin();
    }

    @Override protected String getLayout() { return "layouts.mdui.form"; }
    @Override protected String getWireLayout() { return "layouts.mdui.form"; }
    @Override protected String getRedirectUrl(Request request) { return RouteHelper.route("admin.admin.index"); }

    // fill 是「赋值」:把键值对直接赋到本 Controller 的 public 属性(同名赋值,不覆盖其它字段)
    public void save() {
        Admin.self().newQuery().where("id", setting.getId())
            .data("name", setting.getName()).update();
        wire().component("toast", Map.of("message", "保存成功", "type", "success"));
    }
}
```

### 路由注册（方法名固定 index/update）

```java
Route.get("/change", "AdminController::index").name("change.index");
Route.post("/change", "AdminController::update").name("change");
```

> 推荐用 `getUpdateRouteName()` 返回 POST 路由名（如 `admin.admin.change`），使列表页(GET)的 wire 局部更新指向正确的 POST 端点，而不是误打 GET 的 URI。

### 核心方法

| 方法 | 说明 |
|------|------|
| `render()` | **必须实现**。返回 `WireView` 配置；只声明模板 + 额外数据，**不要**在里面调用 `.bladeExtends(getLayout())`（布局由框架外部套用） |
| `mount(Request)` | 可选。仅首次 `index()` 时调用，参数为 `Request`；可读取 `request.query()/input()` 初始化。Spring 单例 Bean 需在 `mount()` 里重置表单字段，避免上一次请求残留值泄漏进快照 |
| `fill(key, value)` / `fill(Map)` | 可选。把键值对**直接赋**到 Controller 自己的 public 属性（同名赋值 + 基础类型转换），不做任何业务重写 |
| `getLayout()` | 直访场景父模板 |
| `getWireLayout()` | wire 请求场景父模板（Dialog 等） |
| `getUpdateRouteName()` | 组件更新(POST)对应的路由名，`wire:config` 的 `data-wire-update` 指向它 |
| `getRedirectUrl(request)` | wire 请求成功后的重定向 URL（作为 `effects.redirect` 下发，前端跳转）；传统表单提交后也用它做 redirect |
| `wire()` | 下发临时组件 `wire().component("name", params)` |
| `WireEffects.dispatch(name, data)` | 下发前端事件（`window.dispatchEvent(new CustomEvent(name, {detail:data}))`），用于打开/关闭对话框等 |

### 三种请求处理

| 请求类型 | 触发条件 | 处理流程 |
|----------|---------|---------|
| 直访 GET | 无 wire_body | mount → render → 渲染整页 → 注入 wire assets |
| Wire POST | 含 wire_body | decodeSnapshot → invokeAction → renderSections → JSON |
| 传统表单 POST | 无 wire_body 的 POST | mount → fill → invokeAction("save") → redirect |

### 安全机制

- Snapshot HMAC 签名（HmacSHA256 + session key）
- @WireLocked 注解（防 wire:model 篡改）
- 参数全 String + 结构化解析（禁用 eval）
- WireParentOverride 运行时 @extends 覆盖

---

## 单组件后台 CRUD（列表 + 对话框 + 删除 一体）

Livewire 风格的核心理念是「一个组件干完 CRUD 全套」。以管理员管理为例，**列表、新增、修改（对话框）、删除全部由同一个 `AdminController` 组件承担**，路由只分 3 条（不是 4 条）：

```java
Route.prefix("/admin").name("admin").group(() -> {
    Route.get("/", "AdminController::index").name("index");
    Route.get("/change", "AdminController::index").name("change.index"); // 直访=整页表单
    Route.post("/change", "AdminController::update").name("change");     // wire 局部更新端点
});
```

组件内每个动作都是普通方法（`list`/`add`/`edit`/`delete`/`save`），由前端 `wire:click`/`wire:submit` 触发，**不是**独立的 Route 方法：

```java
public class AdminController extends WireController {
    @WireLocked public List<Admin> list;   // 列表数据大,标记 @WireLocked 不进快照
    public boolean fullPageForm;           // 直访 /change 时为 true
    public Long id; public String number; public String name; /* ...表单字段... */

    @Override protected WireView render() {
        // 整页表单(直接访问 /change)渲染 form 模板;否则渲染 list 模板
        return fullPageForm
            ? wireView("mdui.admin.admin.item")
            : wireView("mdui.admin.admin.list", Map.of("list", list));
    }

    @Override protected void mount(Request request) {
        // ① 对话框放 @section('modals'),排除出 wire 可更新区 → 永不重建,彻底避免遮罩/标题错乱
        WireManager.addExcludedSections("modals");
        // ② Spring 单例 Bean:每次 index 必须重置表单字段,否则上次请求残留的 id 会泄漏进快照
        this.id = null; this.number = null; this.name = null; /* ... */
        this.fullPageForm = request.uri() != null && request.uri().endsWith("/change");
        this.list = queryList();
    }

    @Override protected String getUpdateRouteName() { return "admin.admin.change"; }

    public void add()  { WireEffects.dispatch("wire-admin-open-dialog", emptyData()); }
    public void edit(Long id) {
        Admin a = Admin.self().find(id.toString()).toObject();
        WireEffects.dispatch("wire-admin-open-dialog", Map.of("id", a.getId(), "name", a.getName() /* ... */));
    }
    public void delete(Long id) { Admin.self().find(id.toString()).delete(); }

    public void save() {
        Admin a = (id != null) ? Admin.self().find(id.toString()).toObject() : new Admin();
        if (a == null) a = new Admin();         // find() 记录不存在时返回 null,需判空
        a.setName(name); /* ... */ a.save();
        this.list = queryList();
        wire().component("toast", Map.of("message", "保存成功", "type", "success"));
        if (!fullPageForm) WireEffects.dispatch("wire-admin-close-dialog", null);
    }
}
```

关键点：

1. **对话框稳定性**：把对话框放进模板的 `@section('modals')`，并在 `mount()` 里 `WireManager.addExcludedSections("modals")`。`modals` 区不会被 wire 的局部刷新重建，因此对话框 DOM 永远稳定——`mdui` 的打开状态、焦点、标题都不会被冲掉。前端通过 `WireEffects.dispatch("wire-admin-open-dialog", data)` 拿到预填数据回填并 `open()`。
2. **对话框用普通命名字段 + 按钮 `wire:submit`**：对话框内输入框用 `name="xxx"`（**不要** `wire:model`），提交用 `<button wire:submit="save">`，前端走「读取 `FormData(form)` 全部字段」的路径（与表单是否带 `wire:model` 无关），从而不依赖缓存、不被 `$sync` 干扰。
3. **`$sync` 只回传快照、不重渲染**：`wire:model` 同步时服务端 `$sync` 动作只合并字段并返回新的 `snapshot`，**不返回任何 section HTML**，前端据此只更新本地快照、不替换任何 DOM——这正是对话框在 `$sync` 下不崩溃的根本原因。
4. **`@WireLocked` 防快照膨胀**：列表这类大对象用 `@WireLocked` 标记，既不进快照（避免把整张表序列化到客户端），又在每次 wire 更新后由 `refresh()` 重新从 DB 查询。
5. **单例字段重置**：`AdminController` 是 Spring 单例，`mount()` 不重置字段会导致上一次 `edit(13)` 的 `id=13` 泄漏到下一次直访 `/change` 的快照，使 `save()` 误判为「更新」并对已删除记录 NPE。务必在 `mount()` 起点重置所有表单字段。

---

## 旧 API 参考（已废弃，以下章节保留供迁移参考）

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
- [11. 手动控制 wire.js 注入](#11-手动控制-wirejs-注入)
- [12. 前端事件系统](#12-前端事件系统)
- [13. Section 排除列表](#13-section-排除列表)
- [14. 完整控制器示例](#14-完整控制器示例)
- [15. 线程安全说明](#15-线程安全说明)
- [16. 命名组件（toast / confirm 等临时事务）](#16-命名组件toast--confirm-等临时事务)
  - [16.1 设计动机](#161-设计动机)
  - [16.2 三步接入](#162-三步接入)
  - [16.3 四个生命周期与 wire.stop()](#163-四个生命周期与-wirestop)
  - [16.4 WireOutlet 加载位置中间件](#164-wireoutlet-加载位置中间件)
  - [16.5 组件间隔离机制](#165-组件间隔离机制)
  - [16.6 配置项](#166-配置项)
  - [16.7 与 Wire 透明导航协同](#167-与-wire-透明导航协同)
- [17. 透明导航（Transparent Navigation）](#17-透明导航transparent-navigation)

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
├── WireManager          // 核心工具类：Wire 模式渲染、section 提取、snapshot 编解码、资源注入
├── component            // 命名组件（toast / confirm 等临时事务，见第 16 节）
│   ├── WireOutlet              // 加载位置中间件：注入 outlet 容器 + bootstrap + 前端运行时
│   ├── WireComponents          // 注册表（ConcurrentHashMap）+ 待下发队列（ThreadLocal）
│   ├── WireComponentDefinition // 组件定义：name / template / defaults
│   ├── WireComponentPayload    // 下发载荷：id / name / html / script / params
│   └── WireComponentRenderer   // 渲染器：普通 Blade 渲染 + 抽离 <script wire:lifecycle>
└── springboot
    ├── WireProperties       // SpringBoot 配置属性（@ConfigurationProperties, prefix=jaravel.wire）
    ├── WireAutoConfiguration // SpringBoot 自动装配（@ConditionalOnProperty 控制）
    └── WireComponentAutoConfiguration // 注册 WireOutlet 别名、wire_outlet() 模板函数、命名组件

resources/static/
├── wire.js              // 前端运行时：事件绑定、局部更新、双向绑定、认证过期处理（零依赖）
└── wire-component.js    // 命名组件运行时：四生命周期、逐实例闭包隔离、stop 语义（零依赖）
```

---

## 3. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>wire</artifactId>
    <version>0.1.2</version>
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

> 运行环境要求：JDK 17+，Spring Boot 3.2.12（Jakarta Servlet）。使用前需通过 `WireManager.setEngine(bladeEngine)` 注入 Blade 引擎实例（通常由 `springboot` 模块的 Starter 自动完成）。

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
| `responseWire` | 无 | `Response` | 直接生成 Wire 初始页面响应（HTML）。等同于 `WireResponse.wire(templateName, data, updateUrl)`。是否注入 wire.js 受 `WireManager.isAutoInjectJs()` 控制 |
| `responseWire` | `boolean injectJs` | `Response` | 直接生成 Wire 初始页面响应（HTML），显式指定是否注入 wire.js。`injectJs=false` 时只注入 wire:config 配置标签 |
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
| `wire` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `Response` | 初始页面渲染：渲染模板 + 注入 Wire 资源（wire.js + snapshot + updateUrl）。返回完整 HTML 页面。是否注入 wire.js 受 `WireManager.isAutoInjectJs()` 控制 |
| `wire` (static) | `String templateName, Map<String,Object> data` | `Response` | 初始页面渲染（使用默认 update URL: `/wire/update`） |
| `wire` (static) | `String templateName, Map<String,Object> data, String updateUrl, boolean injectJs` | `Response` | 初始页面渲染，显式指定是否注入 wire.js。`injectJs=false` 时只注入 wire:config 配置标签，不注入 `<script src="...">` 标签 |

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
| `from` (static) | `Request request` | `WireRequest` | 从 Jaravel Request 解析 Wire 请求体。仅信任 HTTP 层已解析写入的 `request.input("wire_body")` / `request.get("wire_body")`；读不到直接抛 `IllegalStateException`（不做兜底序列化） |
| `fromJson` (static) | `String json` | `WireRequest` | 直接从 JSON 字符串解析 |
| `getSnapshot` | 无 | `String` | 获取 snapshot（Base64 编码） |
| `getAction` | 无 | `String` | 获取 action 名称 |
| `getParams` | 无 | `Map<String,Object>` | 获取 params（可能为空 Map） |
| `getSections` | 无 | `List<String>` | 获取需要更新的 section 名列表 |
| `getData` | 无 | `Map<String,Object>` | 从 snapshot 解码出原始数据 Map |
| `getMergedData` | 无 | `Map<String,Object>` | 将 params 合并到 snapshot 数据中（用于 wire:model 的属性更新） |

`from` 解析顺序说明：前端 wire.js 以 `wire_body=<JSON>` 的 form-urlencoded 形式 POST，HTTP 层（`RequestFactory.handleFormUrlEncodedRequest`）统一用 `getInputStream()` 缓存式读取 body 并写入 `request.input("wire_body")`。`from` 只信任该字段，读不到即抛 `IllegalStateException`（由 HTTP 层保证解析，Wire 层不做兜底序列化）。解析失败抛 `RuntimeException("解析 Wire 请求失败")`。

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
| `setJsPath` (static) | `String path` | `void` | 设置 wire.js 的外部引用路径（注入到 HTML 中的 script src）。用于自定义静态资源服务路径，如 CDN 或自定义路由前缀。`null` 回退为 `/static/wire.js` |
| `getJsPath` (static) | 无 | `String` | 获取 wire.js 的外部引用路径（默认 `/static/wire.js`） |
| `setAutoInjectJs` (static) | `boolean autoInject` | `void` | 设置是否自动注入 wire.js 的 script 标签。设为 `false` 后，`injectWireAssets` 只注入 wire:config 配置标签 |
| `isAutoInjectJs` (static) | 无 | `boolean` | 是否自动注入 wire.js（默认 `true`，向后兼容） |
| `getWireJsContent` (static) | 无 | `String` | 从 classpath 读取 `/static/wire.js` 完整内容并返回。用于手动引入场景：可修改静态资源请求路径后内联到页面，或通过自定义路由提供修改后的 JS 内容 |
| `renderForWire` (static) | `String templateName, Map<String,Object> data` | `String` | 以 Wire 模式渲染模板（完整页面）。设置 `__wire_mode = true`，使 `@yield` 输出被 `<div wire:section="name">` 包裹 |
| `renderSection` (static) | `String templateName, String sectionName, Map<String,Object> data` | `String` | 渲染指定 section 的内容（不含布局） |
| `renderSections` (static) | `String templateName, List<String> sectionNames, Map<String,Object> data` | `Map<String,String>` | 批量渲染多个 section（高效：只加载和初始化模板一次） |
| `getSectionNames` (static) | `String templateName` | `List<String>` | 获取模板中所有已注册的 section 名 |
| `encodeSnapshot` (static) | `Map<String,Object> data` | `String` | 将数据 Map 编码为 Base64 JSON 快照（自动过滤 `__wire` 前缀的内部字段） |
| `decodeSnapshot` (static) | `String base64` | `Map<String,Object>` | 从 Base64 JSON 快照解码出数据 Map（空串返回空 Map） |
| `injectWireAssets` (static) | `String html, String updateUrl, String snapshot` | `String` | 将 Wire 资源（snapshot + updateUrl + wire.js）注入到 HTML 的 `</body>` 前。是否注入 wire.js 受 `isAutoInjectJs()` 控制 |
| `injectWireAssets` (static) | `String html, String updateUrl, String snapshot, boolean injectJs` | `String` | 将 Wire 资源注入到 HTML 的 `</body>` 前，显式指定是否注入 wire.js。`injectJs=false` 时只注入 wire:config 配置标签 |
| `renderWirePage` (static) | `String templateName, Map<String,Object> data, String updateUrl` | `String` | 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源。是否注入 wire.js 受 `isAutoInjectJs()` 控制 |
| `renderWirePage` (static) | `String templateName, Map<String,Object> data, String updateUrl, boolean injectJs` | `String` | 完整的 Wire 初始渲染：渲染模板 + 注入 Wire 资源，显式指定是否注入 wire.js |

注入的 Wire 资源结构（`injectJs=true` 时）：

```html
<script type="application/json" wire:config
        data-wire-update="/api/wire/admin"
        wire:snapshot="base64snapshot"></script>
<script src="/static/wire.js"></script>
```

当 `injectJs=false` 时，只注入 wire:config 配置标签，不注入 wire.js 的 script 标签。开发者可通过 `WireManager.getWireJsContent()` 获取 wire.js 完整内容，自行内联或通过自定义路径引入。

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

`wire:model` 实现了真正的双向绑定：前端输入会以 `params = {field: value}` 的形式发送 `$sync` action 到服务端。**`$sync` 只回传新的 `snapshot`（不返回任何 section HTML）**，前端据此只更新本地快照、不替换任何 DOM，从而保持对话框、输入框等局部组件存活（这正是把对话框放进 `@section('modals')` 后 `$sync` 不再冲垮对话框的根本原因）。对于需要刷新展示数据的场景，请使用 `wire:click` 触发普通 action 或在 action 中调用 `refresh()`。

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

## 11. 手动控制 wire.js 注入

默认情况下，Wire 渲染页面时会自动在 `</body>` 前注入 `<script src="/static/wire.js">` 标签。从 0.1.2 版本开始，支持手动控制 wire.js 的注入行为，适用于以下场景：

- **CDN 引入**：将 wire.js 托管到 CDN，通过自定义路径引用
- **内联引入**：将 wire.js 内容直接内联到 HTML 中，减少一次 HTTP 请求
- **自定义路由**：通过自定义路由提供修改后的 wire.js 内容
- **SPF / SSR 场景**：由前端框架统一管理脚本加载

### 11.1 SpringBoot 配置方式（推荐）

通过 `application.yml` 配置，由 `WireAutoConfiguration` 自动应用到 `WireManager`：

```yaml
jaravel:
  wire:
    enabled: true
    auto-inject-js: false       # 关闭自动注入
    js-path: /assets/wire.js    # 自定义 JS 引用路径（auto-inject-js=true 时生效）
```

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.wire.enabled` | `boolean` | `true` | 是否启用 Wire 自动装配 |
| `jaravel.wire.auto-inject-js` | `boolean` | `true` | 是否自动注入 wire.js 的 script 标签。设为 `false` 后渲染时只注入 wire:config 配置标签 |
| `jaravel.wire.js-path` | `String` | `/static/wire.js` | wire.js 的外部引用路径（注入到 HTML 中的 script src） |

配置后，所有 Wire 页面渲染都会遵循该设置，无需修改控制器代码：

```java
// 控制器代码无需修改，行为受配置控制
public Response page(Request request) {
    return WireService.from(request, "wire-demo", "/api/wire/demo")
        .once("count", 0)
        .responseWire();  // auto-inject-js=false 时不会注入 <script src="...">
}
```

### 11.2 编程式控制

通过 `WireManager` 静态方法全局控制：

```java
// 全局关闭自动注入
WireManager.setAutoInjectJs(false);

// 自定义 JS 引用路径（当 autoInjectJs=true 时使用）
WireManager.setJsPath("/assets/wire.js");

// 获取当前配置
boolean autoInject = WireManager.isAutoInjectJs();  // false
String jsPath = WireManager.getJsPath();            // "/assets/wire.js"
```

### 11.3 按请求显式控制

使用带 `injectJs` 参数的重载方法，按需控制每次渲染的注入行为：

```java
// WireService：显式指定不注入 wire.js
public Response page(Request request) {
    return WireService.from(request, "wire-demo", "/api/wire/demo")
        .once("count", 0)
        .responseWire(false);  // 不注入 wire.js
}

// WireResponse：显式指定不注入 wire.js
public Response page() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("count", 0);
    return WireResponse.wire("counter", data, "/api/wire/counter", false);
}

// WireManager：显式指定不注入 wire.js
String html = WireManager.renderWirePage("counter", data, "/api/wire/counter", false);
```

### 11.4 获取 wire.js 内容内联

使用 `getWireJsContent()` 从 classpath 读取 wire.js 完整内容，可内联到页面或通过自定义路由提供：

```java
// 方式 1：内联到页面（减少 HTTP 请求）
WireManager.setAutoInjectJs(false);
String html = WireManager.renderWirePage("counter", data, "/api/wire/counter", false);
String jsContent = WireManager.getWireJsContent();
html = html.replace("</body>", "<script>" + jsContent + "</script>\n</body>");

// 方式 2：通过自定义路由提供修改后的 JS
router.get("/custom/wire.js", (req) -> {
    String js = WireManager.getWireJsContent();
    // 可修改 JS 内容中的静态资源请求路径等
    return ResponseBuilder.html(js).contentType("application/javascript");
});
```

### 11.5 WireProperties 与 WireAutoConfiguration

SpringBoot 适配层提供 `WireProperties`（`@ConfigurationProperties(prefix = "jaravel.wire")`）和 `WireAutoConfiguration`（`@ConditionalOnProperty`）：

```java
// WireProperties — 配置属性类
@ConfigurationProperties(prefix = "jaravel.wire")
public class WireProperties {
    private boolean enabled = true;
    private boolean autoInjectJs = true;
    private String jsPath = "/static/wire.js";
    private List<String> excludedSections = new ArrayList<>();
    // getter/setter...
}

// WireAutoConfiguration — 自动装配
@AutoConfiguration
@ConditionalOnClass(WireManager.class)
@ConditionalOnProperty(prefix = "jaravel.wire", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WireProperties.class)
public class WireAutoConfiguration {
    public WireAutoConfiguration(WireProperties properties) {
        WireManager.setAutoInjectJs(properties.isAutoInjectJs());
        WireManager.setJsPath(properties.getJsPath());
        WireManager.addExcludedSections(properties.getExcludedSections().toArray(new String[0]));
    }
}
```

> 非 SpringBoot 环境下，可在应用启动时手动调用 `WireManager.setAutoInjectJs(false)`、`WireManager.setJsPath(...)` 和 `WireManager.addExcludedSections(...)` 进行配置。

---

## 12. 前端事件系统

`wire.js` 暴露全局对象 `Wire`，提供前端事件系统。通过 `Wire.on` / `Wire.off` 注册和移除事件监听器，可在 Wire 生命周期钩子中执行自定义逻辑——典型场景是 DOM 更新后刷新第三方 UI 框架组件（如 mdui 的 `mdui.mutation()`）。事件监听器全局生效，对所有 Wire 组件实例触发。

### 12.1 API

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `Wire.on` | `String event, Function callback` | 无 | 注册事件监听器，支持多次调用注册多个监听器 |
| `Wire.off` | `String event, Function callback` | 无 | 移除指定事件监听器。不传 `callback` 时移除该事件的所有监听器 |

### 12.2 支持的事件

| 事件 | 参数 | 触发时机 |
| --- | --- | --- |
| `beforeRequest` | `(component, action, params)` | **发起 HTTP 请求前**触发（新增） |
| `afterRequest` | `(component, data)` | **收到 HTTP 响应后、数据处理前**触发（新增） |
| `beforeUpdate` | `(component, action, params)` | 数据处理前触发（兼容旧版，与 `beforeRequest` 同时触发） |
| `afterUpdate` | `(component, data, sections)` | DOM 更新完成后触发 |

完整的请求生命周期顺序：

```
beforeRequest ──→ 发送 HTTP 请求 ──→ afterRequest ──→ 数据处理 ──→ beforeUpdate ──→ DOM 更新 ──→ afterUpdate
    │                (fetch)              (收到 JSON)     (handleResponse)          (replaceSection)       (rebindSection)
```

**参数说明**：

- `component`：当前 wire 组件对象
- `action`：即将执行的 action 名称
- `params`：action 参数
- `data`：服务端返回的完整响应 JSON 数据（含 sections、effects、snapshot）
- `sections`：本次更新的 section 列表

### 12.3 使用示例

```javascript
// 发起请求前：展示 loading 状态
Wire.on('beforeRequest', function(component, action, params) {
    showLoading(component);
});

// 收到响应后：解析响应并做预处理
Wire.on('afterRequest', function(component, data) {
    if (data.errors) {
        showErrors(data.errors);
    }
});

// DOM 更新后：刷新第三方 UI 框架组件
Wire.on('afterUpdate', function(component, data, sections) {
    mdui.mutation();  // 重新扫描并初始化 mdui 组件
});

// 更新前可以做些准备工作（兼容旧版）
Wire.on('beforeUpdate', function(component, action, params) {
    console.log('即将执行 action:', action);
});

// 移除事件监听器
Wire.off('afterUpdate', afterUpdateHandler);
```

---

## 13. Section 排除列表

`WireManager` 新增排除列表功能，可以让某些 section/slot 不被 `<!--wire:section-start/end:name-->` 标记包裹，从而不被前端 `wire.js` 识别为可更新区域。适用于 header、footer 等不需要局部更新的全局区域。

### 13.1 设计原理

不修改 jblade 本身，通过后处理渲染结果移除排除 section 的 `<!--wire:section-start/end:name-->` 标记。被排除的 section 仍会正常渲染 HTML，但不会被 wire.js 纳入局部更新范围。

### 13.2 API

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `addExcludedSections` (static) | `String... sectionNames` | `void` | 添加排除的 section 名（可变参数） |
| `removeExcludedSection` (static) | `String sectionName` | `void` | 移除单个排除的 section 名 |
| `getExcludedSections` (static) | 无 | `List<String>` | 获取当前排除的 section 名列表 |
| `clearExcludedSections` (static) | 无 | `void` | 清空排除列表 |
| `isExcluded` (static) | `String sectionName` | `boolean` | 检查指定 section 名是否在排除列表中 |

### 13.3 SpringBoot 配置

通过 `application.yml` 配置，由 `WireAutoConfiguration` 自动应用到 `WireManager`：

```yaml
jaravel:
  wire:
    excluded-sections:
      - header
      - footer
```

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.wire.excluded-sections` | `List<String>` | `[]`（空列表） | 排除的 section 名列表，不被前端 wire.js 识别为可更新区域 |

### 13.4 编程式控制

```java
// 添加排除的 section
WireManager.addExcludedSections("header", "footer");

// 检查是否被排除
WireManager.isExcluded("header");   // true

// 移除单个排除
WireManager.removeExcludedSection("header");

// 清空排除列表
WireManager.clearExcludedSections();
```

---

## 14. 完整控制器示例

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

对应模板 `wire-demo.blade.java`：

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

## 15. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `WireService` | **单请求隔离** | 每次请求通过 `from` / `of` 创建新实例，内部 `data` / `sections` / `actionHandlers` 为请求私有。不应跨请求共享同一个 `WireService` |
| `WireResponse` | **单响应隔离** | 每次调用静态工厂方法创建新实例，构建器状态为本次响应私有。`build()` 后不应再修改 |
| `WireRequest` | **单请求隔离** | 每次请求通过 `from` 创建新实例，字段 `final`，不可变 |
| `WireManager` | **线程安全** | 无状态工具类，所有方法为静态方法。`engine` 静态字段在启动阶段单次写入后只读。`ObjectMapper` 为静态 final 线程安全。可在并发请求间安全复用 |
| `wire.js` | 单组件隔离 | 前端运行时，每个 `wire:config` 对应一个 component 实例，`boundElements` Set 防止重复绑定 |
| `WireComponents` | **线程安全 + 请求级隔离** | 注册表 `REGISTRY` 是 `ConcurrentHashMap`（启动期写、运行期读）；待下发队列 `PENDING` 是 `ThreadLocal`，天然按请求隔离，由 `WireOutlet` 在 `finally` 中兜底清理 |
| `WireComponentRenderer` | **线程安全** | 无状态静态工具类，实例序号用 `AtomicLong` 自增 |
| `WireOutlet` | **线程安全** | 中间件实例无请求态字段，配置项在启动期单次写入后只读 |
| `wire-component.js` | 单实例隔离 | 每个实例的生命周期脚本用 `new Function` 单独求值，各自独立闭包，`wire.stop()` 只作用于自身 |

> `WireService` / `WireResponse` / `WireRequest` 设计为「用完即弃」的请求级对象，不可跨请求复用。`WireManager` 是无状态工具类，可安全地在并发环境下调用。

---

## 16. 命名组件（toast / confirm 等临时事务）

### 16.1 设计动机

消息提示、确认框这类**临时事务型 UI** 有三个特点：与页面主数据无关、生命周期短、可能同时存在多个实例。
把它们塞进 Wire 的 section 体系并不合适——它们不需要 snapshot、不需要局部刷新，只需要「后端说一声，前端弹出来，展示完自己消失」。

命名组件机制为此而生：

- **模板是普通 Blade 模板**，不需要 `wire:section`、不需要 snapshot，也不参与 Wire 局部刷新；
- **在启动期注册名称 → 模板的映射**（类似 Blade 注册自定义指令）；
- **后端一行代码下发**：`WireService.responseComponent(name, params)`；
- **前端完全无感**：`wire-component.js` 自动挂载、自动执行生命周期、自动移除。

### 16.2 三步接入

**① 注册命名组件**（配置式，推荐）：

```yaml
jaravel:
  wire:
    components:
      toast: components.toast       # 名称 -> 模板路径
      confirm: components.confirm
```

也可以在启动期编程式注册：

```java
WireComponents.register("toast", "components.toast");
WireComponents.registerAll(Map.of("confirm", "components.confirm"));
```

**② 写模板**（`templates/components/toast.blade.java`）——普通模板片段，不需要 `@extends`：

```html
<div class="wc-toast wc-toast--{{ $level }}" id="{{ $wireId }}">
    <span class="wc-toast__icon">{{ $icon }}</span>
    <span class="wc-toast__msg">{{ $message }}</span>
</div>

<script wire:lifecycle>
    var timer = null;
    function onCreate(el, params, wire) { /* 入场前：算堆叠位移等 */ }
    function onStart(el, params, wire) {
        el.classList.add('is-in');
        timer = setTimeout(function () { wire.stop(); }, params.ttl || 3000);
    }
    function onStop(el, params, wire) {
        el.classList.remove('is-in');
        return 280;              // 返回毫秒数 -> 延后 280ms 再移除 DOM（播完退场动画）
    }
    function onDestroy(el, params, wire) { clearTimeout(timer); }
</script>
```

**③ 后端下发**：

```java
// 路径 A：Wire 更新响应中下发（随 effects.components 返回）
return WireService.from(request, "my-page", "/api/my-page")
        .action("save", c -> {
            // ... 业务逻辑
            c.responseComponent("toast", Map.of(
                    "level", "success", "icon", "✓",
                    "message", "保存成功", "ttl", 3000));
        })
        .responseUpdate();

// 路径 B：首屏 / 普通页面下发（由 WireOutlet 中间件注入 bootstrap）
WireComponents.push("toast", Map.of("level", "info", "message", "欢迎回来"));
return ResponseBuilder.view("dashboard", data);

// 路径 C：WireResponse 链式写法
return WireResponse.update(sections, snapshot)
        .withComponent("toast", Map.of("message", "已更新"));
```

三条路径都不需要改模板、不需要前端写任何挂载代码。

### 16.3 四个生命周期与 wire.stop()

| 钩子 | 触发时机 | 典型用途 | 返回值语义 |
| --- | --- | --- | --- |
| `onCreate(el, params, wire)` | DOM 已创建、**尚未插入文档** | 计算初始位置、设置初始样式（避免闪烁） | 忽略 |
| `onStart(el, params, wire)` | DOM 已插入文档 | 播入场动画、绑定事件、起 ttl 定时器 | 忽略 |
| `onStop(el, params, wire)` | 调用 `wire.stop()` 后、**移除 DOM 之前** | 播退场动画 | 返回 `number` → 延迟该毫秒数后再移除；返回 `Promise` → 等它 resolve 后再移除 |
| `onDestroy(el, params, wire)` | DOM 已从文档移除 | 清定时器、解绑全局监听 | 忽略 |

四个钩子**全部可选**，只实现需要的即可。

`wire` 参数由运行时注入，提供：

- `wire.stop()` —— 模板内主动声明「我展示完了，移除我」。这是结束一个组件的**唯一入口**；
- `wire.id` —— 当前实例的唯一 id；
- `wire.params` —— 后端下发的参数对象。

```html
<script wire:lifecycle>
    function onStart(el, params, wire) {
        el.querySelector('[data-wc-ok]').addEventListener('click', function () {
            document.dispatchEvent(new CustomEvent('wc:confirm',
                { detail: { id: wire.id, ok: true } }));
            wire.stop();          // ← 用户点了确定，结束本实例
        });
    }
</script>
```

### 16.4 WireOutlet 加载位置中间件

`WireOutlet` 是仿 `VerifyCsrfToken` 设计的中间件，负责三件事：

1. 标记本次请求「命名组件可用」（模板函数 `wire_outlet()` 据此决定是否输出容器）；
2. 请求结束后取走本次积压的组件，注入到响应里（HTML 走 bootstrap `<script>`，Wire 导航 JSON 走 `components` 字段）；
3. 自动注入前端运行时 `wire-component.js`。

**默认挂载位置**在 `RouteServiceProvider` 的 Web 分组末尾（不在 `Web.java` 里逐条声明）：

```java
// RouteServiceProvider
Route.group(new String[]{"VerifyCsrfToken", "WireOutlet"}, () -> {
    // ... web 路由
});
```

**加载位置**默认是 `</body>` 之前（`position: body-end`），也可以在模板里用 `wire_outlet()` 显式指定：

```html
<div class="my-notification-area">
    {!! wire_outlet() !!}
</div>
```

中间件检测到页面里已经有 `data-wire-outlet` 属性（即 `{!! wire_outlet() !!}` 输出的容器或手动添加的 `<div wire:outlet>`）就**不会重复注入**，因此显式指定与自动注入不会冲突。
中间件未启用（例如该路径在 `except` 里）时，`wire_outlet()` 返回空字符串，不会留下无人管理的空容器。

> **前端兜底（无需任何配置）**：即使页面既没有注册 `WireOutlet` 中间件、也没有手写 `wire:outlet` 容器，前端运行时在挂载命名组件时也会**自动在 `<body>` 末尾创建一个默认 `<div id="wire-outlet" wire:outlet>` 容器**并把组件挂进去。因此开发者不声明 outlet 也绝不会看到「找不到 outlet 容器」的告警，toast / confirm 等临时组件始终能正常弹出。该自动容器只创建一次，幂等；若页面后续又显式出现 `wire:outlet`，则直接使用已存在的那个。

**例外配置**支持精确匹配与前缀通配：

```yaml
jaravel:
  wire:
    outlet:
      except:
        - /login              # 精确匹配
        - /demo/storage/*     # 前缀通配
```

### 16.5 组件间隔离机制

同一页面可能同时存在多个实例（包括同名的多个 toast），隔离靠三层保证：

1. **实例 id 唯一**：渲染时生成 `wc-{name}-{seq}`，模板内可用 `{{ $wireId }}`（组件名为 `{{ $wireName }}`）引用，
   用于作用域化 DOM id 与 CSS，DOM 查询天然隔离；
2. **生命周期脚本逐实例求值**：渲染器把 `<script wire:lifecycle>` 从 HTML 中**抽离**到 payload 的 `script` 字段，前端用
   `new Function(script + '; return {onCreate, onStart, onStop, onDestroy}')` 对**每个实例单独求值**，
   得到各自独立的闭包作用域——上例中的 `var timer` 在每个实例里都是独立变量，互不覆盖；
3. **stop 只作用于自身**：`wire.stop()` 是绑定到当前实例的闭包方法，一个实例结束不会影响其他实例。

> 为什么要把 `<script>` 抽出来？因为通过 `innerHTML` 插入的 `<script>` 标签**不会被浏览器执行**。
> 抽离后由运行时显式求值，既解决了执行问题，又顺带拿到了闭包隔离。

组件载荷结构：

```json
{
  "id": "wc-toast-7",
  "name": "toast",
  "html": "<div class=\"wc-toast ...\">...</div>",
  "script": "var timer=null; function onStart(el, params, wire){...}",
  "params": { "level": "success", "message": "保存成功", "ttl": 3000 }
}
```

### 16.6 配置项

```yaml
jaravel:
  wire:
    components:                          # 命名组件注册表：名称 -> 模板
      toast: components.toast
      confirm: components.confirm
    outlet:
      enabled: true                      # 是否启用加载位置注入
      position: body-end                 # body-end | body-start
      auto-inject-js: true               # 是否自动注入 wire-component.js
      js-path: /static/wire-component.js
      except:                            # 不注入的路径，支持 /path 与 /prefix/*
        - /login
```

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.wire.components` | `Map<String,String>` | `{}` | 命名组件注册表，键为组件名，值为 Blade 模板路径 |
| `jaravel.wire.outlet.enabled` | `boolean` | `true` | 是否启用 outlet 注入 |
| `jaravel.wire.outlet.position` | `String` | `body-end` | 自动注入位置：`body-end`（`</body>` 前）/ `body-start`（`<body>` 后） |
| `jaravel.wire.outlet.auto-inject-js` | `boolean` | `true` | 是否自动注入 `wire-component.js` |
| `jaravel.wire.outlet.js-path` | `String` | `/static/wire-component.js` | 前端运行时路径 |
| `jaravel.wire.outlet.except` | `List<String>` | `[]` | 不注入 outlet 的路径，支持精确匹配与 `*` 前缀通配 |

配置由 `WireComponentAutoConfiguration` 在启动时应用，并注册 `WireOutlet` 中间件别名与 `wire_outlet()` 模板函数。
注册失败会直接抛 `IllegalStateException` 让应用启动失败，避免运行期才发现「指令不存在」。

### 16.7 与 Wire 透明导航协同

三条下发路径都能正常工作：

| 场景 | 响应形态 | 组件注入位置 | 前端挂载方 |
| --- | --- | --- | --- |
| 首屏 / 整页加载 | HTML | `<script type="application/json" wire:components>` bootstrap | `wire-component.js` 自扫描 |
| Wire 局部更新 | JSON | `effects.components` | `wire.js` 委派给 `WireComponent.mountAll` |
| Wire 透明导航 | JSON | 顶层 `components` | `wire-navigate.js` 委派给 `WireComponent.mountAll` |

三者共用同一份 `WireComponents` 队列与同一个渲染器，因此后端代码完全不需要区分当前是哪种请求。

---

## 关键概念

- **Snapshot（快照）**：组件状态的 Base64 JSON 编码，在客户端与服务端之间流转，使服务端无状态。
- **Section（区块）**：模板中可独立更新的区域，通过 `wire:section="name"` 标记。
- **Action（动作）**：前端触发的操作名称（如 `increment`、`save`），服务端通过 `WireService.action` 注册处理器。
- **Effects（副作用）**：响应中除 section 更新外的附加效果，如 `redirect`、`dispatch` 事件、`components` 命名组件。
- **命名组件（Wire Component）**：注册在名称下的普通 Blade 模板片段（toast / confirm 等），不参与局部刷新，
  由后端 `responseComponent(name, params)` 下发、前端按四个生命周期自动挂载与销毁。详见[第 16 节](#16-命名组件toast--confirm-等临时事务)。
- **Outlet（加载位置）**：命名组件在页面中的挂载点。默认由 `WireOutlet` 中间件注入到 `</body>` 前，
  也可在模板里用 `{!! wire_outlet() !!}` 显式指定。

## 配置

wire 模块支持通过 SpringBoot 配置文件控制 wire.js 的注入行为，配置前缀为 `jaravel.wire`：

```yaml
# application.yml
jaravel:
  wire:
    enabled: true                # 是否启用自动装配（默认 true）
    auto-inject-js: true         # 是否自动注入 wire.js 的 script 标签（默认 true）
    js-path: /static/wire.js     # wire.js 的外部引用路径（默认 /static/wire.js）
    excluded-sections:           # 排除的 section（不被 wire.js 识别为可更新区域）
      - header
      - footer
```

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.wire.enabled` | `boolean` | `true` | 是否启用 Wire 自动装配 |
| `jaravel.wire.auto-inject-js` | `boolean` | `true` | 是否自动注入 wire.js。设为 `false` 后只注入 wire:config 配置标签 |
| `jaravel.wire.js-path` | `String` | `/static/wire.js` | wire.js 的外部引用路径 |
| `jaravel.wire.excluded-sections` | `List<String>` | `[]`（空列表） | 排除的 section 名列表，不被前端 wire.js 识别为可更新区域 |

> 配置由 `WireAutoConfiguration` 在应用启动时自动应用到 `WireManager`。详见[第 11 节](#11-手动控制-wirejs-注入)。
>
> 命名组件相关的 `jaravel.wire.components` 与 `jaravel.wire.outlet.*` 配置见[第 16.6 节](#166-配置项)。

此外，wire 模块需要通过 `WireManager.setEngine(bladeEngine)` 注入 Blade 引擎实例。这通常由 `springboot` 模块的 Starter 在应用启动时自动完成。

```java
// 手动初始化（通常不需要，Starter 会自动处理）
WireManager.setEngine(bladeEngine);
```

静态资源 `wire.js` 通过 `http` 模块的 `Router.serveStatic` 服务：

```java
router.serveStatic("/static", "classpath:/static/", 3600);
```

---

## 17. 透明导航（Transparent Navigation）

> 在「Wire 组件（Livewire 风格）」之外，wire 模块还提供了一套**页面级透明导航**能力：拦截带 `wire-navigate` 的链接 → 发起 AJAX → 服务端只回传**变化的 section（最小 diff）** → 前端按 `<!--wire:section-start:NAME-->` 标记局部替换 DOM → 同步 `pushState` 历史。该功能深度结合 jblade 的 `@section` / `@yield` 模板继承，实现跨控制器、跨模板的**无感刷新**。

### 17.1 架构与数据流

```
浏览器                                    服务端
──────                                    ──────
<a href="/wire-records" wire-navigate>  ──GET /wire-records
                                          │  WireMiddleware 拦截（X-Wire-Navigate: true）
                                          │  WireMode.begin() + WireContext.begin(客户端上报 hash)
                                          │  next → 标准 ResponseBuilder.view() 渲染带标记 HTML
                                          │  WireRenderer.renderDiff(html, url)
                                          │    ├─ 按 <!--wire:section-start:NAME--> 抽取各 section
                                          │    ├─ FNV-1a 32-bit 计算各 section hash
                                          │    └─ 对比客户端上报 hash，仅保留变化的 section
                                          └─< WireDiffResponse: {sections, hashes, title, url}
XHR 收到 JSON diff
  ├─ 按 marker 替换变化的 section DOM（未变化的 DOM 完全不动）
  ├─ document.title = payload.title
  └─ history.pushState(url)
```

**核心类**：

| 类 | 职责 |
| --- | --- |
| `navigation/WireMiddleware` | 全局拦截器。非 Wire 请求：注入 `window.__wireHashes`；Wire 请求：开启 `WireMode` + `WireContext`，渲染后调用 `WireRenderer.renderDiff` 输出 `WireDiffResponse` |
| `navigation/WireRenderer` | 抽取 section、计算 FNV-1a hash、对比客户端 hash、生成最小 diff JSON |
| `navigation/WireContext` | `ThreadLocal` 保存客户端上报的 hash（`incomingHashes`） |
| `utils/WireMode` | `ThreadLocal` 标记当前是否为 Wire 渲染模式（`ResponseBuilder.view()` 读取以决定是否注入 `X-Template-Name` 等） |
| `static/wire-navigate.js` | 前端运行时：拦截 `wire-navigate` 链接、计算/上报 hash、应用 diff、管理 `pushState`/`popstate` |

### 17.2 首屏 hash 注入（消除 hash 口径差）

最早版本让前端用 DOM 序列化计算 section hash，而服务端用原始 HTML 子串哈希，两者口径不一致 → 每次导航都误判「全部变化」。修复方式：

- `WireMiddleware.injectInitialHashes()` 在**普通整页**响应中，若检测到 `<!--wire:section-start:` 标记，就用 `WireRenderer.computeHashes()` 算出与服务端 diff **同口径**的 hash，注入一段脚本：
  ```html
  <script>window.__wireHashes={"title":"a73a240e","head":"58488b10","sidebar":"05b1510a","content":"b6b910ab","scripts":"66fcc582"};</script>
  ```
- 前端 `wire-navigate.js` 的 `computeHashes()` 首屏**优先**使用 `window.__wireHashes`，不再依赖 DOM 序列化，从而与服务端完全一致。

### 17.3 最小 diff 算法（FNV-1a 32-bit）

`WireRenderer.hash()` 与前端 `wire-navigate.js` 使用**完全相同**的算法：

```java
int h = 0x811c9dc5;                       // FNV offset basis
for (int i = 0; i < content.length(); i++) {
    h ^= content.charAt(i);
    h *= 0x01000193;                      // FNV prime
}
return String.format("%08x", h);         // 8 位十六进制
```

`renderDiff()` 逻辑：

```java
Map<String,String> incoming = WireContext.getIncomingHashes();   // 客户端上报
Map<String,String> changed  = new LinkedHashMap<>();
for (var e : allSections.entrySet()) {        // allSections 来自服务端本次渲染
    String name = e.getKey();
    String newHash = allHashes.get(name);
    String oldHash = incoming.get(name);
    if (newHash != null && !newHash.equals(oldHash)) {
        changed.put(name, e.getValue());      // 仅保留 hash 变化的 section
    }
}
// 响应：changed 作为 sections，allHashes 作为新的 hashes
```

> 例：仪表盘 → 记录列表导航，二者共享 layout，`head`/`scripts` 区域完全相同（hash `58488b10`/`66fcc582` 一致），因此服务端**只回传 `title`/`sidebar`/`content` 三个变化区域**，`head`/`scripts` 完全不传输、前端也不触碰对应 DOM —— 这就是「无感刷新」。

### 17.4 协议细节

**请求头**（前端 `wire-navigate.js` 发出）：

| Header | 值 | 说明 |
| --- | --- | --- |
| `X-Wire-Navigate` | `true` | 标记本次为 Wire 导航请求（仅 GET） |
| `X-Wire-Hashes` | `title=xxx,head=yyy,...` | 客户端当前各 section 的 hash（`key=value` 逗号分隔） |
| `X-Requested-With` | `fetch` | 便于服务端识别 |

**响应体**（JSON）：

```json
{
  "sections": { "title": "...", "sidebar": "...", "content": "..." },
  "hashes":   { "title": "938cc03a", "head": "58488b10", "sidebar": "c14b6ca0", "content": "f1b69add", "scripts": "66fcc582" },
  "title": "记录列表 - Wire Demo",
  "url":   "/wire-records"
}
```

- `sections`：仅含**变化**的 section HTML（剥去 `<!--wire:section-start/end-->` 包裹，保留纯净内容）。
- `hashes`：本次渲染的**全部** section hash，前端据此更新本地 `currentHashes` 供下次导航对比。
- `title`：已剥去 wire 标记，前端直接赋给 `document.title`（不会把注释带进标签页）。

### 17.6 如何启用

1. **注册全局中间件**：在 `RouteServiceProvider`（或等价启动类）中把 `WireMiddleware` 注册为全局中间件，使其能拦截所有请求。
2. **模板用 `@yield`/`@section` 分区**：layout 用 `@yield('sidebar')`/`@yield('content')` 等划分可刷新区域，子模板用 `@section('content', ...)` 填充；`jblade` 的 `BladeTemplate.yieldSection()` 会**始终**输出 `<!--wire:section-start:NAME-->...<!--wire:section-end:NAME-->` 标记（首屏即带锚点，无需进入 Wire 模式）。
3. **链接加 `wire-navigate`**：`<a href="/other" wire-navigate>导航</a>`，前端运行时自动拦截并走 diff 导航。
4. **直访即整页**：不带 `X-Wire-Navigate` 头直接访问 URL → 返回完整 HTML（并注入 `window.__wireHashes`），行为与普通页面完全一致，SEO/刷新友好。

### 17.6 演示（jaravel demo）

`jaravel` 工程提供跨控制器、跨模板的无感导航演示：

| 路由 | 控制器 | 模板 | 说明 |
| --- | --- | --- | --- |
| `/wire` | `WireShowcaseController` | `wire/index.blade.java` | 仪表盘（含统计卡片、快速导航） |
| `/wire/records` | `WireShowcaseController` | `wire/records.blade.java` | 记录列表（与仪表盘共享 layout） |
| `/wire/spa` | `WireSpaController` | `wire/spa-overview.blade.java` | SPA 导航（左侧菜单切换） |
| `/wire/tasks` | `WireListController` | `wire/task-list.blade.java` | CRUD 任务列表（真实数据库） |
| `/wire/components` | `WireComponentController` | `wire/component-demo.blade.java` | 命名组件（toast/confirm）演示 |

以上页面共用 `wire-layout.blade.java`（`@yield` 出 `title`/`content` 等区域）。点击导航链接即可观察到 section diff 替换效果。

---

版本: 0.1.2
