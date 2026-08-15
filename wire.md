# Wire 模块重构设计方案

---

## 一、项目背景与目录结构

### 1.1 项目根目录 `d:\0code\ai\work\` 结构

```
d:\0code\ai\work\
├── PROJECT_SUMMARY.md          ← 项目总览
├── wire.md                     ← 本文档：Wire 重构完整方案
├── error/                      ← 正在开发的参考项目
├── jaravel/                    ← 主框架项目
├── jaravel-blade-extension/    ← jblade IDEA 插件
├── jaravel-vendor/             ← jaravel 依赖模块集合
│   ├── wire/                   ← Wire 模块（本次重构目标）
│   ├── jblade/                 ← Blade 模板引擎（需要配合改造）
│   └── http/                   ← HTTP 模块
└── views/                      ← 其他视图模板
```

### 1.2 error 项目结构（参考实现）

error 项目位于 `d:\0code\ai\work\error\`，是当前最常用的一套 AdminController + 表单设计范式：

```
error/src/main/java/com/weacsoft/system/
├── SystemApplication.java
├── app/
│   ├── http/
│   │   ├── controllers/
│   │   │   ├── AdminController.java    ← 核心参考（index/changeIndex/change/delete）
│   │   │   ├── LoginController.java
│   │   │   ├── RoleController.java     ← 结构与 AdminController 完全一致
│   │   │   └── TestController.java
│   │   └── middleware/
│   │       ├── Authorization.java
│   │       ├── VerifyCsrfToken.java
│   │       ├── WireMiddleware.java     ← 继承框架 WireMiddleware
│   │       └── ...
│   ├── models/admin/
│   │   └── Admin.java                  ← @Repository + @Table 模型
│   ├── provider/
│   │   ├── BladeEngineProvider.java
│   │   └── RouteServiceProvider.java
│   └── service/
│       └── AdminRolePermissionService.java
├── config/
│   ├── AppConfig.java
│   ├── WireConfig.java                 ← Wire 诊断配置快照
│   └── ...
├── routes/
│   └── Web.java                        ← 路由注册（手动注册）
└── src/main/resources/
    ├── application.yml
    ├── static/
    │   ├── wire.js                     ← Wire 前端运行时
    │   ├── wire-navigate.js
    │   ├── wire-component.js
    │   └── asset/
    └── views/
        ├── layouts/mdui/
        │   ├── main.jblade             ← 根布局
        │   ├── form.jblade             ← 表单布局（所有 item 复用）
        │   ├── appbar.jblade
        │   ├── appbar_drawer.jblade
        │   ├── pageinator.jblade
        │   └── slot/
        │       ├── search.jblade       ← 搜索组件
        │       ├── list.jblade         ← 列表表格组件
        │       ├── datepicker.jblade
        │       └── ...
        └── mdui/admin/
            ├── main.jblade
            └── admin/
                ├── list.jblade         ← 管理员列表页
                └── item.jblade         ← 管理员详情/编辑页
```

### 1.3 当前依赖

- `io.github.lijialong1313:wire:0.1.2` — Wire 模块
- `io.github.lijialong1313:jblade:0.1.2` — Blade 模板引擎
- `io.github.lijialong1313:starter:0.1.2` — jaravel-vendor 框架核心
- `gaarason.database.query:7.0.15` — ORM
- Spring Boot 4.1.0

---

## 二、Wire 模块当前源码结构

### 2.1 Java 源码（18 个文件）

```
com/weacsoft/jaravel/vendor/wire/
├── WireService.java              (428 行) 流式上下文，核心入口
├── WireRequest.java              (121 行) 请求解析
├── WireResponse.java             (429 行) 响应构建器
├── WireManager.java              (523 行) 渲染/快照/snapshot 编解码
├── component/
│   ├── WireComponents.java       (207 行) 注册表 + ThreadLocal 队列
│   ├── WireComponentDefinition.java
│   ├── WireComponentPayload.java
│   ├── WireComponentRenderer.java
│   └── WireOutlet.java           (415 行) 加载位置中间件
├── navigation/
│   ├── WireMiddleware.java       (205 行) 全局中间件，透明导航
│   ├── WireRenderer.java         (187 行) section 提取 / diff
│   └── WireContext.java           (81 行) ThreadLocal 上下文
└── springboot/
    ├── WireProperties.java
    ├── WireAutoConfiguration.java
    ├── WireComponentAutoConfiguration.java
    ├── WirePublishableConfig.java
    ├── WirePublishAutoConfiguration.java
    └── WireStaticPublishable.java
```

### 2.2 当前两套体系

| 维度 | Component（命名组件） | Navigation（透明导航） |
|------|----------------------|----------------------|
| 核心类 | WireComponents, WireComponentRenderer, WireOutlet | WireMiddleware, WireRenderer, WireContext |
| 状态载体 | ThreadLocal 队列 | WireContext ThreadLocal |
| 协议 | effects.components 字段 | X-Wire-Navigate Header + diff JSON |
| 生命周期 | onCreate/onStart/onStop/onDestroy | 无生命周期，纯 DOM 局部替换 |
| 模板要求 | 普通 Blade 片段（不要 @extends） | 标准 @section/@yield 模板 |

两套体系互不依赖，但给用户造成"两套系统"的印象。重构后合并为统一的 WireController 组件体系。

### 2.3 当前 error 项目 Wire 能力使用情况

- `wire-navigate`（侧边栏链接的透明导航）已使用
- `wire.js` 和 `wire-navigate.js` 已在 main.jblade 底部加载
- **业务模板中没有任何 `wire:click`、`wire:submit`、`wire:model` 等属性**
- list → item 切换为纯服务端跳转（`<a href>` 整页刷新）
- search 搜索为纯表单 GET 提交
- form 提交为纯 POST + redirect

---

## 三、用户需求总述

### 3.1 核心诉求

1. **Component 与 Navigation 统一**：不再拆分为 WireComponent 注册等多套东西，统一为同一套 WireController 机制
2. **类似 Livewire 的设计**：有 `render()` 方法返回视图、有 `mount()` 初始化、有 `fill()` 批量赋值、支持 `@script`/`@assets`、支持 `->layout()`/`->extends()`/`->section()`
3. **双布局能力**：`item.jblade` 子模板不变，通过 wire 请求或直访自动切换父模板（整页表单 vs Dialog 表单）
4. **最小改动**：`item` 代码除 `return` 语句外不接受任何改动

### 3.2 用户已确认的关键决策（Q1-Q10）

| 问题 | 用户选择 | 说明 |
|------|---------|------|
| Q1: jblade 引擎改造是否接受 | 接受 | @script/@assets 自动输出，无需手动写输出指令 |
| Q2: toast/confirm 实现方式 | A 完全统一 | 各自是独立 WireController，通过 `wire().component("名", Map)` 触发 |
| Q3: Dialog 中表单提交 | B 传统模式 | 不改代码，拦截传统 submit 转为 wire 请求 |
| Q4: 旧 WireService 流式 API | 完全废弃 | 直接删除，重写 demo |
| Q5: 路由注册 | A 手动注册 | 方法名固定 index/update |
| Q6: fill 触发时机 | A 手动调用 | 在 mount() 中手动调用 |
| Q7: form wire 改造 | button 上 wire:submit | 等价于提交所属 form；找不到 form 则退化为 click |
| Q8: 临时组件使用方式 | `wire().component("名", Map)` | 动态注册，不限定方法名 |
| Q9: @script/@assets 输出 | 自动完成 | 不新增输出指令（可选保留） |
| Q10: 动态 @extends 覆盖 | 方式 A | Wire 模块内部维护映射，渲染时覆盖 |

---

## 四、整体架构设计

### 4.1 架构总览图

```
                     ┌─────────────────────────────────────┐
                     │         WireController (抽象)         │
                     │  继承 Controllers 接口                │
                     │  含 public 属性 + 任意方法 + render()  │
                     │  默认实现 index() / update()          │
                     │  可选实现 mount() / fill()           │
                     └──────────┬──────────────────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                  ▼
   ┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐
   │  AdminController  │ │ToastController│ │ConfirmController│
   │  (业务全页组件)   │ │ (临时组件A)  │ │ (临时组件B)     │
   └──────────────────┘ └──────────────┘ └──────────────────┘
              │                 │                  │
              ▼                 ▼                  ▼
   index()/update()   wire().component("toast",...)   wire().component("confirm",...)
   → 渲染模板         → render() 返回组件 HTML       → render() 返回组件 HTML
   → 套 layout        → 经 effects.components 下发    → 经 effects.components 下发
```

**核心原则**：一个 WireController 子类 = 一个组件。临时组件和业务组件使用完全相同的机制，区别仅在于下发方式不同：
- 业务组件：通过路由 `index()` 首次加载，通过 `update()` 处理后续交互
- 临时组件：通过 `wire().component("name", params)` 在任意 action 中触发，渲染后随 wire 响应下发到页面

### 4.2 请求-响应完整链路

```
用户浏览器
  │
  │ GET /admin/admin/change?id=1
  ▼
WireController.index(request)
  → mount({"id":"1"})                        ← 加载数据到 public 属性
  → collectProperties() → data              ← 收集所有 public 属性
  → render() → WireView                     ← 返回模板配置
  → 检测无 wire_body → 主页面用模板自身 @extends 渲染(item.jblade 字面量 @extends('layouts.mdui.form'))
  → renderPage(template, data)              ← 模板自身继承链渲染(整页)
  → injectWireAssets(html)                  ← 注入 wire.js + wire:config + snapshot
  → ResponseBuilder.html(html)
  │
  ▼ 页面加载，用户点击"保存"
  │
wire.js 拦截提交
  → 发送 POST (含 wire_body: snapshot + action + params)
  ▼
WireController.update(request)
  → parse wire_body                          ← 解析 snapshot/action/params
  → decodeSnapshot(snapshot) + HMAC 验证     ← 防篡改
  → data.putAll(params)                     ← 合并 wire:model 更新
  → invokeAction("save", params)            ← 反射调用 save()
  → collectProperties() → data              ← 重新收集属性
  → renderSections(template, sections, data) ← 渲染 sections
  → encodeSnapshot(data) + HMAC 签名        ← 编码新快照
  → drainWireEffects()                      ← 取走临时组件队列
  → ResponseBuilder.json({sections, snapshot, effects})
  │
  ▼ wire.js 收到 JSON
  → 替换 sections DOM
  → 挂载临时组件（toast/confirm 等）
  → 触发 afterUpdate 事件
```

---

## 五、核心类设计

### 5.1 WireController 抽象基类

```java
package com.weacsoft.jaravel.vendor.wire;

public abstract class WireController {

    // ========== 用户必须实现 ==========
    /** 返回模板视图配置（对应 Livewire render） */
    protected abstract WireView render();

    // ========== 用户可选实现 ==========
    /** 初始化，仅首次 index() 时调用 */
    protected void mount(Map<String, String> params) {}

    /** 批量赋值属性（参考 BaseModel.fill） */
    protected void fill(Map<String, Object> data) {}

    /** 声明式模板级布局替换注册表：返回「模板名 → 替换布局名」。
     *  如 Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog") 表示凡以组件形式下发渲染
     *  item 模板时用 dialog 布局替换其 @extends(「判定到模板是 A，就用 B 换掉」)。
     *  声明一次即可，勿在每个 action 里重复调用。仅作用于组件下发渲染;主页面(直访)渲染
     *  始终用模板自身 @extends。默认 null。 */
    protected Map<String, String> wireLayoutReplacements() { return null; }

    /** 声明式控制器强关联组件注册表：返回「组件名 → 模板名」。
     *  如 Map.of("admin-form", "mdui.admin.admin.item")。与控制器绑定的组件禁止写入配置文件。
     *  默认 null。 */
    protected Map<String, String> wireComponents() { return null; }

    /** 请求级临时布局替换(仅当前请求生效,ThreadLocal 请求末清除)。
     *  一般场景用声明式 wireLayoutReplacements() 即可,此方法仅用于个别 action 动态追加规则。 */
    protected void setWireLayoutReplace(String templateName, String layout) { ... }
    protected String getWireLayoutReplace(String templateName) { ... }

    // ========== 框架默认实现（对接两条路由） ==========
    /** GET → 加载组件页面 */
    public Response index(Request request) { ... }

    /** POST → 处理 wire 更新请求 */
    public Response update(Request request) { ... }

    // ========== 便利方法 ==========
    protected WireView wireView(String templateName) { ... }
    protected WireResponse wire() { ... }
}
```

**用户示例**：
```java
public class AdminController extends WireController {

    public Long id;
    public String number;
    public String name;
    public String password;

    @Override
    protected void mount(Map<String, String> params) {
        if (params.containsKey("id")) {
            Admin admin = Admin.self().find(params.get("id")).toObject();
            if (admin != null) {
                this.id = admin.getId();
                this.number = admin.getNumber();
                this.name = admin.getName();
            }
        }
    }

    @Override
    protected WireView render() {
        return wireView("mdui.admin.admin.item")
            .with("roles", AdminRole.query().get().toObjectList());
    }

    // 声明式布局替换:凡以组件形式下发渲染 item 模板 → 套用 dialog 布局(一次声明,处处生效)
    @Override
    protected Map<String, String> wireLayoutReplacements() {
        return Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog");
    }

    // 声明式组件注册表:admin-form 对话框 → item 模板(与控制器强关联,禁止写入配置文件)
    @Override
    protected Map<String, String> wireComponents() {
        return Map.of("admin-form", "mdui.admin.admin.item");
    }

    // 点击「修改」：下发 item 模板对话框(布局由 wireLayoutReplacements 替换),地址栏变深链(无整页刷新)
    public void edit(Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        // ... 回填字段到 data ...
        WireEffects.pushUrl("/admin/admin/change?id=" + id);
        WireEffects.push("admin-form", data);
    }

    public void save() {
        // 保存逻辑
        wire().component("toast", Map.of("message", "保存成功", "type", "success"));
        if (isWireRequest()) {
            WireEffects.dispatch("wire-dialog-close", null);   // 对话框内提交：关闭对话框
        } else {
            WireEffects.redirect(RouteHelper.route("admin.admin.index")); // 整页表单提交：跳回列表
        }
    }
}
```

### 5.2 WireView 链式 API

```java
public class WireView {
    private String templateName;
    private String layout;
    private String extendsTemplate;
    private String section;
    private Map<String, Object> withData = new LinkedHashMap<>();
    private String title;

    public WireView layout(String layout) { ... }
    public WireView extends(String template) { ... }
    public WireView section(String name) { ... }
    public WireView with(Map<String, Object> data) { ... }
    public WireView with(String key, Object value) { ... }
    public WireView title(String title) { ... }

    String getTemplateName();
    Map<String, Object> getMergedData(Map<String, Object> properties);
}
```

使用示例：
```java
protected WireView render() {
    return wireView("mdui.admin.admin.item")
        .extends("layouts.mdui.form")
        .section("body")
        .with("roles", AdminRole.query().get().toObjectList());
}
```

### 5.3 index() 完整流程

```
1. 创建空 data Map
2. 调用 mount(request.query())
3. 反射收集所有 public 字段（或 @WireProperty 标记的字段）→ data
4. 调用 fill(data)（用户可选）
5. 调用 render() → WireView
6. 合并 WireView.with 数据到 data
7. 主页面渲染:模板自身 @extends 继承链(不套外部布局;直访 /change 走 item 字面量 @extends('layouts.mdui.form'))
8. 组件下发渲染时,若命中 wireLayoutReplacements()/setWireLayoutReplace 规则,则用 layout 覆盖该模板的 @extends
9. 渲染页面：renderPage(template, data)
10. 编码 snapshot：WireManager.encodeSnapshot(data)
11. 构建 updateUrl
12. 注入 wire assets：injectWireAssets(html, updateUrl, snapshot)
13. 返回 ResponseBuilder.html(html)
```

### 5.4 update() 完整流程

```
1. 解析 wire_body → {snapshot, action, params, sections}
2. 反序列化 snapshot + HMAC 验证 → data
3. 合并 params 到 data（过滤 @WireLocked 字段）
4. 反射调用 action 方法：`invokeAction(this, action, params)`
   - "$refresh" → 直接调用 `refresh(params)` 重新加载数据
   - "$sync" → wire:model 同步，自动处理（不重渲染 sections）
   - 其他 → 反射匹配 public 方法名，执行完毕后调用 `refresh(params)`
5. **重新加载展示数据**：`refresh(params)` —— action 执行完毕后重新查库。**`params` 来自前端 POST 请求体 `wire_body` JSON 中的 `"params"` 字段**，代表本次请求的 action 参数（如 `wire:click="delete(1)"` → `params={"0":"1"}`），**不是快照状态**
6. 重新收集 public 属性 → data
7. 确定 sections（来自请求或模板注册的 @section 列表）
8. 编码新 snapshot（含 HMAC 签名）
9. 取走 WireEffects 临时组件队列
10. 返回 ResponseBuilder.json({sections, snapshot, effects})
```

---

## 六、@script / @assets 指令（jblade 改造）

### 6.1 指令语法

```blade
@assets
    <script src="https://cdn.example.com/pikaday.js"></script>
    <link rel="stylesheet" href="https://cdn.example.com/pikaday.css">
@endassets

@script
    <script>
        new Pikaday({ field: $wire.$el.querySelector('[data-picker]') });
    </script>
@endscript
```

### 6.2 实现机制

1. `BladeContext` 新增两个有序容器：
   - `Map<String, String> collectedAssets` — key=组件标识, value=资产 HTML
   - `Map<String, String> collectedScripts` — key=组件标识, value=脚本 HTML
2. `BladeCompiler` 将 `@assets...@endassets` 编译为 `ctx.collectAssets("key", sw.toString())`
3. 将 `@script...@endscript` 编译为 `ctx.collectScript("key", sw.toString())`
4. **自动输出**：`BladeEngine.render()` 末尾自动在 `<head>` 结束前注入所有 assets，在 `</body>` 前注入所有 scripts。**无需手动写输出指令**。
5. 可选保留 `@emitAssets()` / `@emitScripts()` 供精确控制使用
6. 合并规则：继承链中子模板优先，父模板后收集；`@assets` 同名不覆盖，`@script` 全部按序输出

---

## 七、动态父模板覆盖（WireParentOverride）

### 7.1 机制

Wire 模块内部维护 `ThreadLocal<Map<String, String>>`，WireController 在 `index()` 渲染前注册覆盖，渲染完成后清除。

```java
public final class WireParentOverride {
    private static final ThreadLocal<Map<String, String>> OVERRIDES =
        ThreadLocal.withInitial(HashMap::new);

    public static void register(String templateName, String newParent) {
        OVERRIDES.get().put(templateName, newParent);
    }
    public static String get(String templateName) {
        return OVERRIDES.get().get(templateName);
    }
    public static void clear() { OVERRIDES.get().clear(); }
}
```

`BladeEngine.initInheritanceChain()` 中优先检查 `WireParentOverride.get()`。

### 7.2 使用

```java
// 主页面(index)渲染:不套任何外部布局,模板自身 @extends 原样生效。
// 组件下发渲染(renderComponents):若模板名命中 wireLayoutReplacements()(声明式)
// 或 setWireLayoutReplace()(请求级)规则,WireParentOverride.register 覆盖其 @extends,
// 未命中则用模板自身 @extends(「名字不匹配,使用原替换」)。
```

`item.jblade` 完全不变，`@extends` 在组件下发渲染期可按 `wireLayoutReplacements()`（声明式）或 `setWireLayoutReplace()`（请求级）覆盖；主页面（直访 /change）始终用模板字面量 `@extends('layouts.mdui.form')` 整页表单。

---

## 八、临时组件下发机制

### 8.1 核心类

- `WireEffects` — ThreadLocal 队列，收集当前请求需要下发的临时组件
- 临时组件直接继承 `WireController`，但 `render()` 返回纯 HTML 片段（不套 layout）

### 8.2 后端触发

```java
// 在任意 action 方法中
wire().component("toast", Map.of(
    "message", "保存成功",
    "type", "success"
));

wire().component("confirm", Map.of(
    "message", "确定删除？",
    "onConfirm", "delete"
));
```

### 8.3 组件注册（启动时）

```yaml
jaravel:
  wire:
    components:
      toast:   components.toast
      confirm: components.confirm
```

或代码注册：
```java
WireComponents.register("toast", "components.toast");
```

### 8.4 下发协议

组件 HTML 通过 `effects.components` 字段下发，wire.js 前端运行时解析并注入到对应 outlet。每个组件模板需遵循**单一根元素**约束。

---

## 九、wire:submit 在 button 上的语义

**前端行为（wire.js 改造）**：

1. 扫描 `wire:submit` 属性所在元素
2. 若元素是 `<form>`，直接监听其 submit 事件
3. 若元素是 `<button>` / `<input type=submit>`，查找所属 `<form>`，为该 form 绑定 submit 事件
4. 若找不到所属 form，退化为 `wire:click` 行为

**后端不变**：`update()` 收到 `action` 后正常反射调用。

这样 `item.jblade` 中 `form.jblade` 的 `<button type="submit">` 只需加 `wire:submit="save"` 属性即可，无需修改 `<form>` 标签。

---

## 十、Dialog 布局模板设计

新增 `layouts/mdui/form.dialog.jblade`：

```blade
@extends('layouts.mdui.main')

@assets
    <!-- Dialog 所需额外资源 -->
@endassets

@script
<script>
    (function() {
        var el = document.getElementById('wire-dialog-' + '{{$wireId}}');
        if (el) new mdui.Dialog(el).open();
    })();
</script>
@endscript

@section('body')
    <div id="wire-dialog-{{$wireId}}"
         class="mdui-dialog"
         style="width:60%;max-width:800px;height:80vh;
                overflow:hidden;display:flex;flex-direction:column;">
        <div class="mdui-dialog-title" style="flex-shrink:0;">
            @yield('title')
        </div>
        <div class="mdui-dialog-content" style="flex:1;overflow-y:auto;">
            @yield('form')
        </div>
        <div class="mdui-dialog-actions" style="flex-shrink:0;">
            <button type="button" class="mdui-btn" wire:click="close">取消</button>
            <button type="submit" class="mdui-btn mdui-color-theme-accent mdui-ripple"
                    wire:submit="save">提交</button>
        </div>
    </div>
@endsection
```

`item.jblade` 完全不变。直访时使用 `layouts.mdui.form`（整页），wire 触发时使用 `layouts.mdui.form.dialog`（Dialog）。

---

## 十一、安全机制设计

### 11.1 核心安全模型

参考 Livewire 的安全模型：**"snapshot 校验保护状态完整性，但 action 参数默认不可信，开发者负责授权"**。

| 攻击场景 | 风险等级 | 防护手段 |
|---------|---------|---------|
| 篡改 snapshot 中的属性值 | 高 | HMAC 签名验证 |
| 篡改 wire:click 参数传入恶意值 | 中 | 参数全 String + 开发者授权 |
| 通过 wire:model 注入受保护属性 | 中 | @WireLocked 注解 |
| 参数值含 XSS 脚本 | 中 | Blade `{{ }}` 自动 HTML 转义 |
| 前端 wire:click 属性被 DOM 注入 | 中 | JS 层结构化解析，禁用 eval |

### 11.2 Snapshot HMAC 签名

```java
// WireManager.java
private static final String HMAC_ALGORITHM = "HmacSHA256";

private static SecretKeySpec getSessionKey(Request request) {
    String key = request.session("wire_key");
    if (key == null) {
        key = UUID.randomUUID().toString();
        request.putSession("wire_key", key);
    }
    return new SecretKeySpec(key.getBytes(UTF_8), HMAC_ALGORITHM);
}

public static String encodeSnapshot(Map<String, Object> data, Request request) {
    String json = Json.stringify(stripInternalFields(data));
    String signature = hmac(json, getSessionKey(request));
    return signature + ":" + Base64.getEncoder().encodeToString(json.getBytes(UTF_8));
}

public static Map<String, Object> decodeSnapshot(String signed, Request request) {
    int colon = signed.indexOf(':');
    if (colon < 0) throw new TamperedSnapshotException("snapshot 格式无效");
    String expectedSig = signed.substring(0, colon);
    String base64 = signed.substring(colon + 1);
    byte[] bytes = Base64.getDecoder().decode(base64);
    String json = new String(bytes, UTF_8);
    String actualSig = hmac(json, getSessionKey(request));
    if (!MessageDigest.isEqual(
            expectedSig.getBytes(UTF_8), actualSig.getBytes(UTF_8))) {
        throw new TamperedSnapshotException("snapshot 签名验证失败");
    }
    return Json.parseToMap(json);
}
```

签名不匹配时返回 403 JSON，前端触发整页刷新。

### 11.3 参数全 String 反射调用

```java
private void invokeAction(String actionName, Map<String, Object> params) {
    if ("$refresh".equals(actionName)) return;
    if (actionName.startsWith("$")) {
        log.warn("未知 magic action: " + actionName);
        return;
    }

    Method method = findPublicMethod(this.getClass(), actionName);
    if (method == null) {
        log.warn("未找到 action 方法: " + actionName);
        return;
    }
    method.setAccessible(true);

    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length == 0) {
        method.invoke(this, new Object[0]);
        return;
    }

    // 所有参数统一转为 String，方法侧自行解析
    Object[] args = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
        Object val = params != null ? params.get(String.valueOf(i)) : null;
        args[i] = val != null ? String.valueOf(val) : null;
    }
    method.invoke(this, args);
}
```

方法签名侧自行解析：
```java
public void delete(String id) {
    Admin.self().find(Long.parseLong(id)).delete();
}
```

### 11.4 @WireLocked 注解

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireLocked {}
```

在 `update()` 中合并 params 时过滤：
```java
private void applyParams(Map<String, Object> data, Map<String, Object> params) {
    for (Map.Entry<String, Object> entry : params.entrySet()) {
        if (!lockedFields.contains(entry.getKey())) {
            data.put(entry.getKey(), entry.getValue());
        }
    }
}
```

使用示例：
```java
public class AdminController extends WireController {
    @WireLocked
    public Long id;         // 客户端不能通过 wire:model 修改
    public String name;     // 可修改
    public String password; // 可修改
}
```

### 11.5 JS 层参数结构化解析（禁用 eval）

```javascript
function parseAction(expr) {
    const match = expr.match(/^([a-zA-Z_$][\w$]*)(?:\((.*)\))?$/);
    if (!match) return { method: expr, params: [] };
    return {
        method: match[1],
        params: parseParams(match[2] || '')
    };
}

function parseParams(src) {
    if (!src.trim()) return [];
    const params = [];
    let cur = '', inStr = false, quote = '';
    for (let i = 0; i < src.length; i++) {
        const c = src[i];
        if (inStr) {
            cur += c;
            if (c === quote) inStr = false;
        } else if (c === "'" || c === '"') {
            inStr = true; quote = c; cur += c;
        } else if (c === ',') {
            params.push(cur.trim());
            cur = '';
        } else {
            cur += c;
        }
    }
    if (cur.trim()) params.push(cur.trim());
    return params;
}
```

**绝不使用 `eval()` 或 `new Function()` 执行参数表达式**，只做结构化解析。

### 11.6 Livewire 已知漏洞参考

| CVE | 说明 |
|-----|------|
| CVE-2024-47823 | 文件上传 RCE，仅根据 MIME 类型猜测扩展名，未验证实际文件扩展名 |
| CVE-2023-44444 | 2023年11月披露的 Livewire 相关安全公告 |

Java 版 Wire 实现中需特别注意文件上传验证和快照完整性校验。

---

## 十二、迁移计划（5 个阶段）

### 阶段 1：jblade 基础能力改造

| 内容 | 涉及模块 |
|------|---------|
| `@script`/`@assets` 指令编译 | jblade: BladeCompiler |
| `BladeContext` 新增 collectedAssets / collectedScripts | jblade: BladeContext |
| `BladeEngine.render()` 末尾自动注入 assets/scripts | jblade: BladeEngine |
| `WireParentOverride` 运行时覆盖 @extends | wire 新增 + jblade: BladeEngine |

### 阶段 2：核心类重构

| 内容 | 涉及模块 |
|------|---------|
| 新增 `WireController`（抽象基类） | wire |
| 新增 `WireView`（链式 API） | wire |
| 新增 `WireEffects`（ThreadLocal 临时组件队列） | wire |
| 新增 `WireParentOverride` | wire |
| 新增 `WireLocked` 注解 | wire |
| 新增 `TamperedSnapshotException` | wire |
| 删除旧 `WireService` / `WireResponse` / `component/` 包 | wire |
| `index()` / `update()` 完整实现 | wire |
| `WireManager` 增加 HMAC 签名 | wire |

### 阶段 3：前端 wire.js 改造

| 内容 | 说明 |
|------|------|
| `wire:submit` 支持 button 元素 | 查找所属 form 绑定 |
| `parseAction` 结构化解析 | 禁用 eval |
| 临时组件挂载协议 | effects.components 解析 |
| Dialog 模式支持 | wire.js 与 mdui Dialog 联动 |

### 阶段 4：error 项目迁移

| 内容 | 说明 |
|------|------|
| `AdminController` 继承 `WireController` | 实现 render/mount/save |
| 新增 `form.dialog.jblade` | Dialog 布局模板 |
| 新增 toast/confirm 组件 | 临时组件示例 |
| list 模板接入 `wire:click` | 修改/删除按钮 wire 化 |
| 修复过时 config | 清理失效配置 |
| 验证无感切换 | list → item 在 Dialog 中打开 |

### 阶段 5：demo 项目更新

| 内容 | 说明 |
|------|------|
| 重写 wire demo 示例 | 删除旧流式 API demo |
| 更新 wire/README.md | 完整使用文档 |

---

## 十三、新增/修改/删除类汇总

### 新增类

| 类名 | 用途 |
|------|------|
| `WireController.java` | 抽象基类（核心） |
| `WireView.java` | 链式视图配置 |
| `WireEffects.java` | ThreadLocal 临时组件队列 |
| `WireParentOverride.java` | 运行时 @extends 覆盖 |
| `WireLocked.java` | 属性锁定注解 |
| `TamperedSnapshotException.java` | 安全异常 |

### 修改类

| 类名 | 改动 |
|------|------|
| `WireManager.java` | encode/decodeSnapshot 增加 HMAC 签名；新增 collectPublicFields 委托 |
| `BladeCompiler.java` | 新增 @script/@assets 指令编译；@extends 支持 WireParentOverride |
| `BladeContext.java` | 新增 collectedAssets / collectedScripts 容器 |
| `BladeEngine.java` | render() 末尾自动注入；initInheritanceChain 读取 WireParentOverride |
| `wire.js` | wire:submit 支持 button；parseAction 结构化解析；临时组件挂载 |
| `WireAutoConfiguration.java` | 移除旧 component 注册相关 |

### 删除类

| 类名 | 说明 |
|------|------|
| `WireService.java` | 废弃，由 WireController 替代 |
| `WireResponse.java` | 废弃，由 WireController.update() 替代 |
| `WireRequest.java` | 可合并到 WireController 内部 |
| `component/WireComponents.java` | 废弃，统一走 WireController + WireEffects |
| `component/WireComponentRenderer.java` | 废弃 |
| `component/WireComponentPayload.java` | 废弃 |
| `component/WireComponentDefinition.java` | 废弃 |
| `component/WireOutlet.java` | 废弃，临时组件直接经 wire.js 注入 |
| `navigation/WireMiddleware.java` | 保留但简化 |
| `navigation/WireRenderer.java` | 保留 |
| `navigation/WireContext.java` | 保留 |

---

## 十四、jblade 三元运算符确认

已确认 `PhpExpressionTranslator.java` 中 `parseTernary()` 方法完整实现了 `? :` 三元和 `?:` Elvis 运算符。因此：

```blade
@extends($layout ? $layout : 'layouts.mdui.form')
```

在 jblade 中完全可用。不过最终选择了方式 A（WireParentOverride），此项不影响方案。

---

## 十五、关键文件绝对路径参考

| 文件 | 路径 |
|------|------|
| wire 模块根目录 | `d:\0code\ai\work\jaravel-vendor\wire\` |
| jblade 模块根目录 | `d:\0code\ai\work\jaravel-vendor\jblade\` |
| error 项目根目录 | `d:\0code\ai\work\error\` |
| 项目总览 | `d:\0code\ai\work\PROJECT_SUMMARY.md` |
| Wire 源码目录 | `jaravel-vendor\wire\src\main\java\com\weacsoft\jaravel\vendor\wire\` |
| AdminController | `error\src\main\java\com\weacsoft\system\app\http\controllers\AdminController.java` |
| item.jblade | `error\src\main\resources\views\mdui\admin\admin\item.jblade` |
| form.jblade | `error\src\main\resources\views\layouts\mdui\form.jblade` |
| list.jblade | `error\src\main\resources\views\mdui\admin\admin\list.jblade` |
| wire.js | `error\src\main\resources\static\wire.js` |

---

*文档生成时间：2026-08-12*
*基于 PROJECT_SUMMARY.md 及完整对话记录整理*
---

## 十六、2026-08-15 Wire功能修复记录

### 问题描述
访问 `/admin/admin` 点击"修改"按钮后，后端返回500错误，URL未更新。

### 问题根因
1. **ClassCastException**: Jackson解析JSON空对象`{}`时默认返回`ArrayList`而非`Map`
2. **NPE**: `Admin.find()`返回null时直接调用`.toObject()`导致空指针异常
3. **数据库表不存在**: `migration.auto-run: false`导致迁移未执行

### 修复内容

#### 1. WireRequest.java类型兼容修复
```java
// 兼容 params 可能是 ArrayList（Jackson 解析空对象 {} 时的问题）
Object paramsObj = data.get("params");
Map<String, Object> params;
if (paramsObj instanceof Map) {
    params = (Map<String, Object>) paramsObj;
} else if (paramsObj instanceof java.util.List) {
    params = new java.util.HashMap<>();
} else {
    params = new java.util.HashMap<>();
}
```

#### 2. AdminController.java NPE修复
```java
// 修复前（会NPE）
Admin a = Admin.self().find(id.toString()).toObject();

// 修复后（安全）
var rec = Admin.self().find(id.toString());
Admin a = rec != null ? rec.toObject() : null;
```

#### 3. application.yml迁移配置
```yaml
jaravel:
  migration:
    enabled: true
    auto-run: true  # 改为true，启动时自动执行迁移
```

### 测试验证
```bash
# 列表页访问
GET /admin/admin → 200 OK ✓

# Wire编辑请求
POST /admin/admin/change
{
  "wire_body": "{\"action\":\"edit(1)\",\"params\":{},\"sections\":[\"content\"]}"
}
→ 200 OK ✓
→ effects.url: "/admin/admin/change?id=1"
→ effects.components: 对话框HTML
→ effects.backUrl: "/admin/admin"
```

### 版本状态
- WireController: 1165cbb版本（支持参数化action如`edit(1)`）
- WireRequest: 含instanceof修复
- wire.js: bf2144c版本（含pushUrl处理）

### 提交记录
- Commit: `ce4d398`
- Message: `fix: WireRequest类型兼容 - 处理Jackson解析空对象为ArrayList的问题`
- 已推送到: `origin/master`

