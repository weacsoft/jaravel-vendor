package com.weacsoft.jaravel.vendor.wire.component;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.json.Json;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wire 命名组件的<b>加载位置</b>中间件，机制与 {@code VerifyCsrfToken} 完全对齐。
 *
 * <h3>它解决什么问题</h3>
 * 命名组件（toast/confirm 等）需要一个稳定的 DOM 挂载点（outlet）与一份前端运行时。
 * 本中间件负责在页面响应里<b>自动补齐</b>这两样东西，业务模板与控制器零改动。
 *
 * <h3>开箱即用</h3>
 * 框架启动时以别名 {@code "WireOutlet"} 注册，应用只需在 Web 路由组末尾引用：
 * <pre>{@code
 * Route.group(Map.of(
 *         Route.Group.MIDDLEWARE, new String[]{"VerifyCsrfToken", "WireOutlet"}
 * ), Web::register);
 * }</pre>
 *
 * <h3>加载位置怎么自定义</h3>
 * <ol>
 *   <li><b>默认</b>：不写任何东西，容器自动插入 {@code </body>} 之前；</li>
 *   <li><b>指定位置</b>：在模板任意位置写 <code>{!! wire_outlet() !!}</code>，
 *       中间件检测到已有 {@code wire:outlet} 就不再重复注入；</li>
 *   <li><b>全局位置</b>：配置 {@code jaravel.wire.outlet.position=body-start|body-end}；</li>
 *   <li><b>例外</b>：配置 {@code jaravel.wire.outlet.except} 排除路径，
 *       支持精确匹配 {@code /login} 与前缀通配 {@code /admin/*}。</li>
 * </ol>
 *
 * <h3>与 CSRF 一致的「启用标记」语义</h3>
 * 只有 {@link #handle} 被调用（即中间件确实挂在当前路由上）时才会写入
 * {@link #OUTLET_ENABLED_MARKER}；未启用时模板函数 {@code wire_outlet()} 返回空字符串，
 * 等同该指令不存在，不会留下孤立的空容器。
 */
public class WireOutlet implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(WireOutlet.class);

    /** 请求属性标记：仅当中间件已应用于当前路由时为 {@code TRUE}。 */
    public static final String OUTLET_ENABLED_MARKER = "__jaravel_wire_outlet_enabled";

    /** 位置：容器插入 {@code </body>} 之前（默认）。 */
    public static final String POSITION_BODY_END = "body-end";

    /** 位置：容器插入 {@code <body>} 之后。 */
    public static final String POSITION_BODY_START = "body-start";

    // ===== 全局配置（由自动装配写入，运行期只读） =====

    private static final List<String> except = new CopyOnWriteArrayList<>();
    private static volatile String outletId = "wire-outlet";
    private static volatile String position = POSITION_BODY_END;
    private static volatile boolean autoInjectJs = true;
    private static volatile String jsPath = "/static/wire-component.js";

    public static void setExcept(List<String> patterns) {
        except.clear();
        if (patterns != null) {
            for (String p : patterns) {
                if (p != null && !p.trim().isEmpty()) {
                    except.add(p.trim());
                }
            }
        }
    }

    public static List<String> getExcept() {
        return new ArrayList<>(except);
    }

    public static void setOutletId(String id) {
        outletId = (id != null && !id.trim().isEmpty()) ? id.trim() : "wire-outlet";
    }

    public static String getOutletId() {
        return outletId;
    }

    public static void setPosition(String value) {
        position = POSITION_BODY_START.equalsIgnoreCase(value) ? POSITION_BODY_START : POSITION_BODY_END;
    }

    public static String getPosition() {
        return position;
    }

    public static void setAutoInjectJs(boolean flag) {
        autoInjectJs = flag;
    }

    public static boolean isAutoInjectJs() {
        return autoInjectJs;
    }

    public static void setJsPath(String path) {
        jsPath = (path != null && !path.trim().isEmpty()) ? path.trim() : "/static/wire-component.js";
    }

    public static String getJsPath() {
        return jsPath;
    }

    // ===== 中间件主体 =====

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        if (isExcluded(request)) {
            // 例外路径：完全不介入，既不写标记也不改响应
            return next.apply(request);
        }

        request.setAttribute(OUTLET_ENABLED_MARKER, Boolean.TRUE);
        try {
            Response response = next.apply(request);

            // 控制器此时已执行完毕，取走它压入的组件。
            // 注意必须在这里"急切"取走：响应体是惰性渲染的，等到写出时本请求的
            // ThreadLocal 可能已被 finally 清理，届时再取就为空了。
            List<Map<String, Object>> pending = WireComponents.drain();

            return new OutletResponse(response, pending);
        } finally {
            // 兜底清理：无论响应是否 HTML、是否抛异常，都不把队列留给下一个复用本线程的请求
            WireComponents.clearPending();
        }
    }

    /**
     * 不注入加载位置的 URI 列表，子类可覆盖以自定义（等价于配置 {@code jaravel.wire.outlet.except}）。
     *
     * @return 排除模式数组，支持精确 {@code /login} 与前缀通配 {@code /admin/*}
     */
    protected String[] except() {
        return except.toArray(new String[0]);
    }

    protected boolean isExcluded(Request request) {
        String uri;
        try {
            uri = request.uri();
        } catch (RuntimeException e) {
            return false;
        }
        if (uri == null) {
            return false;
        }
        for (String pattern : except()) {
            if (matches(uri, pattern)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String uri, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        if (pattern.endsWith("*")) {
            return uri.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return uri.equals(pattern);
    }

    // ===== 模板辅助 =====

    /**
     * 当前请求是否启用了本中间件（{@link #handle} 被调用过）。
     */
    public static boolean isEnabled(Request request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(OUTLET_ENABLED_MARKER));
    }

    /**
     * 供模板函数 {@code wire_outlet()} 使用：输出加载位置容器。
     * <p>
     * 中间件未启用时返回空字符串（等同指令不存在），避免留下无人管理的空容器。
     */
    public static String outletTag(Request request) {
        if (!isEnabled(request)) {
            return "";
        }
        return renderOutletTag();
    }

    /** 加载位置容器的 HTML。 */
    public static String renderOutletTag() {
        return "<div id=\"" + outletId + "\" wire:outlet data-wire-outlet=\"" + outletId + "\"></div>";
    }

    // ===== HTML / JSON 注入 =====

    /**
     * 把加载位置容器、首屏组件 bootstrap 与前端运行时注入到 HTML。
     *
     * @param html    原始 HTML
     * @param payload 首屏要立刻挂载的组件（可为空）
     * @return 注入后的 HTML
     */
    static String injectHtml(String html, List<Map<String, Object>> payload) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String result = html;

        // 1) 容器：模板里已用 {!! wire_outlet() !!} 指定了位置就不再注入。
        //    注意：必须检查 data-wire-outlet 属性而非 "wire:outlet" 文本，
        //    因为模板中可能出现 <code>[wire:outlet]</code> 说明文字导致误判。
        if (result.indexOf("data-wire-outlet") < 0) {
            result = insertAt(result, renderOutletTag(), position);
        }

        // 2) bootstrap + 运行时：始终放在 </body> 之前，保证容器已存在
        StringBuilder tail = new StringBuilder();
        tail.append("<script type=\"application/json\" wire:components data-wire-outlet=\"")
                .append(outletId).append("\">")
                .append(safeJson(payload))
                .append("</script>\n");
        if (autoInjectJs) {
            tail.append("<script src=\"").append(escapeAttr(jsPath)).append("\"></script>");
        }
        return insertAt(result, tail.toString(), POSITION_BODY_END);
    }

    /**
     * 在 HTML 的指定位置插入片段；找不到 body 标签时追加到末尾。
     */
    private static String insertAt(String html, String fragment, String pos) {
        String lower = html.toLowerCase();
        if (POSITION_BODY_START.equals(pos)) {
            int bodyOpen = lower.indexOf("<body");
            if (bodyOpen >= 0) {
                int gt = html.indexOf('>', bodyOpen);
                if (gt >= 0) {
                    return html.substring(0, gt + 1) + "\n" + fragment + html.substring(gt + 1);
                }
            }
        }
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + fragment + "\n" + html.substring(bodyClose);
        }
        return html + "\n" + fragment;
    }

    private static String safeJson(List<Map<String, Object>> payload) {
        try {
            String json = Json.stringify(payload == null ? new ArrayList<>() : payload);
            // 防止组件 HTML 里出现 </script> 提前闭合当前 JSON script 标签
            return json.replace("</", "<\\/");
        } catch (Exception e) {
            log.error("[wire-component] 序列化首屏组件失败，已降级为空列表", e);
            return "[]";
        }
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ===== 响应包装 =====

    /**
     * 惰性注入的响应包装：完全委托原响应，仅在读取正文时按 Content-Type 决定是否注入。
     * <p>
     * 之所以要包装而不是直接改内容：{@code ResponseBuilder.view()} 的正文是在
     * {@code getContent()} 被调用时才渲染的，提前取内容会破坏惰性渲染链路。
     */
    private static final class OutletResponse implements Response {

        private final Response delegate;
        private final List<Map<String, Object>> payload;

        OutletResponse(Response delegate, List<Map<String, Object>> payload) {
            this.delegate = delegate;
            this.payload = payload;
        }

        @Override
        public String getContent() {
            String content = delegate.getContent();
            if (content == null || content.isEmpty()) {
                return content;
            }
            String contentType = delegate.getContentType();
            String type = contentType == null ? "" : contentType.toLowerCase();

            if (type.contains("text/html")) {
                // 已注入过（例如嵌套调用）则跳过，避免重复容器
                if (content.indexOf("wire:components") >= 0) {
                    return content;
                }
                return injectHtml(content, payload);
            }
            // JSON 响应（Wire 自身或其它 JSON 接口）原样返回，不注入组件
            return content;
        }

        @Override
        public int getStatus() {
            return delegate.getStatus();
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public void addHeader(String name, String value) {
            delegate.addHeader(name, value);
        }

        @Override
        public Cookie[] getCookies() {
            return delegate.getCookies();
        }

        @Override
        public void addCookie(Cookie cookie) {
            delegate.addCookie(cookie);
        }

        @Override
        public void addCookie(String name, String value) {
            delegate.addCookie(name, value);
        }

        @Override
        public byte[] getBytes() {
            byte[] original = delegate.getBytes();
            if (original == null || original.length == 0) {
                return original;
            }
            String contentType = delegate.getContentType();
            String type = contentType == null ? "" : contentType.toLowerCase();
            if (type.contains("text/html")) {
                String content = new String(original, java.nio.charset.StandardCharsets.UTF_8);
                if (content.indexOf("wire:components") >= 0) {
                    return original;
                }
                String injected = injectHtml(content, payload);
                return injected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return original;
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public Object getBody() {
            // 若 body 是 String 且为 HTML 响应，注入 outlet
            Object body = delegate.getBody();
            if (body instanceof String) {
                String content = (String) body;
                String contentType = delegate.getContentType();
                String type = contentType == null ? "" : contentType.toLowerCase();
                if (type.contains("text/html") && content.indexOf("wire:components") < 0) {
                    return injectHtml(content, payload);
                }
            }
            return body;
        }
    }

    /**
     * 获取一个开箱即用的实例，供框架自动注册别名。
     */
    public static WireOutlet instance() {
        return new WireOutlet();
    }
}
