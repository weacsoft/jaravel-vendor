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
            return !((String) value).isEmpty();
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
     * @param name 路由名称
     * @return URL 路径
     */
    protected String route(String name) {
        return "/" + name;
    }

    /**
     * 生成带参数的路由 URL，对齐 PHP route('name', ['key' => value])。
     * @param name 路由名称
     * @param params 查询参数
     * @return 带查询参数的 URL
     */
    protected String route(String name, Map<String, Object> params) {
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
     * 生成静态资源 URL，对齐 PHP asset('path')。
     * @param path 资源路径
     * @return 完整资源 URL
     */
    protected String asset(String path) {
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
     * @return 空字符串（占位）
     */
    protected String csrf_field() {
        return "";
    }

    /**
     * CSRF token，对齐 PHP csrf_token()。
     * @return 空字符串（占位）
     */
    protected String csrf_token() {
        return "";
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
            // 尝试根据参数类型查找方法
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            // 先尝试精确匹配
            try {
                return obj.getClass().getMethod(method, paramTypes).invoke(obj, args);
            } catch (NoSuchMethodException e) {
                // 尝试无参方法
                if (args.length == 0) {
                    return obj.getClass().getMethod(method).invoke(obj);
                }
                // 尝试 Object 参数类型
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = Object.class;
                }
                return obj.getClass().getMethod(method, paramTypes).invoke(obj, args);
            }
        } catch (Exception e) {
            return null;
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
        BladeContext componentCtx = componentTemplate.getContext();

        componentCtx.setVariable("$slot", context.getSlot("default"));
        for (Map.Entry<String, String> slotEntry : context.getComponentSlots().entrySet()) {
            componentCtx.setVariable("$" + slotEntry.getKey(), slotEntry.getValue());
        }
        for (Map.Entry<String, Object> dataEntry : context.getComponentData().entrySet()) {
            componentCtx.setVariable(dataEntry.getKey(), dataEntry.getValue());
        }

        componentTemplate.render(writer);

        context.endComponent();
        context.getComponentSlots().clear();
        context.getComponentSlots().putAll(prevSlots);
        context.clearComponentData();
    }
}