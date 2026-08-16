# wire 模块

> Jaravel-Vendor 的全栈响应式 UI 框架模块，实现 Laravel Livewire 风格的服务端渲染 + 前端局部更新。包名统一为 `com.weacsoft.jaravel.vendor.wire`。
>
> **统一使用 `WireController` 抽象基类**（Livewire 风格全页组件）。旧的流式 API（`WireService`/`WireResponse`）已全部删除，新代码请使用 `WireController`。

---

## 目录

- [核心设计：组件与控制器强关联](#核心设计组件与控制器强关联)
- [快速开始：WireController（推荐）](#快速开始wirecontroller推荐)
- [单组件后台 CRUD（列表 + 对话框 + 删除 一体）](#单组件后台-crud列表--对话框--删除-一体)
- [布局替换：setWireLayoutReplace 就是 @extends 的替换](#布局替换setwirelayoutreplace-就是-extends-的替换)
- [组件下发与模板解析规则](#组件下发与模板解析规则)
- [三种请求处理](#三种请求处理)
- [交互式组件下发](#交互式组件下发)
- [命名组件（toast / confirm 等临时事务）](#命名组件toast--confirm-等临时事务)
- [分页无感切换（Pagination）](#分页无感切换pagination)
- [URL 查询参数：@WireQuery 注解](#url-查询参数wirequery-注解)
- [前端事件系统](#前端事件系统)
- [Section 排除列表](#section-排除列表)
- [IDEA 模板语法提示（XSD 命名空间校验）](#idea-模板语法提示xsd-命名空间校验)
- [认证过期无感重定向](#认证过期无感重定向)
- [手动控制 wire.js 注入](#手动控制-wirejs-注入)
- [安全机制](#安全机制)

---

## 核心设计：组件与控制器强关联

WireController 是「一个控制器 = 一个 Livewire 全页组件」。**模板、布局替换、组件下发、action 全部由同一个控制器承接**，因此以下声明必须写在控制器里（一次声明、处处生效），**禁止**写入配置文件：

| 声明 | 方法 | 说明 |
|------|------|------|
| 模板级布局替换 | `wireLayoutReplacements()` | 返回「模板名 → 替换布局名」，声明一次即可。**仅作用于组件下发渲染**；主页面渲染始终用模板自身的 `@extends` |
| 控制器强关联组件注册表 | `wireComponents()` | 返回「组件名 → 模板名」。对话框/表单等与控制器绑定的组件在此声明，**禁止**写入 `jaravel.wire.components` |
| 更新路由名 | `getUpdateRouteName()` | 组件 POST 更新端点的路由名（`wire:config` 的 `data-wire-update`） |
| 更新目标模板 | `getTemplateName()` | 局部 section 刷新的目标模板（固定页面如列表页时覆盖返回） |

配置文件 `jaravel.wire.components` **只允许放与控制器无关的全局命名组件**（toast / confirm 等临时事务组件）。

---

## 快速开始：WireController（推荐）

继承它并实现 `render()` 即可获得完整的 Wire 能力。

### 最小示例

```java
public class AdminController extends WireController {
    public Admin setting;

    @Override
    protected WireView render() {
        // render 只声明模板 + 额外数据。主页面布局由模板自身的 @extends 决定,
        // 组件下发渲染的布局替换由 wireLayoutReplacements() 声明式提供,
        // render 内不需要也不允许再套布局。
        return wireView("mdui.admin.admin.item", Map.of("setting", setting));
    }

    @Override
    protected void mount(Request request) {        // 注意:参数是 Request,不是 Map
        if (request.has("id"))
            setting = Admin.self().find(request.get("id", "")).toObject();
        else setting = new Admin();
    }

    @Override protected String getUpdateRouteName() { return "admin.admin.change"; }
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
Route.get("/change", "AdminController::index").name("change.index"); // 直访=整页表单
Route.post("/change", "AdminController::update").name("change");     // wire 局部更新端点
```

> 推荐用 `getUpdateRouteName()` 返回 POST 路由名（如 `admin.admin.change`），使列表页(GET)的 wire 局部更新指向正确的 POST 端点，而不是误打 GET 的 URI。

### 核心方法

| 方法 | 说明 |
|------|------|
| `render()` | **必须实现**。返回 `WireView` 配置；只声明模板 + 额外数据，**不要**调用 `.bladeExtends(...)`（主页面布局由模板自身 `@extends` 决定，组件布局替换由 `wireLayoutReplacements()` 提供） |
| `mount(Request)` | 可选。仅首次 `index()` 时调用，参数为 `Request`。**执行顺序**：框架先自动赋值（`request.all()` 按同名赋到 public 字段，带类型转换）再调用 `mount()`，因此 mount 里可直接读取 `this.page` 等已被赋值的字段，仅需处理字段名与参数名不一致的情况（如 `key`→`searchKey`）。Spring 单例 Bean 需在 `mount()` 里重置表单字段，避免上一次请求残留值泄漏进快照 |
| `fill(key, value)` / `fill(Map)` | 可选。把键值对**直接赋**到 Controller 自己的 public 属性（同名赋值 + 基础类型转换），不做任何业务重写 |
| `refresh(Map<String, Object> params)` | 可选。每次 wire 更新后重新加载展示数据（如重新查库），保持列表等数据最新。**调用时机**：`update()` 中 `invokeAction(action, params)` 执行完毕后、`renderSections()` 渲染 sections 前调用。**`params` 含义**：来自前端 POST 请求体 `wire_body` JSON 中的 `"params"` 字段，代表**本次请求前端传来的 action 参数**（不是快照状态）。例如：
  - `wire:click="delete(1)"` → `delete(Long id)` 由 `invokeAction` 按 `method(args)` 位置参数解析调用（`1` 经 `convertValue` 转 `Long`，见「参数化 action」）；`params` 字段作为同名下标回退（`params.get("0")`）。
  - `wire:click="$refresh"` → `params = null`（magic action 无参数）
  - `wire:model` 触发 `$sync` → `params` 包含同步的字段值（但 `$sync` 不会走 `invokeAction` → `refresh`，直接返回新快照）
  子类可忽略 params 直接重新查库，也可根据 params 决定加载哪些数据（如按 `params.get("page")` 分页）。对于 `@WireLocked` 的大字段（如列表），建议在 `refresh()` 中重新查询并赋值，确保每次 wire 更新后展示数据是最新的 |
| `wireLayoutReplacements()` | **声明式**模板级布局替换：返回「模板名 → 替换布局名」。如 `Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog")` 表示凡以组件形式下发渲染该模板时用对话框布局替换其 `@extends`。**声明一次即可，不要在每个 action 里重复调用**。仅作用于组件下发渲染；主页面渲染不受影响 |
| `setWireLayoutReplace(template, layout)` | 请求级临时布局替换（仅当前请求生效，ThreadLocal 请求末清除）。一般场景用声明式 `wireLayoutReplacements()` 即可，此方法仅用于个别 action 动态追加规则 |
| `getWireLayoutReplace(template)` | 查询替换规则（声明式 + 请求级合并，请求级优先）；未命中返回 null = 用模板自身 `@extends` |
| `wireComponents()` | **声明式**控制器强关联组件注册表：返回「组件名 → 模板名」。如 `Map.of("admin-form", "mdui.admin.admin.item")`。**禁止写入配置文件** |
| `getUpdateRouteName()` | 组件更新(POST)对应的路由名，`wire:config` 的 `data-wire-update` 指向它 |
| `getTemplateName()` | 局部 section 刷新的目标模板。默认取 `render().getTemplateName()`；若组件的 wire 更新目标是固定页面（如列表页），应覆盖返回该页面模板名，避免依赖请求状态（`fullPageForm` 等字段在 update() 中不更新） |
| `getRedirectUrl(request)` | **全局默认**重定向 URL：所有 wire 响应都会带上它（作为 `effects.redirect` 下发，前端整页跳转）。若你**只想在某个 action 后跳转**（如 `save()`），请勿重写此方法，改用 `WireEffects.redirect(url)` 在 action 内显式下发，否则每个 action（含 `add()`/`edit()`）都会触发整页跳转、把刚下发的对话框冲掉 |
| `wire()` | 下发**命名组件** `wire().component("name", params)`（toast / confirm 等带 `wire:lifecycle` 生命周期脚本的临时事务组件，见「命名组件」节） |
| `WireEffects.push(name, params)` | 下发**交互式组件**：渲染时注入 `wire:config` + 签名快照 + `data-wire-update`，使组件成为「活的」wire 组件（`wire:model` 双向绑定、`wire:submit` 提交均生效）。组件模板由 `wireComponents()` → 全局注册表 → 组件名兜底解析；如需套用 Dialog 等替换布局，在 `wireLayoutReplacements()` 中声明 |
| `WireEffects.redirect(url)` | 在 action 内**显式**下发重定向效果，仅当前 action 生效（区别于 `getRedirectUrl` 的全局默认）。典型：`save()` 末尾调用，保存成功后整页跳回列表 |
| `WireEffects.dispatch(name, data)` | 下发前端事件（`window.dispatchEvent(new CustomEvent(name, {detail:data}))`）。典型用于关闭对话框：`WireEffects.dispatch("wire-dialog-close", null)`，前端 `form.dialog` 监听后关闭、列表监听后 `Wire.refresh()`（均不整页刷新） |
| `WireEffects.pushUrl(url)` | 下发 URL 变更：`effects.url` 由前端 `history.pushState` 改变地址栏（如点击「修改」后 URL 变深链 `/admin/admin/change?id=5`），不刷新页面 |

### 三种请求处理

| 请求类型 | 触发条件 | 处理流程 |
|----------|---------|---------|
| 直访 GET | 无 wire_body | mount → render → 渲染整页（模板自身 `@extends`）→ 注入 wire assets |
| Wire POST | 含 wire_body | decodeSnapshot → invokeAction → renderSections(按 `getTemplateName()` 刷新) → 下发组件/事件/URL → JSON |
| 传统表单 POST | 无 wire_body 的 POST | fill(request.all()) → invokeAction("save") → redirect |

### 安全机制

- Snapshot HMAC 签名（HmacSHA256 + session key）
- @WireLocked 注解（防 wire:model 篡改、防大对象进快照）
- 参数全 String + 结构化解析（禁用 eval）
- WireParentOverride 运行时 @extends 覆盖（jblade 通过反射集成，仅组件下发渲染期生效）

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

组件内每个动作都是普通方法（`add`/`edit`/`delete`/`save`），由前端 `wire:click`/`wire:submit` 触发，**不是**独立的 Route 方法：

```java
public class AdminController extends WireController {
    @WireLocked public List<Admin> list;   // 列表数据大,标记 @WireLocked 不进快照
    public boolean fullPageForm;           // 直访 /change 且非 wire 请求时为 true(仅 render() 选择模板用)
    public Long id; public String number; public String name; /* ...表单字段... */

    @Override protected WireView render() {
        // 整页表单(直接访问 /change)渲染 item 模板(其自身 @extends('layouts.mdui.form') 整页表单);
        // 否则渲染 list 模板(自身 @extends('mdui.admin.main'))。
        return fullPageForm
            ? wireView("mdui.admin.admin.item")
            : wireView("mdui.admin.admin.list", Map.of("list", list));
    }

    // wire 局部更新始终以列表页为刷新对象(对话框是临时组件,不参与 section 刷新)
    @Override protected String getTemplateName() { return "mdui.admin.admin.list"; }

    // 声明式布局替换:凡以组件形式下发渲染 item 模板 → 套用 dialog 布局。一次声明,add()/edit() 无需重复调用。
    @Override protected Map<String, String> wireLayoutReplacements() {
        return Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog");
    }

    // 控制器强关联组件注册表:admin-form 对话框 → item 模板。禁止写入配置文件。
    @Override protected Map<String, String> wireComponents() {
        return Map.of("admin-form", "mdui.admin.admin.item");
    }

    @Override protected String getUpdateRouteName() { return "admin.admin.change"; }

    @Override protected void mount(Request request) {
        // Spring 单例 Bean:每次 index 必须重置表单字段,否则上次请求残留的 id 会泄漏进快照
        this.id = null; this.number = null; this.name = null; /* ... */
        // 整页表单 = 直访 /change 且非 wire 请求(列表点击是 wire POST → update(),mount 不执行,保持 false)
        String uri = request.uri();
        this.fullPageForm = uri != null && uri.endsWith("/change") && !useWireLayout(request);
        this.list = queryList();
        // 编辑预填(直访 /change?id=x):标题「修改/新增」由模板按 !empty($id) 判定,无需额外标记
        if (request.has("id")) { Admin a = Admin.self().find(request.get("id", "")).toObject(); if (a != null) copyFrom(a); }
    }

    // 点击「新增」:下发空表单对话框
    public void add() {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", null); data.put("number", null); /* 字段全空 */
        WireEffects.pushUrl("/admin/admin/change");       // 地址栏变深链,无整页刷新
        WireEffects.push("admin-form", data);             // 模板由 wireComponents() 解析,布局由 wireLayoutReplacements() 替换
    }

    // 点击「修改」:下发预填表单对话框
    public void edit(Long id) {
        Admin a = Admin.self().find(id.toString()).toObject();
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", a.getId()); data.put("number", a.getNumber()); /* 预填 */
        WireEffects.pushUrl("/admin/admin/change?id=" + id);          // 地址栏变深链
        WireEffects.push("admin-form", data);
    }

    public void delete(Long id) {
        Admin.self().find(id.toString()).delete();
        wire().component("toast", Map.of("message", "删除成功", "type", "success"));
    }

    public void save() {
        Admin a = (id != null) ? Admin.self().find(id.toString()).toObject() : new Admin();
        if (a == null) a = new Admin();         // find() 记录不存在时返回 null,需判空
        // 编辑留空密码 -> 保留原密码;新增留空 -> 用学号兜底(避免 admins.password NOT NULL)
        if (id != null && (password == null || password.isEmpty())) {
            var orig = Admin.self().find(id.toString()).toObject();
            password = (orig != null) ? orig.getPassword() : password;
        }
        if (password == null || password.isEmpty())
            password = (number != null && !number.isEmpty()) ? number : "123456";
        a.setNumber(number); /* ... */ a.save();
        this.list = queryList();
        wire().component("toast", Map.of("message", "保存成功", "type", "success"));
        if (isWireRequest()) {
            // 对话框内提交:派发「关闭对话框」事件,前端关闭 dialog 并刷新列表(均不整页刷新)
            WireEffects.dispatch("wire-dialog-close", null);
        } else {
            // 传统整页表单提交:整页跳回列表
            WireEffects.redirect(RouteHelper.route("admin.admin.index"));
        }
    }
}
```

关键点：

1. **对话框即交互式组件,不用 `@section('modals')`**:列表 `wire:click="edit(id)"` 触发 `edit()` → `WireEffects.push("admin-form", {...})`。框架经 `wireComponents()` 解析模板、经 `wireLayoutReplacements()` 把 `item` 模板的 `@extends` 换成 `form.dialog`（**对话框布局是独立片段，不 extends 整页布局**，详见下文），并注入 `wire:config`（含签名快照 + `data-wire-update`）使其成为「活的」组件——`wire:model` 双向绑定、`wire:submit` 提交全部生效，前端自动挂载并打开对话框。整个过程**无需任何 JS 辅助**。
2. **直访 = 整页表单,点击 = 对话框,互不干扰**:直访 `/change` → 主页面渲染 `item` 模板自身 `@extends('layouts.mdui.form')` 整页表单;列表点击 → 组件下发渲染时套用 `layouts.mdui.form.dialog` 对话框片段。布局替换**仅作用于组件下发渲染**,主页面渲染始终用模板自身 `@extends`——这就是「点击修改弹对话框 + URL 变深链,直接进 URL 才是修改单独页」的实现原理。
3. **唯一 id 用 `$wireId`**:组件 / DOM 的唯一标记必须用 `$wireId`（`WireController` 基于 `System.nanoTime()` 为每个组件实例生成,如 `wc-admin-form-364525211712000`）,**禁止**用 `csrf_token()`——`csrf_token()` 在同一次请求内恒定,多个组件会出现 id 冲突、事件串台。toast、dialog 等模板内凡用到 `id` 的地方统一改用 `{{ $wireId }}`。
4. **重定向用 `WireEffects.redirect()`**:`save()` 内**显式**调用,仅保存成功后整页跳回列表。若改用 `getRedirectUrl()` 全局默认,`add()/edit()` 也会整页跳转,刚下发的对话框立刻被冲掉。两者不可混用。
5. **`@WireLocked` 防快照膨胀**:列表这类大对象用 `@WireLocked` 标记,既不进快照(避免把整张表序列化到客户端),又在每次 wire 更新后由 `refresh()` 重新从 DB 查询。
6. **单例字段重置**:`AdminController` 是 Spring 单例,`mount()` 必须重置所有表单字段,否则上一次 `edit(13)` 的残留会泄漏进下次直访 `/change` 的快照,使 `save()` 误判为「更新」并对已删除记录 NPE。
7. **列表复用 `@component` slot**:列表页用 `@component('layouts.mdui.slot.search')`（带 `@slot('action')/@slot('select')`）与 `@component('layouts.mdui.slot.list')`（带 `@slot('header')/@slot('items')`）复用**只读**模板片段,搜索/表格 DOM 写在 slot 里、列表模板不重复;slot 模板（layouts/mdui 下）只调样式/js,列表里只允许增删 `wire:` 标签。

### 对话框布局必须是「独立片段」

`layouts.mdui.form.dialog` **不得 `@extends` 整页布局**（如 `layouts.mdui.main` / `layouts.mdui.appbar_drawer`）。整页布局会连带 `<!DOCTYPE html>/<head>/<body>` 一起渲染,而组件 HTML 是被追加到当前页面的 `#wire-components-container` 里的——整页结构被塞进页面会造成「看起来整页跳转到了修改页」的假象（历史 bug 根因）。正确写法是**只输出对话框 DOM + 打开脚本**（可参考 `components/toast.jblade` 的独立片段写法）,`@yield` 用于拉取 item 模板注册的 `title`/`action`/`form` 区块:

```blade
{{-- layouts/mdui/form.dialog.jblade:独立片段,不 extends 任何布局 --}}
<div id="wire-dialog-{{$wireId}}" class="mdui-dialog" ...>
    <div class="mdui-dialog-title">@yield('title')</div>
    <div class="mdui-dialog-content">
        <form action="@yield('action')" method="post">@yield('form')</form>
    </div>
    <div class="mdui-dialog-actions">
        <button wire:submit="save">提交</button>
    </div>
</div>
<script>/* mdui.Dialog 打开脚本 + wire-dialog-close 监听 */</script>
```

---

## 布局替换：setWireLayoutReplace 就是 @extends 的替换

**`setWireLayoutReplace(template, layout)` / `wireLayoutReplacements()` 的语义 = 指定「@extends 的替换」**，不是新造一种布局机制，更不是 `getLayout()` 那种「整页父布局」概念（已删除）：

- 命中：渲染该模板时，用注册的 `layout` **替换**模板字面量里的 `@extends(...)`；
- 未命中：**使用原替换**——即模板自身的 `@extends(...)` 原样生效。

示例：`item.jblade` 字面量是 `@extends('layouts.mdui.form')`（整页表单）。控制器声明 `wireLayoutReplacements()` 后：

| 场景 | 渲染路径 | 生效的父布局 |
|------|---------|-------------|
| 直访 `/change` | 主页面渲染（index） | `layouts.mdui.form`（模板自身 @extends，替换规则不作用于主页面） |
| 列表点击「修改」 | 组件下发渲染（renderComponents） | `layouts.mdui.form.dialog`（替换规则命中） |

替换通过 `WireParentOverride` 实现：jblade 的 `BladeEngine.overrideParentIfNeeded()` 在继承链每一步读取该覆盖（反射集成，jblade 不依赖 wire 模块），命中则替换父模板名。仅组件下发渲染期注册，渲染完即清除，线程安全。

---

## 组件下发与模板解析规则

`WireEffects.push(name, params)` 下发组件时，模板名按以下顺序解析（`resolveComponentTemplate`）：

1. **本控制器强关联组件注册表** `wireComponents()`（如 `admin-form` → `mdui.admin.admin.item`）——与控制器绑定的对话框/表单组件必须在此声明；
2. **全局命名组件注册表** `WireManager.resolveComponentTemplate(name)`（来自 `jaravel.wire.components` 配置，如 `toast` → `components.toast`）；
3. **兜底**：组件名即模板名。

**禁止**把与某个控制器强关联的组件写进 `jaravel.wire.components` 配置文件——它的模板、布局替换、action 全部由该控制器承接，散落到全局配置会造成职责混乱、跨项目冲突。

---

## 三种请求处理

| 请求类型 | 触发条件 | 处理流程 |
|----------|---------|---------|
| 直访 GET | 无 wire_body | `fill(request.all())` 自动赋值 → `mount(request)` → `collectPublicFields` → `render()` → 整页渲染（模板自身 @extends）→ 注入 wire assets |
| Wire POST | 含 `wire_body` | `WireRequest.from` → 解码签名快照(HMAC 校验) → 合并 params(排除 @WireLocked) → `fill(data)` → `invokeAction(action, params)` → `refresh(params)` → 重新收集属性 → 渲染 sections(按 `getTemplateName()`) → 渲染临时组件 → 编码新快照 → JSON |
| 传统表单 POST | 无 wire_body 的 POST | `fill(request.all())` → `invokeAction(getDefaultAction()="save")` → 重定向（action 显式 `WireEffects.redirect` 优先，否则 `getRedirectUrl`） |

### 内置 magic action

| action | 触发 | 说明 |
|--------|------|------|
| `$sync` | `wire:model` 双向绑定同步 | 仅把字段值合并进快照并重新签名返回,不重渲染任何 section——否则整段 innerHTML 替换会把对话框/模态等局部组件的 DOM 状态(如 mdui Dialog 的打开状态、光标焦点)冲掉。前端拿到新快照后仅更新本地快照,不替换任何 DOM |
| `$refresh` | `Wire.refresh()` / `wire:click="$refresh"` | 重新执行 `refresh(params)` 并刷新组件 |
| `$paginate` | `wire:pagination` 容器内的 `a[href*="?page=N"]` 点击 | 分页**无感切换**：仅调用 `refresh(params)` 重载分页数据（不调用任何 action 方法），并自动 `WireEffects.pushUrl` 把地址栏同步为 `?page=N`（翻回第 1 页时还原为无参 URL），前端只精准刷新目标 section，暗色模式/对话框状态全部保留 |

### 参数化 action（前端解析 + 后端使用）

`wire:click` / `wire:submit` 支持 `method(arg1, arg2, …)` 语法。框架采用**前后端职责分离**设计：

- **前端负责解析**：`wire.js` 的 `parseWireAction(expr)` 函数负责把 `wire:click` 表达式切分为「方法名」和「位置参数」，并负责单引号字符串字面量剥除、逗号分隔（支持引号内逗号不切分，如 `role('a,b',2)` → 两个参数）。
- **后端直接使用**：`WireController.invokeAction` 直接使用 action 作为方法名，参数从 `params` 按位置下标读取（`params.get("0")`, `params.get("1")`…），仅做类型转换，不做任何 action 字符串解析。

```blade
{{-- 模板 --}}
<a wire:click="edit({{ $item->id }})">修改</a>   {{-- 渲染后：wire:click="edit(1)" --}}
<a wire:click="role({{ $item->id }}, 'admin')">角色修改</a>  {{-- 渲染后：wire:click="role(1, 'admin')" --}}
```

```javascript
// wire.js 中的 parseWireAction 处理：
// edit(1)           → action="edit",   params={"0":"1"}
// role(1, 'admin')  → action="role",   params={"0":"1", "1":"'admin'"}
// role('a,b', 2)    → action="role",   params={"0":"a,b", "1":"2"}  {{-- 引号内逗号不切分 --}}
```

```java
// 后端方法直接对应：
public void edit(Long id) { ... }                          // edit(1) → edit(1L)
public void role(Long id, String name) { ... }            // role(1, 'admin') → role(1L, "admin")
public void role(Long id, String filter) { ... }          // role('a,b', 2) → role("a,b", 2)
```

参数解析规则（前端 `parseWireAction`）：
- 取首个 `(` 与配对 `)` 之间为参数区，按逗号切分（**支持多参**）；
- 单引号标记字符串字面量：引号内逗号不切分（`role('a,b',2)` → 两个参数 `"a,b"` 和 `"2"`）；
- 剥去字符串参数外层的单引号（`'admin'` → `admin`）；
- 位置参数放入 `params` 的字符串下标：`params={"0":"...", "1":"..."}`。

后端处理规则（`WireController.invokeAction`）：
- 直接用 `action` 作为方法名字符串匹配（`findPublicMethod`）；
- 从 `params.get(String.valueOf(i))` 按位置下标读取参数；
- 经 `convertValue(val.toString(), paramTypes[i])` 按声明类型转换（`"1"`→`Long`，`"true"`→`Boolean` 等）；
- 无括号 action（如 `save`）原样按方法名匹配，`params` 为 null。

---

## 分页无感切换（Pagination）

Wire 框架内置「分页器无感切换」能力：点击分页链接**不整页跳转**，只精准刷新数据 section，且地址栏同步为 `?page=N`（翻回第 1 页时还原）。底层依赖核心模块 `core` 的 `Paginator`（来自 [gaarason/database-all](https://github.com/gaarason/database-all)，实现 `Iterable` + `Htmlable`，`items()` 供模板 foreach、`links()` 渲染分页模板）。

### 后端：控制器返回 Paginator 并 override refresh

```java
public class AdminController extends WireController {
    @WireLocked public Paginator<Admin> paginator;   // 分页结果,标记 @WireLocked 不进快照
    // 参与 URL 查询串:仅列表页生效;默认值 1(第 1 页时 URL 不带 ?page=1,翻页后才带 ?page=N)
    @WireQuery(templates = {"mdui.admin.admin.list"}, defaultValue = "1")
    public Long page;
    // 搜索条件:URL 参数名用 name() 指定为 key/value(与搜索表单/mount 读取一致),非空才加入 URL
    @WireQuery(name = "key", templates = {"mdui.admin.admin.list"})
    public String searchKey;
    @WireQuery(name = "value", templates = {"mdui.admin.admin.list"})
    public String searchValue;

    private Paginator<Admin> queryPaginated() {
        QueryBuilder<Admin, Long> q = Admin.self().newQuery();
        if (searchKey 非空) q.whereLike(searchKey, searchValue);
        q.orderBy("id", gaarason.database.appointment.OrderBy.ASC); // 必须 ORDER BY,否则 SQLite 行序不稳定→分页错位
        int currentPage = (page != null && page > 0) ? page.intValue() : 1;
        Paginator<Admin> p = Admin.self().paginate(q, currentPage, 10);
        p.setPath("/admin/admin");                    // 链接基准路径(与列表路由一致)
        return p;
    }

    @Override
    protected void refresh(Map<String, Object> params) {
        if (params != null && params.containsKey("pageNum")) {
            try { this.page = Long.valueOf(params.get("pageNum").toString()); } catch (Exception ignored) {}
        }
        this.paginator = queryPaginated();
    }
}
```

- `$paginate` 是框架内置 magic action（`WireController.invokeAction` 中处理）：仅调用 `refresh(params)` 重载数据（**不调用任何 action 方法**），随后自动 `WireEffects.pushUrl(...)` 基于 `@WireQuery` 注解字段生成带参 URL——`page=2` 时带 `?page=2`、`page=1`（等于 defaultValue）时还原无参 URL；`searchKey/searchValue` 非空时一并保留为 `key`/`value`。`pageNum` 取自前端分页拦截器。
- `Paginator.links()` 需要注册 `ViewProvider`（`ViewFacade.bind()`）才能渲染分页模板；分页模板自身是普通 `.jblade`（如 `layouts.mdui.pageinator`），通过 `{{ $paginator->links() }}` 输出、放在 `[wire:pagination]` 容器内。

### 前端：wire:pagination 绑定

模板在分页区加 `wire:pagination`（可选 `wire:target` 指定刷新目标 section，如 `content`）：

```blade
@foreach($paginator->items() as $item)
    {{-- 列表行 --}}
@endforeach

<div wire:pagination wire:target="content">
    {!! $paginator->links() !!}
</div>
```

`wire.js` 的 `bindPagination` 会为 `[wire:pagination]` 容器内的 `a[href*="?page=N"]` 绑定点击拦截：阻止浏览器整页跳转，改为发 `$paginate` 请求（携带 `pageNum`/`perPage`），后端只精准刷新目标 section。分页链接格式需为 `?page=N`（可选 `&perPage=M`）。

### 完整流程

```
列表页点击 ?page=2 链接
   └─ wire.js bindPagination 拦截 → sendRequest(comp, '$paginate', {pageNum:2}, el, ['content'])
        └─ POST /admin/admin/change (wire_body, action=$paginate, params={pageNum:2})
             └─ WireController.invokeAction → refresh({pageNum:2}) 重载 paginator
                  └─ WireEffects.pushUrl( buildQueryUrl("/admin/admin") )  ← 基于 @WireQuery 生成
                     (page=2 → ?page=2;searchKey/searchValue 非空 → &key=..&value=..)
        ← 响应 sections={content: 新列表 HTML} + effects.url="/admin/admin?page=2"
   └─ 前端 replaceSection 精准替换 content(暗色模式/对话框状态均保留,无整页刷新)
```

「翻页 → 点修改 → 取消」的 URL 一致性：点修改时后端 `inferBackUrl()` 基于同一套 `@WireQuery` 字段生成 backUrl（如 `/admin/admin?page=2`），前端取消时优先还原 `window.__wirePrevUrl`（每次 pushUrl 前暂存的上一条 URL），两者一致——地址栏与内容始终同步，不再出现「内容在第 2 页、URL 却是无参列表页」的错位。

### 数据量注意

分页模板用 `hasPages()` 决定是否渲染分页控件。若活跃记录数 ≤ 每页大小（如 10 条），`hasPages()` 为 false，`links()` 返回空串、分页器「消失」——这是数据量问题，不是 bug。

---

## URL 查询参数：@WireQuery 注解

`@WireQuery` 标记 `WireController` 中**参与 URL 查询串**的 public 字段，使框架在生成 pushUrl / backUrl 时能**声明式**地自动带上这些参数，无需手写拼接。解决「翻页后 URL 带 `?page=2`，点修改再取消却还原成无参 URL、内容与地址栏错位」的问题。

### 注解属性

| 属性 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `name()` | `String` | 字段名 | 该参数在 URL 中使用的名字;字段名与 URL 参数名不一致时使用(如字段 `searchKey` → URL 参数 `key`) |
| `templates()` | `String[]` | `{}`(所有模板) | 该参数生效的模板名列表;生成 URL 时以 `getTemplateName()` 为上下文匹配,列表非空且当前模板不在列表内 → 不加入 |
| `defaultValue()` | `String` | `""`(未设置) | 默认值;当前值等于该值或为 `null` 时不加入 URL。默认空串表示「未设置默认值」→ 仅 null(及空串)时不加入 |

### 过滤规则（WireController.buildQueryUrl）

对每个被 `@WireQuery` 标记的 public 字段,按序过滤:
1. `templates()` 非空且 `getTemplateName()` 不在列表内 → 跳过;
2. 当前值为 `null` → 跳过;
3. 当前值等于 `defaultValue()`(defaultValue 非空) → 跳过;
4. 当前值为空串 → 跳过(与 null 等价处理);
5. 其余按 `name()`(缺省用字段名)加入: `?name=value&...`。

### 生成时机

| 场景 | 使用的方法 | 效果 |
|------|-----------|------|
| `$paginate` 分页 | `WireEffects.pushUrl(buildQueryUrl(inferBasePath(req), getTemplateName()))` | 翻页地址栏同步 `?page=N`,翻回第 1 页(page=defaultValue)还原无参 |
| 对话框返回 | `inferBackUrl(request)` = `buildQueryUrl(inferBasePath(req), getTemplateName())` | backUrl 带上 `?page=N` 等参数,「翻页 → 修改 → 取消」还原带参 URL |

### 前端：保留上一条 URL

`wire.js` 每次收到 `effects.url` 执行 `history.pushState` 前,先把当前地址存入 `window.__wirePrevUrl`(只保留一条,通常用户只会返回一次)。对话框取消时 `restoreBackUrl` **优先还原 `__wirePrevUrl`**,兜底用后端 `effects.backUrl` 生成的 `__wireBackUrl`——两套机制一致,互为保险。

---

## 交互式组件下发

`WireEffects.push(name, params)` 把解析出的模板渲染成一个**活的 wire 组件**下发到前端，区别于「命名组件」（无状态临时事务）：

- **渲染期注入 `wire:config`**：`WireController.renderComponents` 为每个组件生成唯一 `id`（基于 `System.nanoTime()`）与**签名快照**，并包成 `<script wire:config data-wire-update="..." wire:snapshot="...">` 注入组件根元素内部末尾。前端 `mountComponents` 识别后调用 `initComponent` 将其初始化为独立 wire 组件，于是组件内的 `wire:model` 双向绑定、`wire:submit` 提交全部生效，且各组件作用域隔离、互不干扰。
- **声明式布局替换**：在 `wireLayoutReplacements()` 中声明「模板名 → 替换布局名」，渲染该组件时通过 `WireParentOverride` 把子模板原本 `@extends` 的父布局换成指定布局。请求级 `setWireLayoutReplace` 仅用于动态追加，一般场景无需使用。
- **更新 URL 取自 `getUpdateRouteName()`**：组件 `wire:config` 的 `data-wire-update` 指向控制器 `getUpdateRouteName()`（如 `admin.admin.change`），因此对话框表单 `wire:submit="save"` 会提交到正确端点。
- **`$wireId` 作唯一标记**：模板内用 `{{ $wireId }}` 引用组件唯一 id（dialog 的 `id`、toast 的 `id` 等），由框架注入。**禁止使用 `csrf_token()` 作 id**——`csrf_token()` 在同一次请求内恒定，多个组件会出现 id 冲突、事件串台。

与 `wire().component(name, params)`（命名组件，带 `wire:lifecycle` 脚本、不参与快照/局部刷新）的区别：`WireEffects.push` 下发的是**有状态、可交互**的 wire 组件；`wire().component` 下发的是**无状态临时事务**组件（toast / confirm）。

典型流程（列表页点「修改」打开编辑对话框）：

```
列表页 wire:click="edit(13)"
   └─ POST /admin/admin/change (wire_body, action=edit, params=[13])
        └─ AdminController.edit(13)
             └─ WireEffects.pushUrl("/admin/admin/change?id=13") + WireEffects.push("admin-form", {id:13, ...})
                  └─ renderComponents → wireComponents() 解析模板为 mdui.admin.admin.item,
                     wireLayoutReplacements() 套上 layouts.mdui.form.dialog(独立片段),
                     注入 wire:config(wire:model / wire:submit 生效)
        ← 响应 effects.components = [ {html, id:"wc-admin-form-...", ...} ]
   └─ 前端 mountComponents 注入 DOM 并 initComponent → dialog.open() 自动弹出(URL 已 pushState 为深链)
对话框内 wire:submit="save" → POST /admin/admin/change (action=save)
   └─ AdminController.save() → 落库 → isWireRequest()? dispatch("wire-dialog-close") 关闭对话框并刷新列表 : redirect(列表) 整页跳回
```

---

## 命名组件（toast / confirm 等临时事务）

针对「与主数据无关、生命周期短、可能多实例并存」的临时 UI，wire 模块提供一套独立于 section 体系的**命名组件**机制（由 `WireOutlet` 中间件 + `wire-component.js` 前端运行时承接，详见 wire 模块的 `WireOutlet`/`WireComponents` 相关源码）：

- **后端一行下发**：`wire().component("name", params)`（WireController 内）。
- **模板注册**：全局命名组件在 `application.yml` 的 `jaravel.wire.components` 注册（组件名 → 模板名），如 `toast: components.toast`、`confirm: components.confirm`。与控制器绑定的业务组件（对话框等）**不在此注册**，用 `wireComponents()` 声明。
- **四条下发路径统一**：首屏 HTML bootstrap、Wire 更新 `effects.components`、组件内嵌套下发——共用同一队列与渲染器。
- **生命周期**：`onCreate → onStart → onStop → onDestroy`，`wire.stop()` 主动结束实例，多实例 id 隔离（`wc-{name}-{seq}`），脚本按实例独立闭包求值。

### 纯脚本命名组件（如 snackbar，零显式 div）

命名组件模板可以是**单个 `<script wire:lifecycle>`，不含任何根 HTML 元素**——连 `display:none` 的占位 div 都不需要。后端 `renderComponents` 识别这种「纯生命周期脚本组件」：把脚本内容抽到 `payload.script`、`html` 置空，并**跳过 `wire:config` 注入与 outlet/div 注入**（否则 `injectConfigIntoRoot` 会把 JSON 塞进 `<script>` 根内部，前端 `new Function` 报 `SyntaxError: Unexpected token '<'`）。

前端 `WireComponent.mount` 对纯脚本组件：不向 outlet 注入任何 div、也不要求 outlet，仅 `parseLifecycle(payload.script)` 取出 `onCreate/onStart/onStop/onDestroy` 并触发 `onStart`（组件自身用 mdui 等自建 DOM）。典型实现（snackbar）：

```blade
{{-- layouts/mdui/component/snackbar.jblade：整段即一个 <script wire:lifecycle> --}}
<script wire:lifecycle>
    function onStart(el, wire) {
        var message = (wire.params && wire.params.message) || '';
        var type = (wire.params && wire.params.type) || 'info';
        mdui.snackbar(message, {
            timeout: type === 'error' ? 5000 : 3000,
            position: type === 'error' ? 'top' : 'bottom',
            onClosed: function () { wire.stop(); }   // 关闭后自我移除，无 DOM 残留
        });
    }
</script>
```

下发（组件名直接写模板全路径，**无需在 `application.yml` 注册**）：

```java
wire().component("layouts.mdui.component.snackbar",
        Map.of("message", "保存成功", "type", "success"));
```

> 配合「参数化 action」，`wire:click="role(1)"` 触发的 `role(Long id)` 也可下发此类纯脚本组件完成轻提示。

#### 生命周期脚本执行机制（严格模式注意）

纯脚本组件的 `comp.script` 由 `wire.js` 的 `mountComponents` 执行，**不是** `parseLifecycle` 单独取出四钩子。执行方式：

```javascript
// wire.js mountComponents 内
if (comp.script) {
    // 注意:wire.js 头部有 'use strict'。严格模式下直接 eval 的 onStart 不会泄漏到外层作用域,
    // typeof onStart === 'function' 恒为 false,导致 onStart 永不调用(snackbar 等无反应的历史 bug)。
    // 正确做法:把脚本包装成「定义 onStart + return onStart」的函数体,用 new Function 取回函数引用。
    var lifecycleFactory = new Function(comp.script + '\n; return onStart;');
    var onStartFn = lifecycleFactory();
    onStartFn(el, { stop: function() { el.remove(); } });
}
```

- `new Function` 创建的函数体默认**非严格模式**，函数声明（如 `function onStart(){}`）在函数作用域内可见，`return onStart` 可取到引用；
- **禁止**用裸 `eval(comp.script)`（严格模式作用域隔离导致外层拿不到 `onStart`），也**禁止**用 `new Function(comp.script)` 后不取引用直接调用（那只定义了函数却没有执行 `onStart`）；
- `el` 是组件在 `#wire-components-container` 内的挂载点（纯脚本组件通常无显式 div，`el` 可能为空元素或 container 本身），`wire.stop()` 用于自我移除实例。

---

## 前端事件系统

`wire.js` 暴露全局对象 `Wire`，提供前端事件系统。通过 `Wire.on` / `Wire.off` 注册和移除事件监听器，可在 Wire 生命周期钩子中执行自定义逻辑——典型场景是 DOM 更新后刷新第三方 UI 框架组件（如 mdui 的 `mdui.mutation()`）。事件监听器全局生效，对所有 Wire 组件实例触发。

### API

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `Wire.on` | `String event, Function callback` | 无 | 注册事件监听器，支持多次调用注册多个监听器 |
| `Wire.off` | `String event, Function callback` | 无 | 移除指定事件监听器。不传 `callback` 时移除该事件的所有监听器 |

### 支持的事件

| 事件 | 参数 | 触发时机 |
| --- | --- | --- |
| `beforeRequest` | `(component, action, params)` | **发起 HTTP 请求前**触发 |
| `afterRequest` | `(component, data)` | **收到 HTTP 响应后、数据处理前**触发 |
| `beforeUpdate` | `(component, action, params)` | 数据处理前触发（兼容旧版，与 `beforeRequest` 同时触发） |
| `afterUpdate` | `(component, data, sections)` | DOM 更新完成后触发 |

完整的请求生命周期顺序：

```
beforeRequest ──→ 发送 HTTP 请求 ──→ afterRequest ──→ 数据处理 ──→ beforeUpdate ──→ DOM 更新 ──→ afterUpdate
    │                (fetch)              (收到 JSON)     (handleResponse)          (replaceSection)       (rebindSection)
```

### 使用示例

```javascript
// 发起请求前：展示 loading 状态
Wire.on('beforeRequest', function(component, action, params) {
    showLoading(component);
});

// DOM 更新后：刷新第三方 UI 框架组件
Wire.on('afterUpdate', function(component, data, sections) {
    mdui.mutation();  // 重新扫描并初始化 mdui 组件
});

// 移除事件监听器
Wire.off('afterUpdate', afterUpdateHandler);
```

---

## Section 排除列表

`WireManager` 提供排除列表功能，可以让某些 section/slot 不被 `<!--wire:section-start/end:name-->` 标记包裹，从而不被前端 `wire.js` 识别为可更新区域。适用于 header、footer 等不需要局部更新的全局区域（如列表页把 `modals` 区排除，避免对话框模板被列表刷新重建）。

### API

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `addExcludedSections` (static) | `String... sectionNames` | `void` | 添加排除的 section 名（可变参数） |
| `removeExcludedSection` (static) | `String sectionName` | `void` | 移除单个排除的 section 名 |
| `getExcludedSections` (static) | 无 | `Set<String>` | 获取当前排除的 section 名列表 |
| `clearExcludedSections` (static) | 无 | `void` | 清空排除列表 |
| `isExcluded` (static) | `String sectionName` | `boolean` | 检查指定 section 名是否在排除列表中 |

### SpringBoot 配置

```yaml
jaravel:
  wire:
    excluded-sections:
      - header
      - footer
```

### 编程式控制

```java
WireManager.addExcludedSections("header", "footer");
WireManager.isExcluded("header");   // true
WireManager.removeExcludedSection("header");
WireManager.clearExcludedSections();
```

---

## IDEA 模板语法提示（XSD 命名空间校验）

在 IntelliJ IDEA 中直接编写 `.blade.java` / `.jblade` 模板时，`wire:*` 属性会报「命名空间不存在」警告。
本仓库已提供 XSD 校验文件，下载后即可消除警告并获得属性补全。

### 下载 XSD 文件

本仓库 `wire/xsd/wire.xsd` 包含全部 15 个 `wire:*` 属性的类型声明。
可直接从仓库克隆或下载到本地：

```
jaravel-vendor/wire/xsd/wire.xsd
```

### IDEA 配置步骤

1. 打开 `Settings` → `Languages & Frameworks` → `HTML` → `Schemas and DTDs`
2. 点击 `+` 添加按钮，选择本地 `wire.xsd` 文件
3. Namespace URI 填写：`https://jaravel.dev/ns/wire`
4. 在模板文件根元素添加命名空间声明：

```blade
<html xmlns:wire="https://jaravel.dev/ns/wire">
```

5. 保存后 IDEA 会自动匹配并启用校验，`wire:click`、`wire:model` 等属性将显示补全提示，不再报红线警告。

### 支持的 wire:* 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `wire:click` | string | 点击事件处理器 |
| `wire:submit` | string | 表单提交处理器 |
| `wire:model` | string | 双向数据绑定 |
| `wire:model.live` | string | 实时双向绑定 |
| `wire:model.defer` | string | 延迟双向绑定 |
| `wire:section` | string | 精准刷新区域标记 |
| `wire:config` | boolean | 组件配置标记（仅 `<script>`） |
| `wire:snapshot` | string | 状态快照（框架自动注入） |
| `wire:lifecycle` | boolean | 生命周期脚本标记（仅 `<script>`） |
| `wire:outlet` | boolean | 命名组件注入容器 |
| `wire:navigate` | boolean | 透明导航链接标记 |
| `wire:update` | string | 自定义 update 请求地址 |
| `wire:keydown` | string | 键盘按下事件处理器 |
| `wire:change` | string | 输入变化事件处理器 |
| `wire:param-id` | string | 操作参数 ID |

配合使用的 `data-wire-*` 属性（wire.js 运行时读取，无需声明）：
`data-wire-update`、`data-wire-key`、`data-wire-owned`、`data-wire-outlet`、`data-wire-back-url`

---

## 认证过期无感重定向

Wire 实现了认证过期的「无感」重定向体验：当用户在 Wire 交互过程中 session 过期，前端会自动跳转到登录页，登录成功后回到之前的页面。

```
用户操作触发 Wire 请求
        │
        ▼
中间件检测到 session 过期
        │
        ├── 返回 401 + JSON {message, redirect: "/login"}
        │   或返回 302 重定向（非 API 路径）
        ▼
wire.js fetch 拦截
        ├── response.status === 401
        │   → 读取 errData.redirect（默认 /login）→ redirectToLogin(loginUrl)
        ├── response.type === 'opaqueredirect'（manual 模式下的 302）
        │   → redirectToLogin('/login')
        ▼
redirectToLogin(loginUrl)
        ├── 携带当前页面 URL 作为 redirect 参数（登录成功后回跳）
        ├── 避免重复重定向（若已在登录页则不跳）
```

关键设计点：

1. **`fetch` 使用 `redirect: 'manual'`**：不自动跟随重定向，由 wire.js 手动处理，避免 302 被浏览器吞掉。
2. **401 优先读 `redirect` 字段**：中间件返回的 401 JSON 中可携带 `redirect` 字段指定登录页 URL。
3. **携带回跳地址**：`redirectToLogin` 会将当前页面 URL 编码后作为 `redirect` 参数附加到登录页 URL，登录成功后可回跳。
4. **防重复**：若当前已在登录页则不再跳转，避免死循环。

---

## 手动控制 wire.js 注入

默认情况下，Wire 渲染页面时会自动在 `</body>` 前注入 `<script src="/static/wire.js">` 标签。支持手动控制 wire.js 的注入行为，适用于 CDN 引入、内联引入、自定义路由等场景。

### SpringBoot 配置方式（推荐）

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

配置后，所有 Wire 页面渲染都会遵循该设置，无需修改控制器代码。

### 编程式控制

```java
// 全局关闭自动注入
WireManager.setAutoInjectJs(false);

// 自定义 JS 引用路径（当 autoInjectJs=true 时使用）
WireManager.setJsPath("/assets/wire.js");

// 获取当前配置
boolean autoInject = WireManager.isAutoInjectJs();
String jsPath = WireManager.getJsPath();
```

---

## 使用注意

`WireController` 是 Spring 单例，实例字段在多请求间共享——实现 `mount()` 时必须重置所有表单字段，避免上一个请求的数据串入下一个请求。

## 安全机制

- **Snapshot HMAC 签名**：快照经 HmacSHA256 + session key 签名（`signature:base64` 形式），篡改会抛 `TamperedSnapshotException` → 前端提示刷新页面。
- **@WireLocked 注解**：标记的字段不进快照、不接受 `wire:model` 参数合并（防篡改、防大对象序列化）。
- **参数全 String + 结构化解析**：前端 `parseWireAction` 按字符串切分（无正则、无 eval）把 `wire:click` 表达式解析为方法名和位置参数；后端 `WireController.invokeAction` 直接使用 action 作为方法名，参数从 `params` 按位置下标读取，经 `convertValue` 按声明类型转换（非反射执行任意代码）；单引号参数标记为字符串字面量并剥引号。
- **WireParentOverride 运行时 @extends 覆盖**：仅组件下发渲染期生效，主页面渲染不受影响。
