package com.weacsoft.jaravel.vendor.jblade;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BladeTemplate {
    protected BladeContext context;
    private BladeEngine engine;
    private volatile boolean initialized = false;

    public BladeTemplate() {
        this.context = new BladeContext();
    }

    public abstract void init();

    public abstract void render(Writer writer) throws Exception;

    public String render() throws Exception {
        StringWriter writer = new StringWriter();
        render(writer);
        return writer.toString();
    }

    protected void write(Writer writer, String content) throws Exception {
        writer.write(content);
    }

    protected void write(Writer writer, Object content) throws Exception {
        if (content != null) {
            writer.write(content.toString());
        }
    }

    protected boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String s = (String) value;
            // PHP 语义："" 和 "0" 为假
            return !s.isEmpty() && !"0".equals(s);
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0;
        }
        return true;
    }

    public BladeContext getContext() {
        return context;
    }

    public void setContext(BladeContext context) {
        this.context = context;
    }

    public void setEngine(BladeEngine engine) {
        this.engine = engine;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public void resetContext() {
        this.context = new BladeContext();
        this.initialized = false;
    }

    public void resetContext(BladeContext newContext) {
        this.context = newContext;
        this.initialized = false;
    }

    // ===== PHP Helper 方法（对齐 Laravel Blade 模板辅助函数）=====

    /**
     * 生成路由 URL，对齐 PHP route('name')。
     * <p>
     * 优先委托给通过 {@link BladeFunctions} 注册的 "route" 函数
     * （由 http 模块/应用注册，按路由别名反查真实 URI）；
     * 未注册时退化为 "/name"。
     *
     * @param name 路由名称（别名）
     * @return URL 路径
     */
    protected String route(String name) {
        if (BladeFunctions.has("route")) {
            Object r = BladeFunctions.call("route", name);
            return r == null ? "" : r.toString();
        }
        return "/" + name;
    }

    /**
     * 生成带参数的路由 URL，对齐 PHP route('name', ['key' => value])。
     * @param name 路由名称（别名）
     * @param params 查询参数
     * @return 带查询参数的 URL
     */
    protected String route(String name, Map<String, Object> params) {
        if (BladeFunctions.has("route")) {
            Object r = BladeFunctions.call("route", name, params);
            return r == null ? "" : r.toString();
        }
        if (params == null || params.isEmpty()) {
            return "/" + name;
        }
        StringBuilder sb = new StringBuilder("/").append(name).append("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * route() 的宽松入口：第二个参数可为 Map 或标量。
     */
    protected String routeAny(Object name, Object params) {
        String n = name == null ? "" : name.toString();
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) params;
            return route(n, m);
        }
        if (params == null) {
            return route(n);
        }
        if (BladeFunctions.has("route")) {
            Object r = BladeFunctions.call("route", n, params);
            return r == null ? "" : r.toString();
        }
        return "/" + n + "/" + params;
    }

    /**
     * 生成静态资源 URL，对齐 PHP asset('path')。
     * @param path 资源路径
     * @return 完整资源 URL
     */
    protected String asset(String path) {
        if (BladeFunctions.has("asset")) {
            Object r = BladeFunctions.call("asset", path);
            return r == null ? "" : r.toString();
        }
        if (path == null || path.isEmpty()) {
            return "/assets/";
        }
        if (path.startsWith("/")) {
            return "/assets" + path;
        }
        return "/assets/" + path;
    }

    /**
     * 生成 URL，对齐 PHP url('path')。
     * @param path 路径
     * @return URL
     */
    protected String url(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }

    /**
     * 获取 session 值，对齐 PHP session('key')。
     * @param key session 键
     * @return session 值或 null
     */
    protected Object session(String key) {
        return context.getVariable("session_" + key);
    }

    /**
     * 获取旧输入值，对齐 PHP old('key')。
     * Phase 7 迁移完整逻辑后实现。
     * @param key 输入键
     * @return 空字符串（占位）
     */
    protected String old(String key) {
        return "";
    }

    /**
     * CSRF 字段，对齐 PHP csrf_field()。
     * token 来源：BladeFunctions 注册的 "csrf_token" 函数（由应用注册）。
     */
    protected String csrf_field() {
        return "<input type=\"hidden\" name=\"_token\" value=\"" + e(csrf_token()) + "\">";
    }

    /**
     * CSRF token，对齐 PHP csrf_token()。
     * 优先使用 BladeFunctions 注册的 "csrf_token" 函数，其次取变量 _token。
     */
    protected String csrf_token() {
        Object token = BladeFunctions.callOrDefault("csrf_token", null);
        if (token == null) {
            token = context.getVariable("_token");
        }
        return token == null ? "" : token.toString();
    }

    /**
     * 获取对象属性，对齐 PHP $var->property。
     * 使用反射尝试 getter 方法，再尝试字段访问。
     * @param obj 目标对象
     * @param name 属性名
     * @return 属性值或 null
     */
    protected Object getProperty(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        // $loop 属性快速通道
        if (obj instanceof LoopHelper) {
            return ((LoopHelper) obj).prop(name);
        }
        // Map 优先按键访问
        if (obj instanceof Map && ((Map<?, ?>) obj).containsKey(name)) {
            return ((Map<?, ?>) obj).get(name);
        }
        // 尝试 getter 方法
        try {
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return obj.getClass().getMethod(getter).invoke(obj);
        } catch (Exception e) {
            // 尝试 isXxx 方法
            try {
                String isser = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                return obj.getClass().getMethod(isser).invoke(obj);
            } catch (Exception e2) {
                // 尝试直接字段访问
                try {
                    return obj.getClass().getField(name).get(obj);
                } catch (Exception e3) {
                    // 如果是 Map，尝试 get
                    if (obj instanceof Map) {
                        return ((Map<?, ?>) obj).get(name);
                    }
                    return null;
                }
            }
        }
    }

    /**
     * 获取 Map 值，对齐 PHP $var['key']。
     * @param obj 目标对象（应为 Map）
     * @param key 键名
     * @return 值或 null
     */
    protected Object getMapValue(Object obj, String key) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(key);
        }
        // 尝试反射属性访问
        return getProperty(obj, key);
    }

    /**
     * 调用对象方法，对齐 PHP $var->method() 和 $var->method(args)。
     * @param obj 目标对象
     * @param method 方法名
     * @param args 方法参数
     * @return 方法返回值
     */
    protected Object invokeMethod(Object obj, String method, Object... args) {
        if (obj == null) {
            return null;
        }
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            java.lang.reflect.Method m = findMethod(obj.getClass(), method, paramTypes);
            if (m == null && args.length == 0) {
                m = findMethod(obj.getClass(), method, new Class<?>[0]);
            }
            if (m == null) {
                // 尝试 Object 参数类型
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = Object.class;
                }
                m = findMethod(obj.getClass(), method, paramTypes);
            }
            if (m == null) {
                return null;
            }
            return m.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 在类及其所有接口（包括父类接口）中查找方法。
     * 优先在实现的接口中查找（接口方法不受模块 open 限制），
     * 然后在类自身和父类中查找。
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        if (clazz == null) {
            return null;
        }
        // 1. 先在实现的接口中查找（接口方法可以跨模块调用，不受模块 open 限制）
        for (Class<?> iface : getAllInterfaces(clazz)) {
            try {
                java.lang.reflect.Method m = iface.getMethod(methodName, paramTypes);
                if (m != null) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        // 2. 在类自身查找
        try {
            return clazz.getDeclaredMethod(methodName, paramTypes);
        } catch (NoSuchMethodException ignored) {
        }
        // 3. 在父类中查找
        return findMethod(clazz.getSuperclass(), methodName, paramTypes);
    }

    /**
     * 获取类实现的所有接口（包括父类实现的接口）。
     */
    private java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        java.util.Set<Class<?>> interfaces = new java.util.LinkedHashSet<>();
        while (clazz != null && clazz != Object.class) {
            collectInterfaces(clazz, interfaces);
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    private void collectInterfaces(Class<?> clazz, java.util.Set<Class<?>> result) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (result.add(iface)) {
                collectInterfaces(iface, result);
            }
        }
    }

    /**
     * Elvis 运算符，对齐 PHP $a ?: $b。
     * @param a 第一个值
     * @param b 默认值
     * @return a 为真时返回 a，否则返回 b
     */
    protected Object elvis(Object a, Object b) {
        return toBoolean(a) ? a : b;
    }

    /**
     * 空合并运算符，对齐 PHP $a ?? $b。
     * @param a 第一个值
     * @param b 默认值
     * @return a 非 null 时返回 a，否则返回 b
     */
    protected Object nullCoalesce(Object a, Object b) {
        return a != null ? a : b;
    }

    /**
     * 字符串拼接，对齐 PHP . 运算符。
     * @param parts 各部分
     * @return 拼接后的字符串
     */
    protected String concat(Object... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part != null) {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    // ===== PHP 内置函数支持 =====

    /**
     * 空值检查，对齐 PHP empty($var)。
     * @param obj 检查对象
     * @return true 如果为 null、空字符串、空集合、0 等
     */
    protected boolean empty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return ((String) obj).isEmpty();
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue() == 0;
        }
        if (obj instanceof Boolean) {
            return !(Boolean) obj;
        }
        return false;
    }

    /**
     * 转整数，对齐 PHP intval($var)。
     * @param obj 输入对象
     * @return 整数值
     */
    protected int intval(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            try {
                return Integer.parseInt(((String) obj).trim());
            } catch (NumberFormatException e) {
                try {
                    return (int) Double.parseDouble(((String) obj).trim());
                } catch (NumberFormatException e2) {
                    return 0;
                }
            }
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? 1 : 0;
        }
        return 0;
    }

    /**
     * JSON 编码，对齐 PHP json_encode($var)。
     * @param obj 编码对象
     * @return JSON 字符串
     */
    protected String json_encode(Object obj) {
        return toJson(obj);
    }

    /**
     * JSON 编码（忽略第二个参数），对齐 PHP json_encode($var, JSON_UNESCAPED_SLASHES)。
     * @param obj 编码对象
     * @param flags 忽略
     * @return JSON 字符串
     */
    protected String json_encode(Object obj, Object flags) {
        return toJson(obj);
    }

    /**
     * JSON 序列化。
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + ((String) obj).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":").append(toJson(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJson(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        return String.valueOf(obj);
    }

    /**
     * 计数，对齐 PHP count($var)。
     * @param obj 集合或数组
     * @return 元素数量
     */
    protected int count(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).size();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).size();
        }
        if (obj instanceof Object[]) {
            return ((Object[]) obj).length;
        }
        return 1;
    }

    /**
     * 格式化字符串，对齐 PHP sprintf("%.2f", $var)。
     * @param format 格式字符串
     * @param args 参数
     * @return 格式化后的字符串
     */
    protected String sprintf(String format, Object... args) {
        if (format == null) {
            return "";
        }
        return String.format(format, args);
    }

    /**
     * 字符串替换，对齐 PHP str_replace($search, $replace, $subject)。
     */
    protected String str_replace(String search, String replace, String subject) {
        if (search == null || subject == null) {
            return subject;
        }
        return subject.replace(search, replace != null ? replace : "");
    }

    /**
     * 数组连接，对齐 PHP implode($glue, $pieces)。
     * @param glue 分隔符
     * @param obj 集合或数组
     * @return 连接后的字符串
     */
    protected String implode(String glue, Object obj) {
        if (obj == null) {
            return "";
        }
        if (glue == null) {
            glue = "";
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) {
                    sb.append(glue);
                }
                sb.append(item);
                first = false;
            }
            return sb.toString();
        }
        if (obj instanceof Object[]) {
            return String.join(glue, Arrays.stream((Object[]) obj).map(String::valueOf).toArray(String[]::new));
        }
        return String.valueOf(obj);
    }

    /**
     * 向上取整，对齐 PHP ceil($var)。
     */
    protected double ceil(double val) {
        return Math.ceil(val);
    }

    /**
     * 向下取整，对齐 PHP floor($var)。
     */
    protected double floor(double val) {
        return Math.floor(val);
    }

    /**
     * 空合并运算符，对齐 PHP $a ?? $b。
     * @param a 第一个值
     * @param b 默认值
     * @return a 不为 null 时返回 a，否则返回 b
     */
    protected Object nullCoalescing(Object a, Object b) {
        return a != null ? a : b;
    }

    /**
     * Carbon 日期解析，对齐 PHP Carbon::parse($date)。
     * @param date 日期字符串
     * @return 日期对象
     */
    protected java.time.LocalDateTime carbonParse(Object date) {
        if (date == null) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(date.toString());
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(date.toString()).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Carbon 当前日期，对齐 PHP Carbon::today()。
     * @return 当前日期
     */
    protected java.time.LocalDate carbonToday() {
        return java.time.LocalDate.now();
    }

    /**
     * 获取年份，对齐 PHP Carbon::today()->year。
     * @param date 日期对象
     * @return 年份
     */
    protected int carbonYear(Object date) {
        if (date instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) date).getYear();
        }
        if (date instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) date).getYear();
        }
        return java.time.LocalDate.now().getYear();
    }

    protected void renderComponent(Writer writer, String componentName, Map<String, Object> data, Map<String, String> slots) throws Exception {
        if (engine == null) {
            throw new IllegalStateException("BladeEngine not set for template");
        }

        String prevComponent = context.getCurrentComponent();
        Map<String, String> prevSlots = new ConcurrentHashMap<>(context.getComponentSlots());

        context.startComponent(componentName);

        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                context.setComponentData(entry.getKey(), entry.getValue());
            }
        }

        if (slots != null) {
            for (Map.Entry<String, String> entry : slots.entrySet()) {
                context.getComponentSlots().put(entry.getKey(), entry.getValue());
            }
        }

        BladeTemplate componentTemplate = engine.loadTemplate(componentName);
        // 使用全新 context，避免共享模板实例导致的变量泄漏
        componentTemplate.resetContext();
        componentTemplate.setEngine(engine);
        BladeContext componentCtx = componentTemplate.getContext();

        // 组件可见：外层变量（Laravel 行为：组件视图共享环境数据）+ 显式传入的数据 + 插槽
        for (Map.Entry<String, Object> varEntry : context.getVariables().entrySet()) {
            componentCtx.setVariable(varEntry.getKey(), varEntry.getValue());
        }
        String defaultSlot = context.getSlot("default");
        componentCtx.setVariable("slot", defaultSlot);
        componentCtx.setVariable("$slot", defaultSlot); // 兼容旧版编译产物
        for (Map.Entry<String, String> slotEntry : context.getComponentSlots().entrySet()) {
            componentCtx.setVariable(slotEntry.getKey(), slotEntry.getValue());
            componentCtx.setVariable("$" + slotEntry.getKey(), slotEntry.getValue());
        }
        for (Map.Entry<String, Object> dataEntry : context.getComponentData().entrySet()) {
            componentCtx.setVariable(dataEntry.getKey(), dataEntry.getValue());
        }

        componentTemplate.init();
        componentTemplate.setInitialized(true);
        componentTemplate.render(writer);

        context.endComponent();
        context.getComponentSlots().clear();
        context.getComponentSlots().putAll(prevSlots);
        context.clearComponentData();
    }

    // ===================================================================
    // ===== 新一代运行时辅助（供重写后的 BladeCompiler 生成代码调用）=====
    // ===================================================================

    /**
     * 读取模板变量（$xxx）。"loop" 特殊映射为当前 $loop。
     */
    protected Object v(String name) {
        if ("loop".equals(name)) {
            LoopHelper loop = context.currentLoop();
            if (loop != null) {
                return loop;
            }
        }
        Object val = context.getVariable(name);
        if (val == null && name.length() > 0) {
            // 兼容旧组件插槽命名（"$slot"）
            val = context.getVariable("$" + name);
        }
        return val;
    }

    /**
     * 设置模板变量（@php($x = ...) / @foreach 循环变量）。
     */
    protected Object setVar(String name, Object value) {
        context.setVariable(name, value);
        return value;
    }

    /**
     * HTML 转义（对齐 Laravel e() / {{ }}）。
     */
    protected String e(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#039;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 转义输出（{{ }}）。
     */
    protected void echo(Writer writer, Object value) throws Exception {
        if (value != null) {
            writer.write(e(value));
        }
    }

    /**
     * 原样输出（{!! !!}）。
     */
    protected void echoRaw(Writer writer, Object value) throws Exception {
        if (value != null) {
            writer.write(value.toString());
        }
    }

    /**
     * 注册 section（子模板优先 + @parent 占位符合并，支持多重继承），
     * 同时注册 section 渲染器供 wire 局部渲染使用。
     */
    protected void registerSection(String name, String content) {
        context.extendSection(name, content);
        context.setSectionRenderer(name, w -> {
            try {
                String c = context.yieldSection(name);
                if (c != null) {
                    w.write(c);
                }
            } catch (Exception ex) {
                throw new RuntimeException("渲染 section 失败: " + name, ex);
            }
        });
    }

    /**
     * @yield：输出 section 内容或默认值。
     * 仅当 __wire_mode 为“真值”时输出 wire 分段标记
     * （修复：以前使用 != null 判断，传入 false 也会输出标记）。
     */
    protected void yieldSection(Writer writer, String name, Object defaultValue) throws Exception {
        boolean wireMode = toBoolean(context.getVariable("__wire_mode"));
        if (wireMode) {
            writer.write("<!--wire:section-start:" + name + "-->");
        }
        String content = context.yieldSection(name);
        if (content != null) {
            writer.write(content);
        } else if (defaultValue != null) {
            writer.write(String.valueOf(defaultValue));
        }
        if (wireMode) {
            writer.write("<!--wire:section-end:" + name + "-->");
        }
    }

    /**
     * @hasSection
     */
    protected boolean hasSection(String name) {
        return context.getSection(name) != null;
    }

    /**
     * @sectionMissing
     */
    protected boolean sectionMissing(String name) {
        return context.getSection(name) == null;
    }

    /**
     * @include / @includeIf / @includeWhen：渲染子视图（共享当前变量 + 附加数据）。
     */
    protected void includeTemplate(Writer writer, String name, Map<String, Object> data) throws Exception {
        if (engine == null) {
            throw new IllegalStateException("BladeEngine not set for template");
        }
        Map<String, Object> merged = new java.util.HashMap<>(context.getVariables());
        if (data != null) {
            merged.putAll(data);
        }
        // 子视图独立渲染（含其自身的继承链）
        writer.write(engine.render(name, merged));
    }

    /**
     * @includeIf：模板存在才渲染。
     */
    protected void includeTemplateIf(Writer writer, String name, Map<String, Object> data) throws Exception {
        if (engine == null) {
            return;
        }
        try {
            if (!engine.templateExists(name)) {
                return;
            }
        } catch (Exception ignore) {
            return;
        }
        includeTemplate(writer, name, data);
    }

    /**
     * 调用动态注册的函数（BladeFunctions）。
     */
    protected Object fn(String name, Object... args) {
        return BladeFunctions.call(name, args);
    }

    /**
     * 自定义输出指令求值。
     */
    protected Object evalDirective(String name, Object... args) {
        return BladeDirectives.evaluateDirective(name, args);
    }

    /**
     * 自定义条件指令求值（Blade::if 语义）。
     */
    protected boolean evalCondition(String name, Object... args) {
        return BladeDirectives.evaluateCondition(name, args);
    }

    /**
     * @csrf：输出隐藏域。token 来源于注册的 csrf_token 函数。
     */
    protected String csrf() {
        return "<input type=\"hidden\" name=\"_token\" value=\"" + e(csrf_token()) + "\">";
    }

    /**
     * @method('PUT')：HTTP 方法伪造隐藏域。
     */
    protected String methodField(Object method) {
        return "<input type=\"hidden\" name=\"_method\" value=\"" + e(method) + "\">";
    }

    /* ==================== foreach / $loop 支持 ==================== */

    /**
     * 将任意可迭代对象统一为 [key, value] 对列表。
     * Map → 键值对；List/数组/Iterable → 索引 + 元素；null → 空。
     */
    protected java.util.List<Object[]> toPairs(Object obj) {
        java.util.List<Object[]> pairs = new java.util.ArrayList<>();
        if (obj == null) {
            return pairs;
        }
        if (obj instanceof Map) {
            for (Map.Entry<?, ?> en : ((Map<?, ?>) obj).entrySet()) {
                pairs.add(new Object[]{en.getKey(), en.getValue()});
            }
            return pairs;
        }
        if (obj instanceof Iterable) {
            int i = 0;
            for (Object item : (Iterable<?>) obj) {
                pairs.add(new Object[]{i++, item});
            }
            return pairs;
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                pairs.add(new Object[]{i, java.lang.reflect.Array.get(obj, i)});
            }
            return pairs;
        }
        // 分页器等实现了 iterator()/getItems() 的对象
        Object items = invokeMethod(obj, "getItems");
        if (items instanceof Iterable || (items != null && items.getClass().isArray()) || items instanceof Map) {
            return toPairs(items);
        }
        pairs.add(new Object[]{0, obj});
        return pairs;
    }

    /* ==================== 数据访问 ==================== */

    /**
     * 统一下标访问：$arr['key'] / $arr[0]。
     */
    protected Object arrGet(Object obj, Object key) {
        if (obj == null || key == null) {
            return null;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.containsKey(key)) {
                return map.get(key);
            }
            // 数字/字符串键宽松匹配
            Object byString = map.get(key.toString());
            if (byString != null) {
                return byString;
            }
            if (key instanceof Number) {
                return map.get(((Number) key).intValue());
            }
            try {
                return map.get(Integer.parseInt(key.toString()));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        int idx = -1;
        if (key instanceof Number) {
            idx = ((Number) key).intValue();
        } else {
            try {
                idx = Integer.parseInt(key.toString());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            return idx >= 0 && idx < list.size() ? list.get(idx) : null;
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            return idx >= 0 && idx < len ? java.lang.reflect.Array.get(obj, idx) : null;
        }
        return null;
    }

    /* ==================== PHP 风格运算 ==================== */

    protected java.math.BigDecimal toNumber(Object v) {
        if (v == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (v instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) v;
        }
        if (v instanceof Number) {
            return new java.math.BigDecimal(v.toString());
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    /** 若为整数值则化简为 Long，否则 Double（输出更自然） */
    private Object simplify(java.math.BigDecimal d) {
        if (d.stripTrailingZeros().scale() <= 0) {
            return d.longValueExact();
        }
        return d.doubleValue();
    }

    protected Object plus(Object a, Object b) {
        return simplify(toNumber(a).add(toNumber(b)));
    }

    protected Object minus(Object a, Object b) {
        return simplify(toNumber(a).subtract(toNumber(b)));
    }

    protected Object mul(Object a, Object b) {
        return simplify(toNumber(a).multiply(toNumber(b)));
    }

    protected Object div(Object a, Object b) {
        java.math.BigDecimal divisor = toNumber(b);
        if (divisor.signum() == 0) {
            return 0;
        }
        return simplify(toNumber(a).divide(divisor, 10, java.math.RoundingMode.HALF_UP).stripTrailingZeros());
    }

    protected Object mod(Object a, Object b) {
        java.math.BigDecimal divisor = toNumber(b);
        if (divisor.signum() == 0) {
            return 0;
        }
        return simplify(toNumber(a).remainder(divisor));
    }

    protected Object neg(Object a) {
        return simplify(toNumber(a).negate());
    }

    private boolean bothNumeric(Object a, Object b) {
        return (a instanceof Number || b instanceof Number)
                || (isNumericString(a) && isNumericString(b));
    }

    private boolean isNumericString(Object o) {
        if (o instanceof Number) {
            return true;
        }
        if (!(o instanceof String)) {
            return false;
        }
        try {
            new java.math.BigDecimal(((String) o).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** PHP 宽松相等（==） */
    protected boolean looseEquals(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            // PHP8: null == '' 为 true；null == 0 为 true（宽松处理）
            Object other = a == null ? b : a;
            if (other instanceof String) {
                return ((String) other).isEmpty();
            }
            if (other instanceof Number) {
                return toNumber(other).signum() == 0;
            }
            if (other instanceof Boolean) {
                return !((Boolean) other);
            }
            return false;
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            return toBoolean(a) == toBoolean(b);
        }
        if (bothNumeric(a, b)) {
            return toNumber(a).compareTo(toNumber(b)) == 0;
        }
        return a.equals(b) || a.toString().equals(b.toString());
    }

    protected boolean eq(Object a, Object b) {
        return looseEquals(a, b);
    }

    protected boolean neq(Object a, Object b) {
        return !looseEquals(a, b);
    }

    /** 严格相等（===） */
    protected boolean identical(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number && b instanceof Number) {
            boolean aInt = a instanceof Integer || a instanceof Long || a instanceof Short || a instanceof Byte;
            boolean bInt = b instanceof Integer || b instanceof Long || b instanceof Short || b instanceof Byte;
            if (aInt != bInt) {
                return false;
            }
            return toNumber(a).compareTo(toNumber(b)) == 0;
        }
        return a.getClass() == b.getClass() && a.equals(b);
    }

    protected boolean notIdentical(Object a, Object b) {
        return !identical(a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected int cmp(Object a, Object b) {
        if (bothNumeric(a, b)) {
            return toNumber(a).compareTo(toNumber(b));
        }
        if (a instanceof Comparable && b != null && a.getClass().isInstance(b)) {
            return ((Comparable) a).compareTo(b);
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    protected boolean gt(Object a, Object b) {
        return cmp(a, b) > 0;
    }

    protected boolean gte(Object a, Object b) {
        return cmp(a, b) >= 0;
    }

    protected boolean lt(Object a, Object b) {
        return cmp(a, b) < 0;
    }

    protected boolean lte(Object a, Object b) {
        return cmp(a, b) <= 0;
    }

    /* ==================== 常用 PHP 函数补充 ==================== */

    /**
     * number_format($num, $decimals = 0)
     */
    protected String number_format(Object num, Object... decimals) {
        int dec = decimals != null && decimals.length > 0 ? intval(decimals[0]) : 0;
        java.math.BigDecimal d = toNumber(num).setScale(dec, java.math.RoundingMode.HALF_UP);
        java.text.DecimalFormat df = new java.text.DecimalFormat();
        df.setGroupingSize(3);
        df.setMinimumFractionDigits(dec);
        df.setMaximumFractionDigits(dec);
        return df.format(d);
    }

    protected String strtoupper(Object s) {
        return s == null ? "" : s.toString().toUpperCase();
    }

    protected String strtolower(Object s) {
        return s == null ? "" : s.toString().toLowerCase();
    }

    protected String ucfirst(Object s) {
        if (s == null) {
            return "";
        }
        String str = s.toString();
        return str.isEmpty() ? str : Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    protected String trim(Object s) {
        return s == null ? "" : s.toString().trim();
    }

    protected int strlen(Object s) {
        return s == null ? 0 : s.toString().length();
    }

    protected String substr(Object s, Object start, Object... len) {
        if (s == null) {
            return "";
        }
        String str = s.toString();
        int st = intval(start);
        if (st < 0) {
            st = Math.max(0, str.length() + st);
        }
        if (st >= str.length()) {
            return "";
        }
        int end = str.length();
        if (len != null && len.length > 0) {
            int l = intval(len[0]);
            end = l < 0 ? Math.max(st, str.length() + l) : Math.min(str.length(), st + l);
        }
        return str.substring(st, end);
    }

    protected boolean isset(Object v) {
        return v != null;
    }

    /**
     * old($key, $default)
     */
    protected Object old(String key, Object defaultValue) {
        Object val = BladeFunctions.callOrDefault("old", null, key);
        if (val == null) {
            val = session(key) == null ? null : session(key);
        }
        return val != null ? val : defaultValue;
    }

    /**
     * 构造有序 Map（数组字面量 ['a' => 1, ...]）。
     */
    protected Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /**
     * 构造 List（数组字面量 [1, 2, 3]）。
     */
    protected java.util.List<Object> list(Object... items) {
        return new java.util.ArrayList<>(Arrays.asList(items));
    }
}