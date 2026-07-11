package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wire 流式上下文 —— 把请求解析、默认值填充、action 处理、响应构建串联起来。
 * <p>
 * 设计理念：控制器一行链式调用搞定，不写 if/switch。
 *
 * <h3>三步流程</h3>
 *
 * <h4>第一步：填充请求</h4>
 * <pre>{@code
 * WireService ctx = WireService.from(request, "wire-demo", "/api/wire/demo");
 * }</pre>
 * 这一步自动解析 snapshot + action + params + sections，返回上下文对象。
 *
 * <h4>第二步：进行各种处理</h4>
 * <pre>{@code
 * ctx.once("count", 0)                          // 没有就填默认值
 *   .once("message", "")
 *   .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
 *   .action("increment", c -> c.put("count", c.getInt("count") + 1))
 *   .action("decrement", c -> c.put("count", c.getInt("count") - 1))
 *   .action("reset", c -> { c.put("count", 0); c.put("message", ""); })
 *   .action("addItem", c -> {
 *       List<Object> items = c.getList("items");
 *       items.add("项目 " + (items.size() + 1));
 *   })
 *   .action("removeItem", c -> {
 *       List<Object> items = c.getList("items");
 *       if (!items.isEmpty()) items.remove(items.size() - 1);
 *   });
 * }</pre>
 *
 * <h4>第三步：生成响应</h4>
 * <pre>{@code
 * // 方式 A：直接返回
 * return ctx.responseUpdate();
 *
 * // 方式 B：先取数据再自己构建
 * Map<String, Object> data = ctx.toData();
 * List<String> sections = ctx.toSections();
 * return WireResponse.update("wire-demo", data, sections);
 *
 * // 方式 C：初始页面渲染
 * return ctx.responseWire();
 * }</pre>
 *
 * <h3>完整控制器示例</h3>
 * <pre>{@code
 * public Response page(Request request) {
 *     return WireService.from(request, "wire-demo", "/api/wire/demo")
 *         .once("count", 0)
 *         .once("message", "")
 *         .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
 *         .responseWire();
 * }
 *
 * public Response update(Request request) {
 *     return WireService.from(request, "wire-demo", "/api/wire/demo")
 *         .once("count", 0)
 *         .once("message", "")
 *         .once("items", Arrays.asList("苹果", "香蕉", "橙子"))
 *         .action("increment", c -> c.put("count", c.getInt("count") + 1))
 *         .action("decrement", c -> c.put("count", c.getInt("count") - 1))
 *         .action("addItem", c -> {
 *             List<Object> items = c.getList("items");
 *             items.add("项目 " + (items.size() + 1));
 *         })
 *         .responseUpdate();
 * }
 * }</pre>
 */
public class WireService {

    // ===== 上下文状态 =====

    private final String templateName;
    private final String updateUrl;
    private final String action;
    private final Map<String, Object> data;
    private List<String> sections;

    /** action 处理器注册表 */
    private final Map<String, Consumer<WireService>> actionHandlers = new LinkedHashMap<>();

    // ===== 构造 =====

    private WireService(String templateName, String updateUrl, String action,
                        Map<String, Object> data, List<String> sections) {
        this.templateName = templateName;
        this.updateUrl = updateUrl;
        this.action = action != null ? action : "";
        this.data = data != null ? data : new LinkedHashMap<>();
        this.sections = sections != null ? sections : new ArrayList<>();
    }

    /**
     * 第一步：从 HTTP 请求解析 Wire 上下文。
     * <p>
     * 自动解析 snapshot + action + params + sections，合并为 data。
     *
     * @param request      HTTP 请求
     * @param templateName 模板名（用于后续渲染）
     * @param updateUrl    Wire 更新 URL
     * @return WireService 上下文
     */
    public static WireService from(Request request, String templateName, String updateUrl) {
        WireRequest wireReq = WireRequest.from(request);
        Map<String, Object> data = wireReq.getMergedData();
        List<String> sections = wireReq.getSections();
        return new WireService(templateName, updateUrl, wireReq.getAction(), data, sections);
    }

    /**
     * 从已有数据创建上下文（用于初始页面渲染，不需要解析请求）。
     *
     * @param templateName 模板名
     * @param updateUrl    Wire 更新 URL
     * @param data         初始数据
     * @return WireService 上下文
     */
    public static WireService of(String templateName, String updateUrl, Map<String, Object> data) {
        return new WireService(templateName, updateUrl, "", data, new ArrayList<>());
    }

    // ===== 第二步：数据处理 =====

    /**
     * 如果字段不存在则填入默认值（仅一次）。
     * <pre>{@code
     * ctx.once("count", 0)
     *    .once("message", "")
     *    .once("items", Arrays.asList("苹果", "香蕉", "橙子"));
     * }</pre>
     *
     * @param key          字段名
     * @param defaultValue 默认值
     * @return this（链式）
     */
    public WireService once(String key, Object defaultValue) {
        if (!data.containsKey(key)) {
            data.put(key, defaultValue);
        }
        return this;
    }

    /**
     * 注册 action 处理器。当当前请求的 action 匹配时，执行处理器。
     * <p>
     * 内部使用 Map 存储，注册时不执行，调用 {@link #responseUpdate()} 时统一分派。
     *
     * <pre>{@code
     * ctx.action("increment", c -> c.put("count", c.getInt("count") + 1))
     *    .action("addItem", c -> {
     *        List<Object> items = c.getList("items");
     *        items.add("项目 " + (items.size() + 1));
     *    });
     * }</pre>
     *
     * @param actionName action 名称
     * @param handler    处理器（接收 WireService 自身，可直接操作 data）
     * @return this（链式）
     */
    public WireService action(String actionName, Consumer<WireService> handler) {
        actionHandlers.put(actionName, handler);
        return this;
    }

    /**
     * 直接设置字段值（无条件覆盖）。
     *
     * <pre>{@code
     * ctx.set("count", 10);
     * }</pre>
     */
    public WireService set(String key, Object value) {
        data.put(key, value);
        return this;
    }

    /**
     * 更新字段值（函数式，接收当前值，返回新值）。
     * <pre>{@code
     * ctx.update("count", oldVal -> oldVal + 1);
     * }</pre>
     *
     * @param key      字段名
     * @param updater  更新函数（接收当前值，返回新值）
     * @return this（链式）
     */
    public <T> WireService update(String key, Function<T, T> updater) {
        @SuppressWarnings("unchecked")
        T oldVal = (T) data.get(key);
        data.put(key, updater.apply(oldVal));
        return this;
    }

    /**
     * 删除字段。
     * <pre>{@code
     * ctx.remove("message");
     * }</pre>
     */
    public WireService remove(String key) {
        data.remove(key);
        return this;
    }

    // ===== 数据读取 =====

    /**
     * 获取原始 data Map（可直接操作）。
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * 获取字段值。
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 获取字段值，带默认值。
     */
    public Object get(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    /**
     * 获取 int 类型字段值。
     */
    public int getInt(String key) {
        return toInt(data.get(key));
    }

    /**
     * 获取 String 类型字段值。
     */
    public String getStr(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : "";
    }

    /**
     * 获取 List 类型字段值（返回可变 List）。
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object val = data.get(key);
        if (val == null) {
            List<Object> empty = new ArrayList<>();
            data.put(key, empty);
            return empty;
        }
        if (val instanceof List) {
            // 确保可变
            if (!(val instanceof ArrayList)) {
                List<Object> mutable = new ArrayList<>((List<Object>) val);
                data.put(key, mutable);
                return mutable;
            }
            return (List<Object>) val;
        }
        List<Object> wrapped = new ArrayList<>();
        wrapped.add(val);
        data.put(key, wrapped);
        return wrapped;
    }

    /**
     * 获取当前 action 名称。
     */
    public String getAction() {
        return action;
    }

    // ===== 第三步：生成响应 =====

    /**
     * 分派 action 处理器并返回 data Map。
     * <p>
     * 会执行匹配的 action 处理器，然后返回最终的 data。
     */
    public Map<String, Object> toData() {
        dispatchActions();
        return data;
    }

    /**
     * 获取要更新的 section 列表。
     * 如果请求中没有指定 sections，则使用模板的默认 section。
     */
    public List<String> toSections() {
        if (sections == null || sections.isEmpty()) {
            sections = WireManager.getSectionNames(templateName);
        }
        return sections;
    }

    /**
     * 直接生成 Wire 初始页面响应（HTML）。
     * <p>
     * 等同于 {@code WireResponse.wire(templateName, data, updateUrl)}。
     */
    public Response responseWire() {
        return WireResponse.wire(templateName, data, updateUrl);
    }

    /**
     * 分派 action 处理器并生成 Wire 更新响应（JSON）。
     * <p>
     * 等同于 {@code WireResponse.update(templateName, data, sections)}，但自动分派 action。
     */
    public Response responseUpdate() {
        dispatchActions();
        return WireResponse.update(templateName, data, toSections());
    }

    /**
     * 分派 action 处理器并生成全能构建器。
     * <p>
     * 返回 {@link WireResponse} 构建器，可继续链式调用：
     * <pre>{@code
     * return ctx.responseOf()
     *     .withRedirect("/dashboard")
     *     .build();
     * }</pre>
     */
    public WireResponse responseOf() {
        dispatchActions();
        return WireResponse.of(templateName, data, toSections());
    }

    // ===== 内部方法 =====

    /**
     * 分派 action 处理器：找到匹配的 handler 执行，找不到则跳过。
     */
    private void dispatchActions() {
        Consumer<WireService> handler = actionHandlers.get(action);
        if (handler != null) {
            handler.accept(this);
        }
    }

    /**
     * 安全的 int 转换。
     */
    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
