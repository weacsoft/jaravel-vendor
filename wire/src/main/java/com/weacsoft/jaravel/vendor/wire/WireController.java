package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
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
 *   <li>{@code protected abstract WireView render()} —— 返回模板和布局配置</li>
 * </ul>
 * <p>
 * 用户可选实现的:
 * <ul>
 *   <li>{@code protected void mount(Map<String,String> params)} —— 初始化(仅首次 index)调用</li>
 *   <li>{@code protected void fill(Map<String,Object> data)} —— 批量赋值 public 属性</li>
 *   <li>{@code protected String getLayout()} —— 直访场景的父模板</li>
 *   <li>{@code protected String getWireLayout()} —— wire 场景的父模板(Dialog 等)</li>
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
            "getLayout", "getWireLayout", "getTemplateName",
            "wire", "getPublicFields", "getLockedFields", "collectPublicFields",
            "invokeAction", "isWireRequest", "buildUpdateUrl",
            "renderPage", "renderSections", "renderWireSections",
            "getSessionKey", "encodeSignedSnapshot", "decodeSignedSnapshot", "hmac"
    ));

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
     * 参数为 GET query 参数。子类可在其中通过 {@code fill(...)} 批量赋值。
     *
     * @param params GET query 参数
     */
    protected void mount(Map<String, Object> params) {
    }

    /**
     * 批量赋值 public 属性,参考 BaseModel.fill 的语义。
     * <p>
     * 默认可选实现,基于反射遍历 public 字段并调用 setter。
     *
     * @param data 要赋值的键值对(键为字段名)
     */
    protected void fill(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;
        Set<String> locked = getLockedFields();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty() || locked.contains(key)) continue;
            Object value = entry.getValue();
            if (value == null) continue;
            try {
                Field f = findPublicField(this.getClass(), key);
                if (f == null) continue;
                f.setAccessible(true);
                // 类型转换:全部先转 String
                String strVal = value.toString();
                Class<?> type = f.getType();
                Object converted = convertValue(strVal, type);
                if (converted != null) {
                    f.set(this, converted);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 直访场景的父模板。默认返回 null(表示不套布局)。
     * 子类可覆盖返回如 "layouts.mdui.form"。
     */
    protected String getLayout() {
        return null;
    }

    /**
     * wire 场景的父模板。默认同 getLayout()。
     * 子类可覆盖返回如 "layouts.mdui.form.dialog"。
     */
    protected String getWireLayout() {
        return getLayout();
    }

    /**
     * 返回当前 WireController 对应的模板名。
     * <p>
     * 默认从 render().getTemplateName() 获取,子类可覆盖。
     */
    protected String getTemplateName() {
        return render().getTemplateName();
    }

    /* ============ 框架默认实现(对接路由) ============ */

    /**
     * GET 请求：首屏渲染。
     * <p>
     * 流程:mount → collectPublicFields → fill → render → 判断 wire/直访 → 套布局 → 注入 wire assets。
     */
    public Response index(Request request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            // mount（仅首次加载时调用，重建嵌套对象如 Admin setting）
            mount(request.query());
            // 收集 public 属性
            collectPublicFields(data);
            // fill（将 data 中的值赋给 public 字段）
            fill(data);
            // render
            WireView view = render();
            Map<String, Object> renderData = view.getMergedData(data);
            // 判断 wire 场景
            String parentTemplate = isWireRequest(request) ? getWireLayout() : getLayout();
            String templateName = view.getTemplateName();

            // 注册父模板覆盖
            if (parentTemplate != null && !parentTemplate.equals(view.getExtendsTemplate())) {
                WireParentOverride.register(templateName, parentTemplate);
            }
            try {
                // 渲染页面
                String html = renderPage(templateName, parentTemplate, renderData);
                // 编码快照(签名)
                String snapshot = encodeSignedSnapshot(data, request);
                // 构建 update URL
                String updateUrl = buildUpdateUrl(request);
                // 注入 wire assets
                html = WireManager.injectWireAssets(html, updateUrl, snapshot);
                return ResponseBuilder.html(html);
            } finally {
                WireParentOverride.clear();
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

            // 调用 mount() 重建嵌套对象(如 Admin setting 从 Map→真实对象)
            // 注意：update 时的 mount 只重建对象结构，不重新从数据库加载
            // 子类应覆盖 refresh() 来重新从数据库加载最新数据
            mount(data);

            // 反射调用 action 方法(所有参数视为 String)
            if (action != null && !action.isEmpty()) {
                invokeAction(action, params);
            }

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

            // 取走临时组件,并渲染其 HTML
            List<Map<String, Object>> components = renderComponents(WireEffects.drain());

            // 构建响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sections", sectionHtmls);
            result.put("snapshot", newSnapshot);
            Map<String, Object> effects = new LinkedHashMap<>();
            if (!components.isEmpty()) {
                effects.put("components", components);
            }
            result.put("effects", effects);

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
        }
    }

    /* ============ 便利方法 ============ */

    /**
     * 创建一个 WireView 对象,用于 render() 返回。
     */
    protected WireView wireView(String templateName) {
        return new WireView(templateName);
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
        if (action.startsWith("$")) {
            log.warn("未知 magic action: " + action);
            return;
        }

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
                args[i] = val != null ? val.toString() : null;
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

    protected String buildUpdateUrl(Request request) {
        try {
            return request.uri();
        } catch (Exception e) {
            return "/wire/update";
        }
    }

    private String renderPage(String templateName, String parentTemplate, Map<String, Object> data) {
        if (parentTemplate != null) {
            // 通过注册 WireParentOverride 已由 BladeEngine 自动处理
        }
        return WireManager.renderForWire(templateName, data);
    }

    private Map<String, String> renderSections(String templateName, List<String> sections, Map<String, Object> data) {
        return WireManager.renderSections(templateName, sections, data);
    }

    /* ============ 快照 HMAC 签名 ============ */

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SESSION_KEY_NAME = "wire_key";

    private String encodeSignedSnapshot(Map<String, Object> data, Request request) {
        String snapshot = WireManager.encodeSnapshot(data);
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
     */
    private List<Map<String, Object>> renderComponents(List<Map<String, Object>> rawComponents) {
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
                String html = WireManager.renderForWire(templateName, params);
                // 保证单一根元素
                html = ensureSingleRoot(html);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("params", params);
                entry.put("html", html);
                entry.put("id", "wc-" + name + "-" + System.nanoTime());
                rendered.add(entry);
            } catch (Exception e) {
                log.error("渲染组件失败: " + name, e);
            }
        }
        return rendered;
    }

    /**
     * 从 WireProperties.components 注册表中查找组件模板名。
     * 通过反射调用以解耦依赖。
     */
    private String resolveComponentTemplate(String name) {
        try {
            Class<?> props = Class.forName("com.weacsoft.jaravel.vendor.wire.springboot.WireProperties");
            java.lang.reflect.Field compField = props.getDeclaredField("components");
            compField.setAccessible(true);
            // 组件注册表可能是类静态字段或通过自动装配初始化;这里简化为检查 WireManager 是否有对应方法
            Class<?> wm = Class.forName("com.weacsoft.jaravel.vendor.wire.WireManager");
            try {
                java.lang.reflect.Method m = wm.getMethod("resolveComponentTemplate", String.class);
                Object result = m.invoke(null, name);
                return result != null ? (String) result : null;
            } catch (NoSuchMethodException e) {
                // 回退:尝试从类路径直接渲染 name 对应的模板
                return name;
            }
        } catch (Exception ignored) {
            return name;
        }
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