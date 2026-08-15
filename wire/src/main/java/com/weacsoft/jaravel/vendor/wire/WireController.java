package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.route.RouteHelper;
import com.weacsoft.jaravel.vendor.wire.WireParentOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Wire 抽象控制器基类,类似 Laravel Livewire 的全页组件。
 * <p>
 * 用户继承此类并实现 {@link #render()} 即可获得完整的 Wire 能力:
 * <ul>
 *   <li>首屏渲染(index)：mount → collectPublicFields → fill → render → 套 layout → 注入 wire assets</li>
 *   <li>局部更新(update)：decode snapshot → 合并 params(排除 @WireLocked) → invoke action → 重新 render sections → JSON 响应</li>
 *   <li>临时组件下发：action 中通过 {@code wire().component("name", params)} 添加,自动随响应下发</li>
 * </ul>
 * <p>
 * 用户必须实现的:
 * <ul>
 *   <li>{@code protected WireView render()} —— 通过 {@code wireView("模板名", Map)} 返回模板;
 *       等价于 {@code ResponseBuilder.view("模板名", map)},且会自动把 Controller 的 public 属性
 *       聚合进模板,无需手动 {@code .with()} 注入。</li>
 * </ul>
 * <p>
 * 用户可选实现的:
 * <ul>
 *   <li>{@code protected void mount(Request request)} —— 初始化(仅首次 index() 调用),可读取 query/input</li>
 *   <li>{@code protected void fill(String key, Object value)} / {@code fill(Map)} —— 把键值对直接赋值到
 *       Controller 自己的 public 属性(等同于赋值,不做任何业务重写)</li>
 *   <li>{@code protected void refresh(Map)} —— 每次 wire 更新后重新加载展示数据(如重新查库)</li>
 *   <li>{@code protected Map<String,String> wireLayoutReplacements()} —— 模板级布局替换注册表(声明式):
 *       返回「模板名 → 替换布局名」映射,声明一次即可。例如
 *       {@code Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog")}
 *       表示「凡以组件形式下发渲染 {@code mdui.admin.admin.item} 时,用 {@code layouts.mdui.form.dialog}
 *       替换其 {@code @extends}」。替换<b>仅作用于组件下发渲染</b>;主页面(直访)渲染始终使用模板自身的
 *       {@code @extends},不受影响。不匹配的模板同样使用原 {@code @extends}。</li>
 *   <li>{@code protected Map<String,String> wireComponents()} —— 本控制器<b>强关联</b>的组件注册表(声明式):
 *       返回「组件名 → 模板名」映射(如 {@code Map.of("admin-form", "mdui.admin.admin.item")})。
 *       这类组件与控制器绑定(模板、布局替换、action 都由本控制器承接),<b>禁止</b>写入配置文件;
 *       配置文件({@code jaravel.wire.components})只放全局命名组件(toast/confirm 等)。</li>
 *   <li>{@code protected void setWireLayoutReplace(String template, String layout)} —— 请求级临时布局替换
 *       (仅当前请求生效,ThreadLocal 请求末清除)。一般场景用声明式 {@link #wireLayoutReplacements()} 即可,
 *       此方法仅用于个别 action 需要动态追加替换规则的情况。</li>
 *   <li>{@code protected String getUpdateRouteName()} —— 组件更新(POST)对应的路由名,
 *       wire:config 的 data-wire-update 会指向该路由</li>
 *   <li>{@code protected String getTemplateName()} —— 组件局部更新(section 刷新)对应的模板名;
 *       默认取 {@code render().getTemplateName()},若组件的 wire 更新目标是固定页面(如列表页),
 *       子类应覆盖返回该页面模板名,避免依赖请求状态。</li>
 * </ul>
 * <p>
 * 内置 magic action(由前端 wire.js 自动发起,无需在子类声明方法):
 * <ul>
 *   <li>{@code $sync} —— wire:model 双向绑定同步,仅把字段合并进快照,不调用任何方法</li>
 *   <li>{@code $refresh} —— 重新执行 {@link #refresh(Map)} 并刷新组件</li>
 * </ul>
 * <p>
 * 框架默认实现的(对接两条路由):
 * <ul>
 *   <li>{@code public Response index(Request request)} —— GET 首屏</li>
 *   <li>{@code public Response update(Request request)} —— POST 局部更新</li>
 * </ul>
 */
public abstract class WireController {

    private static final Logger log = LoggerFactory.getLogger(WireController.class);
    private static final Set<String> WIRE_INTERNAL_METHODS = new HashSet<>(Arrays.asList(
            "index", "update", "render", "mount", "fill",
            "getTemplateName",
            "wire", "getPublicFields", "getLockedFields", "collectPublicFields",
            "invokeAction", "isWireRequest", "buildUpdateUrl",
            "renderPage", "renderSections", "renderWireSections",
            "getSessionKey", "encodeSignedSnapshot", "decodeSignedSnapshot", "hmac"
    ));

    /**
     * 当前请求实例,在 index()/update() 入口保存,供子类 action(如 save())通过
     * {@link #isWireRequest()} 判断本次请求是否为 wire 局部更新请求。
     */
    protected Request currentRequest;

    /**
     * 模板级布局替换注册表(请求级 ThreadLocal)。
     * 由 {@link #setWireLayoutReplace(String, String)} 写入,渲染模板时
     * (index/update 的主渲染与临时组件渲染)若模板名命中则套用替换布局。
     * 每请求处理完毕在 finally 中清除,避免跨请求泄漏(控制器多为 Spring 单例)。
     */
    private static final ThreadLocal<Map<String, String>> WIRE_LAYOUT_REPLACEMENTS =
            ThreadLocal.withInitial(LinkedHashMap::new);

    /**
     * 子类实现的 render 方法返回的视图配置。
     * <p>
     * 语义等价于 Livewire 组件的 render():声明要渲染的模板及布局配置。
     */
    protected abstract WireView render();

    /**
     * 初始化方法,仅在首次 index() 请求时调用一次。
     * <p>
     * 语义等价于 Livewire 的 mount():用于加载初始数据(如从数据库查询 Model)。
     * 参数为当前 {@link Request},子类可读取 {@code request.query()}/{@code request.input()} 等。
     * 子类可在此通过 {@code fill(...)} 把数据赋值到 Controller 自己的 public 属性。
     *
     * @param request 当前 HTTP 请求
     */
    protected void mount(Request request) {
    }

    /**
     * 把单个键值对直接赋值到 Controller 自己的 public 属性。
     * <p>
     * 语义极其简单:等同于 {@code this.<key> = value}(按属性类型做基础类型转换)。
     * 不做任何业务重写,也不递归处理嵌套对象。找不到对应 public 属性时静默忽略。
     *
     * @param key   属性名(区分大小写)
     * @param value 属性值(为 null 时直接置 null)
     */
    protected void fill(String key, Object value) {
        if (key == null || key.isEmpty()) return;
        try {
            Field f = findPublicField(this.getClass(), key);
            if (f == null) return;
            f.setAccessible(true);
            if (value == null) {
                f.set(this, null);
            } else {
                f.set(this, convertValue(value.toString(), f.getType()));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 批量赋值 public 属性,等价于对每一项调用 {@link #fill(String, Object)}。
     * <p>
     * 直接把传入的键值对赋值到 Controller 自己的 public 属性,等同于逐字段赋值。
     *
     * @param data 要赋值的键值对(键为属性名)
     */
    protected void fill(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            fill(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 本控制器强关联的「模板级布局替换」注册表(声明式,一次声明处处生效)。
     * <p>
     * 返回「模板名 → 替换布局名」映射。例如:
     * <pre>{@code
     * @Override
     * protected Map<String, String> wireLayoutReplacements() {
     *     return Map.of("mdui.admin.admin.item", "layouts.mdui.form.dialog");
     * }
     * }</pre>
     * 表示「凡以组件形式下发渲染 {@code mdui.admin.admin.item} 时,用 {@code layouts.mdui.form.dialog}
     * 替换其 {@code @extends} 指定的父模板」(对应「判定到模板是 A,就用 B 换掉」的 Dialog 场景)。
     * <p>
     * <b>作用范围</b>:仅作用于组件下发渲染({@link #renderComponents 渲染临时组件}时),
     * 命中模板名才替换;不匹配的模板仍使用模板自身的 {@code @extends}。
     * 主页面(直访 index / wire 局部 section 刷新)渲染<b>不受影响</b>,始终使用模板自身的 {@code @extends}——
     * 因此直访 {@code /change} 走 {@code layouts.mdui.form} 整页表单,列表点击走对话框布局,互不干扰。
     * <p>
     * <b>与请求级 {@link #setWireLayoutReplace(String, String)} 的关系</b>:两者合并生效,
     * 请求级规则优先。一般场景声明式即可,无需在每个 action 里重复调用。
     *
     * @return 模板名 → 替换布局名;默认 null(无替换规则)
     */
    protected Map<String, String> wireLayoutReplacements() {
        return null;
    }

    /**
     * 本控制器<b>强关联</b>的组件注册表(声明式)。
     * <p>
     * 返回「组件名 → 模板名」映射,例如:
     * <pre>{@code
     * @Override
     * protected Map<String, String> wireComponents() {
     *     return Map.of("admin-form", "mdui.admin.admin.item");
     * }
     * }</pre>
     * 之后在 action 中 {@code WireEffects.push("admin-form", data)} 即可下发该组件,
     * 模板名由本注册表解析,无需在配置文件({@code jaravel.wire.components})中登记。
     * <p>
     * <b>强关联语义</b>:此类组件与控制器绑定——模板、布局替换({@link #wireLayoutReplacements()}),
     * 表单字段、action(save/edit/add)全部由本控制器承接。<b>禁止</b>写入配置文件;
     * 配置文件只放与控制器无关的全局命名组件(toast/confirm 等)。
     *
     * @return 组件名 → 模板名;默认 null(无控制器私有组件)
     */
    protected Map<String, String> wireComponents() {
        return null;
    }

    /**
     * 注册一个「请求级模板布局替换」规则:当以组件形式渲染名为 {@code templateName} 的模板时,
     * 用 {@code layoutName} 替换其 {@code @extends} 指定的父模板(仅当前请求生效,ThreadLocal 请求末清除)。
     * <p>
     * 设计意图(对应 Dialog 场景):「判断到模板是 A,就得用 B 把它换掉」。
     * 例如 {@code setWireLayoutReplace("mdui.admin.admin.item", "layouts.mdui.form.dialog")}
     * 表示:凡以组件形式渲染 {@code mdui.admin.admin.item} 模板,一律套用 {@code layouts.mdui.form.dialog}
     * 而非模板字面量里的 {@code @extends}。
     * <p>
     * <b>一般场景无需使用本方法</b>:请在 {@link #wireLayoutReplacements()} 中声明式注册(一次声明,处处生效)。
     * 本方法仅用于个别 action 需要动态追加替换规则的情况;与声明式规则合并,请求级优先。
     *
     * @param templateName 受影响的模板名(如 "mdui.admin.admin.item")
     * @param layoutName   替换成的父布局模板名(如 "layouts.mdui.form.dialog")
     */
    protected void setWireLayoutReplace(String templateName, String layoutName) {
        if (templateName != null && layoutName != null && !templateName.isEmpty() && !layoutName.isEmpty()) {
            WIRE_LAYOUT_REPLACEMENTS.get().put(templateName, layoutName);
        }
    }

    /**
     * 查询某模板注册的布局替换规则(合并声明式 {@link #wireLayoutReplacements()} 与请求级
     * {@link #setWireLayoutReplace(String, String)},请求级优先)。命中返回替换后的布局名,否则返回 null。
     *
     * @param templateName 模板名
     * @return 替换布局名或 null(表示使用模板自身的 @extends)
     */
    protected String getWireLayoutReplace(String templateName) {
        if (templateName == null) return null;
        String runtime = WIRE_LAYOUT_REPLACEMENTS.get().get(templateName);
        if (runtime != null) return runtime;
        Map<String, String> declared = wireLayoutReplacements();
        if (declared != null) return declared.get(templateName);
        return null;
    }

    /**
     * 返回当前 WireController 对应的模板名(局部更新 section 渲染的目标模板)。
     * <p>
     * 默认从 render().getTemplateName() 获取。若组件的 wire 更新目标是固定页面
     * (如列表页——对话框等组件通过 push 临时下发,不参与 section 刷新),
     * 子类应覆盖返回该固定页面模板名,避免依赖请求状态(fullPageForm 等字段在 update() 中不更新)。
     */
    protected String getTemplateName() {
        return render().getTemplateName();
    }

    /* ============ 框架默认实现(对接路由) ============ */

    /**
     * GET 请求：首屏渲染。
     * <p>
     * 流程:mount → collectPublicFields → fill → render(模板自身 @extends 整页) → 注入 wire assets。
     */
    public Response index(Request request) {
        try {
            this.currentRequest = request;
            Map<String, Object> data = new LinkedHashMap<>();
            // mount（仅首次加载时调用,从 Request 加载初始数据并赋值到 public 属性）
            mount(request);
            // 收集 public 属性
            collectPublicFields(data);
            // render
            WireView view = render();
            Map<String, Object> renderData = view.getMergedData(data);
            String templateName = view.getTemplateName();

            try {
                // 渲染页面:主页面渲染始终使用模板自身的 @extends(布局替换仅作用于组件下发渲染,
                // 见 renderComponents / getWireLayoutReplace)。直访 /change 走 item 模板自身的
                // @extends('layouts.mdui.form') 整页表单;列表页走 list 模板自身的 @extends。
                String html = renderPage(templateName, renderData);
                // 编码快照(签名,自动排除 @WireLocked 字段)
                String snapshot = encodeSignedSnapshot(data, request);
                // 构建 update URL
                String updateUrl = buildUpdateUrl(request);
                // 注入 wire assets
                html = WireManager.injectWireAssets(html, updateUrl, snapshot);
                return ResponseBuilder.html(html);
            } finally {
                WireParentOverride.clear();
                WIRE_LAYOUT_REPLACEMENTS.remove();
            }
        } catch (Exception e) {
            log.error("WireController.index 失败: " + e.getMessage(), e);
            return ResponseBuilder.error(500, "Wire 渲染失败: " + e.getMessage());
        }
    }

    /**
     * POST 请求：Wire 局部更新。
     * <p>
     * 流程:decode snapshot → 合并 params(排除 @WireLocked) → invoke action → 收集属性 → 渲染 sections → JSON。
     */
    public Response update(Request request) {
        try {
            this.currentRequest = request;
            // 检测是否为 wire 请求（含 wire_body）
            String wireBody = null;
            try {
                wireBody = request.input("wire_body");
                if (wireBody == null || wireBody.isEmpty()) {
                    wireBody = request.get("wire_body", "");
                }
            } catch (Exception ignored) {}

            // 传统表单提交（无 wire_body）：mount → save → redirect
            if (wireBody == null || wireBody.isEmpty()) {
                return handleTraditionalSubmit(request);
            }

            // 解析 wire_body
            WireRequest wireReq = WireRequest.from(request);
            String action = wireReq.getAction();
            Map<String, Object> params = wireReq.getParams();
            String snapshot = wireReq.getSnapshot();

            // 解码快照(带 HMAC 验证)
            Map<String, Object> data = decodeSignedSnapshot(snapshot, request);

            // 合并 params(排除 @WireLocked 字段)
            Set<String> locked = getLockedFields();
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (!locked.contains(entry.getKey())) {
                        data.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // 把解码后的快照(已合并 params)赋值到 Controller 自己的 public 属性
            // (等价于把快照状态还原到当前组件实例,以便 action 方法读取)
            fill(data);

            // $sync:wire:model 双向绑定同步。仅把字段值合并进快照并重新签名返回,
            // 不重渲染任何 section——否则整段 innerHTML 替换会把对话框/模态等局部组件
            // 的 DOM 状态(如 mdui Dialog 的打开状态、光标焦点)冲掉。前端拿到新快照后
            // 仅更新本地快照,不替换任何 DOM,从而保持对话框存活。
            if ("$sync".equals(action)) {
                Map<String, Object> synced = new LinkedHashMap<>(data);
                collectPublicFields(synced);
                String newSnapshot = encodeSignedSnapshot(synced, request);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("snapshot", newSnapshot);
                return ResponseBuilder.json(result);
            }

            // 反射调用 action 方法(参数按声明类型自动转换)
            if (action != null && !action.isEmpty()) {
                invokeAction(action, params);
            }

            // 重新加载展示数据(子类覆盖 refresh 以重新查库,保持列表等数据最新)
            refresh(params);

            // 重新收集 public 属性
            data = new LinkedHashMap<>(data);
            collectPublicFields(data);

            // 确定要刷新的 sections
            List<String> sections = wireReq.getSections();
            if (sections == null || sections.isEmpty()) {
                sections = WireManager.getSectionNames(getTemplateName());
            }

            // 渲染 sections
            Map<String, String> sectionHtmls = renderSections(getTemplateName(), sections, data);

            // 编码新快照
            String newSnapshot = encodeSignedSnapshot(data, request);

            // 取走临时组件,并渲染其 HTML(注入 wire:config 使其成为可交互组件)
            List<Map<String, Object>> components = renderComponents(WireEffects.drain(), request);
            // 取走 dispatch 事件(供前端 window.dispatchEvent 触发,如打开/关闭对话框)
            List<Map<String, Object>> dispatches = WireEffects.drainDispatches();
            // 计算重定向地址:优先使用 action 显式指定的(WireEffects.redirect),
            // 否则回退到 getRedirectUrl()(整页表单保存等场景,默认 null = 不跳转)。
            // 这样 edit()/add() 只下发组件、不整页跳转;save() 显式 redirect 回列表。
            String redirectUrl = WireEffects.drainRedirect();
            if (redirectUrl == null || redirectUrl.isEmpty()) {
                redirectUrl = getRedirectUrl(request);
            }
            // 计算 URL 变更(pushState):action 中通过 WireEffects.pushUrl 指定,
            // 前端仅用 history.pushState 改变地址栏(如点击「修改」后 URL 变深链),不刷新页面。
            String pushUrl = WireEffects.drainPushUrl();
            // 计算返回 URL:优先使用 action 显式指定的(WireEffects.backUrl),
            // 否则自动从当前请求 URI 推断(去掉最后一段路径)。
            // 例如:请求 URI=/admin/admin/change → backUrl=/admin/admin
            String backUrl = WireEffects.getBackUrl();
            if (backUrl == null || backUrl.isEmpty()) {
                backUrl = inferBackUrl(request);
                if (backUrl != null && !backUrl.isEmpty()) {
                    WireEffects.backUrl(backUrl); // 设置回 ThreadLocal 供 renderComponents 使用
                }
            }

            // 构建响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sections", sectionHtmls);
            result.put("snapshot", newSnapshot);
            Map<String, Object> effects = new LinkedHashMap<>();
            if (!components.isEmpty()) {
                effects.put("components", components);
            }
            if (!dispatches.isEmpty()) {
                effects.put("dispatch", dispatches);
            }
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                effects.put("redirect", redirectUrl);
            }
            if (pushUrl != null && !pushUrl.isEmpty()) {
                effects.put("url", pushUrl);
            }
            if (backUrl != null && !backUrl.isEmpty()) {
                effects.put("backUrl", backUrl);
            }
            if (!effects.isEmpty()) {
                result.put("effects", effects);
            }

            return ResponseBuilder.json(result);
        } catch (TamperedSnapshotException e) {
            log.warn("Wire snapshot 篡改: " + e.getMessage());
            return ResponseBuilder.json(Map.of("error", Map.of(
                    "status", 403,
                    "message", "Snapshot 验证失败,请刷新页面"
            )));
        } catch (Exception e) {
            log.error("WireController.update 失败: " + e.getMessage(), e);
            return ResponseBuilder.json(Map.of("error", Map.of(
                    "status", 500,
                    "message", "Wire 更新失败: " + e.getMessage()
            )));
        } finally {
            // 控制器多为 Spring 单例,请求级 ThreadLocal 必须显式清除,否则会泄漏到同线程的下一个请求。
            WIRE_LAYOUT_REPLACEMENTS.remove();
        }
    }

    /**
     * 处理传统表单提交（无 wire_body 的 POST 请求）。
     * <p>
     * 当表单通过传统 method="post" 提交（而非 wire:submit）时走此路径：
     * fill(request.all()) → invokeAction(getDefaultAction()) → redirect
     * <p>
     * 子类可覆盖 {@link #getDefaultAction()} 和 {@link #getRedirectUrl(Request)} 来自定义行为。
     *
     * @param request HTTP 请求
     * @return 重定向响应
     */
    protected Response handleTraditionalSubmit(Request request) {
        // 用表单数据初始化
        Map<String, Object> formData = new LinkedHashMap<>();
        try {
            Map<String, Object> all = request.all();
            if (all != null) formData.putAll(all);
        } catch (Exception e) {
            log.warn("收集表单数据失败", e);
        }
        // 表单字段为简单键值对,直接赋值到 Controller 自己的 public 属性
        fill(formData);
        // 调用默认 action（通常是 save）
        String defaultAction = getDefaultAction();
        if (defaultAction != null && !defaultAction.isEmpty()) {
            invokeAction(defaultAction, formData);
        }
        // 重定向:优先使用 action 显式指定的(WireEffects.redirect,如 save 后跳回列表),
        // 否则回退到 getRedirectUrl(Request)。传统表单(直接访问 /change 提交)走整页跳转,属正常行为。
        String redirectUrl = WireEffects.drainRedirect();
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            redirectUrl = getRedirectUrl(request);
        }
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            return ResponseBuilder.redirect(redirectUrl);
        }
        return ResponseBuilder.content("保存成功");
    }

    /**
     * 传统表单提交时调用的默认 action 方法名。
     * <p>
     * 子类可覆盖返回如 "save"、"store" 等。默认返回 "save"。
     */
    protected String getDefaultAction() {
        return "save";
    }

    /**
     * 传统表单提交成功后的重定向 URL。
     * <p>
     * 子类可覆盖返回具体的重定向地址。默认返回 null（不重定向）。
     * wire 请求场景下，此 URL 会作为 effects.redirect 下发到前端触发跳转。
     *
     * @param request HTTP 请求
     * @return 重定向 URL，null 表示不重定向
     */
    protected String getRedirectUrl(Request request) {
        return null;
    }

    /* ============ 便利方法 ============ */

    /**
     * 创建一个 WireView 对象,用于 render() 返回。
     * <p>
     * 等价于 {@code ResponseBuilder.view(templateName, null)}:模板只接收 Controller 的
     * public 属性(由框架自动聚合),无需手动注入额外数据。
     */
    protected WireView wireView(String templateName) {
        return new WireView(templateName);
    }

    /**
     * 创建一个 WireView 对象并附带额外数据,用于 render() 返回。
     * <p>
     * 语义完全等同于 {@code ResponseBuilder.view("模板名", map)}:框架会把 Controller 自己的
     * public 属性与这里的 {@code extra} 一起聚合进模板(extra 优先),无需使用 {@code .with()} 手动注入。
     *
     * @param templateName 模板名(如 "mdui.admin.admin.list")
     * @param extra        额外渲染数据(可为 null;没有额外参数时直接传 null)
     */
    protected WireView wireView(String templateName, Map<String, Object> extra) {
        WireView view = new WireView(templateName);
        if (extra != null) view.with(extra);
        return view;
    }

    /**
     * 获取 wire 响应构建器,用于在 action 中下发临时组件。
     * <p>
     * 使用示例:
     * <pre>{@code
     * wire().component("toast", Map.of("message", "保存成功", "type", "success"));
     * }</pre>
     */
    protected WireResponseHelper wire() {
        return new WireResponseHelper();
    }

    /* ============ 内部辅助方法 ============ */

    private Map<String, Object> collectPublicFields(Map<String, Object> target) {
        for (Field f : findPublicFields(this.getClass())) {
            try {
                f.setAccessible(true);
                target.put(f.getName(), f.get(this));
            } catch (Exception ignored) {
            }
        }
        return target;
    }

    private Set<String> getLockedFields() {
        Set<String> locked = new HashSet<>();
        for (Field f : findPublicFields(this.getClass())) {
            if (f.isAnnotationPresent(WireLocked.class)) {
                locked.add(f.getName());
            }
        }
        return locked;
    }

    private List<Field> findPublicFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != WireController.class && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())) {
                    result.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    private Field findPublicField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(name);
                if (!Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())) {
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void invokeAction(String action, Map<String, Object> params) {
        if ("$refresh".equals(action)) {
            refresh(params);
            return;
        }
        if ("$sync".equals(action)) {
            // wire:model 双向绑定的同步动作:仅把字段值合并进快照(已在 update() 中完成),
            // 不调用任何 action 方法,刷新由 update() 统一处理。
            return;
        }
        if ("$paginate".equals(action)) {
            // 分页请求:仅调用 refresh 重新加载数据,不调用任何 action 方法
            refresh(params);
            // 同步地址栏为 ?page=N(保留已有查询参数,如搜索条件 key/value)。
            // 前端收到 effects.url 后 history.pushState,不刷新页面;翻回第 1 页同样还原 URL。
            if (params != null && params.get("pageNum") != null) {
                try {
                    int pageNum = Integer.parseInt(params.get("pageNum").toString());
                    if (currentRequest != null) {
                        // 主页面路径:POST 更新端点(如 /admin/admin/change)的 uri 不是列表页,
                        // 用 inferBackUrl 去末段得到列表页路径(如 /admin/admin)。
                        String basePath = inferBackUrl(currentRequest);
                        if (basePath == null) basePath = "/";
                        StringBuilder sb = new StringBuilder(basePath);
                        Map<String, Object> qm = currentRequest.query();
                        LinkedHashMap<String, Object> newQuery = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : qm.entrySet()) {
                            String k = e.getKey();
                            if ("page".equals(k) || e.getValue() == null) continue;
                            newQuery.put(k, e.getValue());
                        }
                        if (pageNum > 1) newQuery.put("page", pageNum);
                        boolean first = true;
                        for (Map.Entry<String, Object> e : newQuery.entrySet()) {
                            sb.append(first ? "?" : "&")
                              .append(e.getKey()).append("=").append(encodeQueryParam(String.valueOf(e.getValue())));
                            first = false;
                        }
                        WireEffects.pushUrl(sb.toString());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }
        if (action.startsWith("$")) {
            log.warn("未知 magic action: " + action);
            return;
        }

        // 参数化 action 解析由前端 wire.js 的 parseWireAction 负责:
        //   wire:click="edit(1)"  →  action="edit", params={"0":"1"}
        //   wire:click="role('admin')"  →  action="role", params={"0":"'admin'"}
        // 后端直接按精确方法名查找，参数从 params 按位置下标读取。
        Method method = findPublicMethod(this.getClass(), action);
        if (method == null) {
            log.warn("未找到 action 方法: " + action);
            return;
        }
        method.setAccessible(true);

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args;
        if (paramTypes.length == 0) {
            args = new Object[0];
        } else {
            args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                Object val = params != null ? params.get(String.valueOf(i)) : null;
                // 按 action 方法声明的参数类型做基础转换(如 Long/Integer/Boolean),
                // 使 wire:click="delete(1)" 能正确映射到 delete(Long id)。
                args[i] = val != null ? convertValue(val.toString(), paramTypes[i]) : null;
            }
        }
        try {
            method.invoke(this, args);
        } catch (Exception e) {
            throw new RuntimeException("action 方法调用失败: " + action, e);
        }
    }

    /**
     * 刷新方法，当 action 为 $refresh 时调用。
     * <p>
     * 子类可覆盖此方法以重新加载数据（如从数据库查询最新值）。
     *
     * @param params 请求参数
     */
    protected void refresh(Map<String, Object> params) {
    }

    private Method findPublicMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                for (Method m : current.getDeclaredMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())
                            && Modifier.isPublic(m.getModifiers())
                            && m.getName().equals(name)
                            && !WIRE_INTERNAL_METHODS.contains(m.getName())) {
                        return m;
                    }
                }
            } catch (Exception ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * 判断当前请求是否应该使用「wire 交互」语义(如 mount 期区分「直访整页」与「wire 局部更新」)。
     * <p>
     * 默认实现：判断请求是否携带 wire_body(POST)或 X-Wire-Request 头。
     * 子类可覆盖此方法以改变行为，例如让所有请求都按 wire 处理。
     * <p>
     * 注意：本框架已移除 getLayout()——主页面渲染始终使用模板自身的 {@code @extends}，
     * 布局切换统一由 {@link #wireLayoutReplacements()}（声明式,组件下发渲染时按模板名替换
     * {@code @extends}）与 {@link #setWireLayoutReplace(String, String)}（请求级临时规则）完成。
     *
     * @param request 当前请求
     * @return true=是 wire 请求
     */
    protected boolean useWireLayout(Request request) {
        return isWireRequest(request);
    }

    /**
     * 判断当前请求是否为 wire 局部更新请求。
     * <p>
     * 基于 {@link #index(Request)} / {@link #update(Request)} 入口保存的
     * {@link #currentRequest} 进行判断，供子类 action（如 save()）区分
     * 「对话框内 wire 提交」与「直接访问表单的传统提交」并分别处理。
     *
     * @return true=当前请求是 wire 局部更新（带 wire_body / X-Wire-Request 头）
     */
    protected boolean isWireRequest() {
        return currentRequest != null && isWireRequest(currentRequest);
    }

    private boolean isWireRequest(Request request) {
        try {
            String body = request.input("wire_body");
            return body != null && !body.isEmpty();
        } catch (Exception e) {
            try {
                return "true".equals(request.header("X-Wire-Request"));
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * 返回组件更新(POST)对应的 URL,即前端 wire.js 发起局部更新请求的目标地址。
     * <p>
     * 默认按 {@link #getUpdateRouteName()} 解析路由;若子类未指定路由名,则回退为当前请求 URI。
     * <b>关键点</b>:列表页(GET {@code /admin})的更新必须指向 POST 端点(如 {@code /admin/change}),
     * 而不是 GET 的 URI,否则 wire 局部更新会打到错误的地址。
     */
    protected String buildUpdateUrl(Request request) {
        try {
            String name = getUpdateRouteName();
            if (name != null && !name.isEmpty()) {
                return RouteHelper.route(name);
            }
        } catch (Exception ignored) {
        }
        try {
            return request.uri();
        } catch (Exception e) {
            return "/wire/update";
        }
    }

    /**
     * 从当前请求 URI 推断「返回 URL」:去掉最后一段路径。
     * 例如 /admin/admin/change → /admin/admin，/admin/admin/change?id=5 → /admin/admin。
     */
    private String inferBackUrl(Request request) {
        try {
            String uri = request.uri();
            if (uri == null || uri.isEmpty() || uri.equals("/")) return null;
            // 去掉 query string
            int qIdx = uri.indexOf('?');
            String path = qIdx >= 0 ? uri.substring(0, qIdx) : uri;
            // 去掉尾斜杠
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            // 去掉最后一段
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash <= 0) return null;  // 只有一级路径（如 /）或零级
            return path.substring(0, lastSlash);
        } catch (Exception e) {
            return null;
        }
    }

    /** URL 查询参数编码(与 {@link com.weacsoft.jaravel.vendor.core.pagination.Paginator} 的编码一致)。 */
    private static String encodeQueryParam(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /**
     * 组件更新(POST)对应的路由名,供 {@link #buildUpdateUrl(Request)} 解析。
     * <p>
     * 子类应覆盖返回如 {@code "admin.admin.change"},使列表页的 wire:config 更新地址
     * 指向正确的 POST 端点。默认返回 null(回退为当前 URI)。
     *
     * @return 路由别名,或 null
     */
    protected String getUpdateRouteName() {
        return null;
    }

    private String renderPage(String templateName, Map<String, Object> data) {
        return WireManager.renderForWire(templateName, data);
    }

    private Map<String, String> renderSections(String templateName, List<String> sections, Map<String, Object> data) {
        return WireManager.renderSections(templateName, sections, data);
    }

    /* ============ 快照 HMAC 签名 ============ */

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SESSION_KEY_NAME = "wire_key";

    private String encodeSignedSnapshot(Map<String, Object> data, Request request) {
        // 排除 @WireLocked 字段(如列表数据),避免把大型/派生数据塞进快照
        Map<String, Object> filtered = new LinkedHashMap<>(data);
        for (String locked : getLockedFields()) {
            filtered.remove(locked);
        }
        String snapshot = WireManager.encodeSnapshot(filtered);
        String key = getOrCreateSessionKey(request);
        String signature = hmac(snapshot, key);
        return signature + ":" + snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeSignedSnapshot(String signed, Request request) {
        if (signed == null || signed.isEmpty()) return new LinkedHashMap<>();
        int colon = signed.indexOf(':');
        if (colon < 0) throw new TamperedSnapshotException("snapshot 格式无效");
        String expectedSig = signed.substring(0, colon);
        String base64 = signed.substring(colon + 1);
        Map<String, Object> data = WireManager.decodeSnapshot(base64);
        String actualSig = hmac(base64, getOrCreateSessionKey(request));
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                actualSig.getBytes(StandardCharsets.UTF_8))) {
            throw new TamperedSnapshotException("snapshot 签名验证失败");
        }
        return data;
    }

    private String getOrCreateSessionKey(Request request) {
        try {
            String key = request.session(SESSION_KEY_NAME);
            if (key == null || key.isEmpty()) {
                key = UUID.randomUUID().toString();
                request.putSession(SESSION_KEY_NAME, key);
            }
            return key;
        } catch (Exception e) {
            return "fallback-key-" + UUID.randomUUID().toString();
        }
    }

    private static String hmac(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_ALGORITHM);
            javax.crypto.spec.SecretKeySpec keySpec =
                    new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }

    /**
     * 类型转换(参考 BaseModel.fill 的策略,简化版)。
     */
    private static Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class || targetType == Object.class) return value;
        if (targetType == Long.class || targetType == long.class) {
            try { return Long.parseLong(value); } catch (Exception e) { return null; }
        }
        if (targetType == Integer.class || targetType == int.class) {
            try { return Integer.parseInt(value); } catch (Exception e) { return null; }
        }
        if (targetType == Double.class || targetType == double.class) {
            try { return Double.parseDouble(value); } catch (Exception e) { return null; }
        }
        if (targetType == Float.class || targetType == float.class) {
            try { return Float.parseFloat(value); } catch (Exception e) { return null; }
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (targetType == Short.class || targetType == short.class) {
            try { return Short.parseShort(value); } catch (Exception e) { return null; }
        }
        if (targetType == Byte.class || targetType == byte.class) {
            try { return Byte.parseByte(value); } catch (Exception e) { return null; }
        }
        if (targetType == Character.class || targetType == char.class) {
            return value.length() > 0 ? value.charAt(0) : null;
        }
        return value;
    }

    /**
     * 渲染临时组件 HTML。
     * <p>
     * 根据组件名从 WireProperties.components 注册表中查找模板名,
     * 渲染后将 HTML 返回给前端下发。
     * <p>
     * 每个下发的组件都是「可交互」的独立 Wire 组件:内嵌一份 {@code wire:config}
     * (含 data-wire-update 与 wire:snapshot),前端据此把它初始化为带 wire:model /
     * wire:submit 绑定的活动组件,从而对话框里的表单能正确双向绑定、并在提交时把
     * 字段回传到服务端(如 save)。其更新地址统一指向上层组件(本控制器)的更新路由,
     * 即「点击弹框」与「直接访问编辑页」由同一个控制器承接(save 等 action 共用)。
     *
     * @param request 当前请求(用于计算更新地址与签名快照)
     */
    private List<Map<String, Object>> renderComponents(List<Map<String, Object>> rawComponents, Request request) {
        // 上层组件的更新地址:对话框提交的 save 等 action 走同一个 update 入口
        String parentUpdateUrl = buildUpdateUrl(request);
        List<Map<String, Object>> rendered = new ArrayList<>();
        for (Map<String, Object> raw : rawComponents) {
            String name = (String) raw.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) raw.get("params");
            String templateName = resolveComponentTemplate(name);
            if (templateName == null) {
                log.warn("未找到组件模板: " + name);
                continue;
            }
            try {
                // 组件唯一 id:每个组件实例都不同(基于 nanoTime),注入为 $wireId 供模板用作 DOM 唯一标记。
                // 切勿使用 csrf_token()/常量——它们在同一次请求内恒定,会导致多个组件 id 重复。
                String compId = "wc-" + name + "-" + System.nanoTime();
                Map<String, Object> renderParams = new LinkedHashMap<>();
                if (params != null) renderParams.putAll(params);

                // 模板级布局替换:若本组件模板名命中 setWireLayoutReplace 注册的规则,
                // 则用替换布局覆盖其 @extends(对应「判定到模板是 A,就用 B 换掉」的 Dialog 场景)。
                // 规则由 action(如 edit()/add())在调用 wire().component(...) 前注册,
                // 渲染该组件时生效,不影响其它未命中的模板。
                String parentOverride = getWireLayoutReplace(templateName);
                if (parentOverride != null) {
                    WireParentOverride.register(templateName, parentOverride);
                }
                try {
                    renderParams.put("wireId", compId);
                    String html = WireManager.renderForWire(templateName, renderParams);
                    // 保证单一根元素(片段模板如 dialog/toast 可能含多个兄弟节点)
                    html = ensureSingleRoot(html);

                    // 生命周期脚本组件(整段 HTML 即一个 <script wire:lifecycle>,如 snackbar):
                    // 把脚本内容抽到 payload.script,html 置空,且不注入 wire:config。
                    // 纯生命周期组件无需更新地址/签名快照;若仍注入, injectConfigIntoRoot 会把 JSON
                    // 塞进 <script> 根内部,导致前端 new Function 报 SyntaxError: Unexpected token '<'。
                    String lifecycleScript = null;
                    // 纯生命周期脚本组件(整段 HTML 即一个 <script wire:lifecycle>,如 snackbar):
                    // 字符串判断(不用正则):以 <script 开头、</script> 结尾,且 opening tag 含 wire:lifecycle。
                    // 把脚本内容抽到 payload.script,html 置空,且不注入 wire:config。
                    // 纯生命周期组件无需更新地址/签名快照;若仍注入, injectConfigIntoRoot 会把 JSON
                    // 塞进 <script> 根内部,导致前端 new Function 报 SyntaxError: Unexpected token '<'。
                    String trimmedHtml = html.trim();
                    if (trimmedHtml.startsWith("<script") && trimmedHtml.endsWith("</script>")) {
                        int tagEnd = trimmedHtml.indexOf('>');
                        if (tagEnd > 0 && trimmedHtml.substring(0, tagEnd).contains("wire:lifecycle")) {
                            int closeStart = trimmedHtml.lastIndexOf("</script>");
                            if (closeStart > tagEnd) {
                                lifecycleScript = trimmedHtml.substring(tagEnd + 1, closeStart);
                                html = ""; // 无内容节点,前端 mount 视为纯生命周期组件(不注入 div)
                            }
                        }
                    }

                    if (lifecycleScript == null) {
                        // 组件快照:用 push 时传入的数据(去掉 wireId 等框架内部标记),
                        // 经 HMAC 签名,供前端回传后在 update() 中解码并 fill 到本控制器字段。
                        Map<String, Object> snapshotData = new LinkedHashMap<>();
                        if (params != null) {
                            for (Map.Entry<String, Object> e : params.entrySet()) {
                                if (!"wireId".equals(e.getKey())) {
                                    snapshotData.put(e.getKey(), e.getValue());
                                }
                            }
                        }
                        String snapshot = encodeSignedSnapshot(snapshotData, request);

                        // 内嵌 wire:config:data-wire-update 指向上层组件更新路由,
                        // wire:snapshot 为签名快照。注入到「根元素内部末尾」(而非最后一个 </ 之前——
                        // 片段模板末尾往往是 </script>,插到它前面会把 JSON 塞进脚本块破坏渲染)。
                        // 放在根元素内部,使前端 initComponent 以对话框本身为作用域(scope=config.parentElement),
                        // 避免与列表组件的事件被重复绑定。
                        String configScript = "<script type=\"application/json\" wire:config"
                                + " data-wire-update=\"" + escapeAttr(parentUpdateUrl) + "\""
                                + " wire:snapshot=\"" + escapeAttr(snapshot) + "\"></script>";
                        html = injectConfigIntoRoot(html, configScript);

                        // 注入 data-wire-back-url:供前端 dialog 取消按钮读取,还原地址栏。
                        // 由 action(edit/add)通过 WireEffects.backUrl() 指定,
                        // 避免 dialog 模板写死返回 URL,与控制器逻辑彻底解耦。
                        // 注意:drainBackUrl 是请求级一次性读取,这里调用后从 ThreadLocal 中取出。
                        String backUrlAttr = WireEffects.drainBackUrl();
                        if (backUrlAttr != null && !backUrlAttr.isEmpty()) {
                            html = injectDataAttr(html, "data-wire-back-url", backUrlAttr);
                        }
                    }

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("params", renderParams);
                    entry.put("html", html);
                    if (lifecycleScript != null) {
                        entry.put("script", lifecycleScript);
                    }
                    entry.put("id", compId);
                    rendered.add(entry);
                } finally {
                    if (parentOverride != null) {
                        WireParentOverride.clear();
                    }
                }
            } catch (Exception e) {
                log.error("渲染组件失败: " + name, e);
            }
        }
        return rendered;
    }

    /** 转义 HTML 属性值中的引号等特殊字符 */
    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 在根元素的起始标签上追加一个自定义属性(data-wire-back-url)。
     * <p>
     * 先定位第一个非自闭合的起始标签,在其末尾插入属性,
     * 例如 {@code <div class="mdui-dialog">} → {@code <div class="mdui-dialog" data-wire-back-url="/admin/admin">}。
     */
    private static String injectDataAttr(String html, String attrName, String attrValue) {
        if (html == null || html.isEmpty()) return html;
        String trimmed = html.trim();
        if (!trimmed.startsWith("<")) return html + " " + attrName + "=\"" + escapeAttr(attrValue) + "\"";
        int tagEnd = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '>' || c == '\t' || c == '\n' || c == '/') {
                tagEnd = i;
                break;
            }
        }
        if (tagEnd <= 1) return html;
        String rootTag = trimmed.substring(1, tagEnd);
        if (rootTag.isEmpty() || !rootTag.matches("[a-zA-Z][a-zA-Z0-9]*")) return html;
        // 跳过自闭合标签(/>)
        if (trimmed.charAt(tagEnd - 1) == '/') return html;
        String attr = " " + attrName + "=\"" + escapeAttr(attrValue) + "\"";
        return trimmed.substring(0, tagEnd) + attr + trimmed.substring(tagEnd);
    }

    /**
     * 把 wire:config 脚本注入到 HTML 根元素内部末尾。
     * <p>
     * 先确定根标签名(取 html 开头第一个标签),再在最后一个 {@code </根标签>} 前插入。
     * 不能简单用 {@code lastIndexOf("</")}——片段模板末尾往往是 {@code </script>},
     * 会把 JSON 配置插进脚本块内部,破坏组件渲染。
     *
     * @param html        已 ensureSingleRoot 的组件 HTML
     * @param configScript wire:config 脚本
     * @return 注入后的 HTML
     */
    private String injectConfigIntoRoot(String html, String configScript) {
        if (html == null || html.isEmpty()) return html;
        String trimmed = html.trim();
        if (!trimmed.startsWith("<")) {
            return html + configScript;
        }
        // 提取根标签名(如 <div class="mdui-dialog"> → div)
        int tagEnd = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '>' || c == '\t' || c == '\n' || c == '/') {
                tagEnd = i;
                break;
            }
        }
        if (tagEnd <= 1) return html + configScript;
        String rootTag = trimmed.substring(1, tagEnd);
        if (rootTag.isEmpty() || !rootTag.matches("[a-zA-Z][a-zA-Z0-9]*")) {
            return html + configScript;
        }
        String closeTag = "</" + rootTag + ">";
        int lastClose = trimmed.lastIndexOf(closeTag);
        if (lastClose >= 0) {
            return trimmed.substring(0, lastClose) + configScript + trimmed.substring(lastClose);
        }
        return html + configScript;
    }

    /**
     * 解析组件名对应的模板名。解析顺序:
     * <ol>
     *   <li>本控制器强关联组件注册表 {@link #wireComponents()}(如 admin-form → mdui.admin.admin.item);</li>
     *   <li>全局命名组件注册表 {@link WireManager#resolveComponentTemplate(String)}
     *       (来自 jaravel.wire.components 配置,如 toast → components.toast);</li>
     *   <li>兜底:组件名即模板名。</li>
     * </ol>
     * 控制器私有组件(对话框/表单等)必须在 {@link #wireComponents()} 声明,禁止写入配置文件。
     */
    private String resolveComponentTemplate(String name) {
        if (name == null || name.isEmpty()) return name;
        // 1. 控制器强关联组件注册表(声明式,与控制器绑定)
        Map<String, String> own = wireComponents();
        if (own != null) {
            String t = own.get(name);
            if (t != null && !t.isEmpty()) return t;
        }
        // 2. 全局命名组件注册表(配置文件 jaravel.wire.components,如 toast/confirm)
        String global = WireManager.resolveComponentTemplate(name);
        if (global != null && !global.isEmpty()) return global;
        // 3. 兜底:组件名即模板名
        return name;
    }

    /**
     * 确保 HTML 有单一根元素。若无,包裹到 <div> 中。
     */
    private String ensureSingleRoot(String html) {
        if (html == null || html.isEmpty()) return "";
        String trimmed = html.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith("/>")) return trimmed;
        // 简化:如果开头是 <tag 且不是自闭合,且末尾是 </tag>,认为已有根元素
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            int firstSpace = trimmed.indexOf(' ');
            int lastClose = trimmed.lastIndexOf("</");
            if (lastClose >= 0 && firstSpace > 0 && firstSpace < 100) {
                String openTag = trimmed.substring(1, Math.min(firstSpace, trimmed.indexOf('>')));
                if (trimmed.endsWith("</" + openTag + ">")) return trimmed;
            }
        }
        return "<div>" + trimmed + "</div>";
    }

    /* ============ 内嵌响应辅助类 ============ */

    /**
     * Wire 响应辅助器,用于在 action 中下发临时组件。
     * <p>
     * 使用:
     * <pre>{@code
     * wire().component("toast", Map.of("message", "保存成功", "type", "success"));
     * }</pre>
     */
    public static class WireResponseHelper {
        /**
         * 添加一个临时组件到当前请求的下发队列。
         *
         * @param name   组件名(如 "toast", "confirm")
         * @param params 组件参数
         * @return this 支持链式调用
         */
        public WireResponseHelper component(String name, Map<String, Object> params) {
            WireEffects.push(name, params);
            return this;
        }
    }
}