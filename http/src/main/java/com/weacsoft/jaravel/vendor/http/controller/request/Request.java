package com.weacsoft.jaravel.vendor.http.controller.request;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

public class Request {

    private final Map<String, Object> query = new LinkedHashMap<>();
    private final Map<String, Object> input = new LinkedHashMap<>();
    private final Map<String, Object> file = new LinkedHashMap<>();
    private final Map<String, Object> header = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<Cookie> cookies = new ArrayList<>();
    private final List<Cookie> newCookies = new ArrayList<>();
    private final Map<String, Object> session = new LinkedHashMap<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    @Setter
    private Map<String, Object> routeParams = new LinkedHashMap<>();
    @Getter
    private HttpServletRequest request;


    public Request() {
    }

    public void addInput(String key, Object newValue) {
        Object value = input.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(value);
            list.add(newValue);
            value = list;
        }
        input.put(key, value);
    }

    public void addQuery(String key, Object newValue) {
        Object value = query.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(value);
            list.add(newValue);
            value = list;
        }
        query.put(key, value);
    }

    public void addHeader(String key, Object newValue) {
        Object value = header.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(value);
            list.add(newValue);
            value = list;
        }
        header.put(key, value);
    }

    public void addFile(String key, MultipartFile newValue) {
        Object value = file.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<MultipartFile>) value).add(newValue);
        } else {
            List<MultipartFile> list = new ArrayList<>();
            list.add((MultipartFile) value);
            list.add(newValue);
            value = list;
        }
        file.put(key, value);
    }

    public void addCookie(String key, Object newValue) {
        Cookie newCookie = new Cookie(key, newValue.toString());
        cookies.add(newCookie);
        newCookies.add(newCookie);
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
        newCookies.add(cookie);
    }

    public void addSession(String key, Object newValue) {
        Object value = session.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(value);
            list.add(newValue);
            value = list;
        }
        session.put(key, value);
    }

    public void replaceInput(String key, Object newValue) {
        input.put(key, newValue);
    }

    public void replaceQuery(String key, Object newValue) {
        query.put(key, newValue);
    }

    public void replaceHeader(String key, Object newValue) {
        header.put(key, newValue);
    }

    public void replaceFile(String key, Object newValue) {
        file.put(key, newValue);
    }

    public void replaceCookie(String key, Object newValue) {
        Cookie newCookie = new Cookie(key, newValue.toString());
        for (int i = 0; i < cookies.size(); i++) {
            if (cookies.get(i).getName().equals(key)) {
                cookies.set(i, newCookie);
                return;
            }
        }
        cookies.add(newCookie);
    }

    public void replaceCookie(Cookie cookie) {
        replaceCookie(cookie.getName(), cookie.getValue());
    }

    public void replaceSession(String key, Object newValue) {
        session.put(key, newValue);
    }

    public void removeInput(String key) {
        input.remove(key);
    }

    public void removeQuery(String key) {
        query.remove(key);
    }

    public void removeHeader(String key) {
        header.remove(key);
    }

    public void removeFile(String key) {
        file.remove(key);
    }

    public void removeCookie(String key) {
        cookies.removeIf(cookie -> cookie.getName().equals(key));
    }

    public void removeSession(String key) {
        session.remove(key);
    }

    public Set<String> getNames() {
        Set<String> names = new HashSet<>();
        names.addAll(inputNames());
        names.addAll(queryNames());
        return names;
    }

    public Map<String, Object> get() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(query);
        result.putAll(input);
        return result;
    }

    public String get(String key) {
        return get(key, (String) null);
    }

    public String get(String key, String defaultValue) {
        String value = get(key, String.class);
        if (value == null) {
            Object v = input.get(key);
            if (v != null) {
                value = v.toString();
            } else {
                v = query.get(key);
                if (v != null) {
                    value = v.toString();
                }
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T get(String key, T defaultValue) {
        if (defaultValue == null) {
            // 当 defaultValue 为 null 时，无法推断 Class<T>，直接尝试从 input/query 获取原始值
            // 用户应使用 get(key, Class<T>) 进行类型安全的 null 默认值获取
            Object raw = null;
            if (input.containsKey(key)) {
                raw = input.get(key);
                if (raw instanceof List) {
                    raw = ((List<Object>) raw).get(0);
                }
            }
            if (raw == null && query.containsKey(key)) {
                raw = query.get(key);
                if (raw instanceof List) {
                    raw = ((List<Object>) raw).get(0);
                }
            }
            return (T) raw;
        }
        T value = get(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = null;
        if (input.containsKey(key)) {
            value = input.get(key);
            if (value instanceof List) {
                value = ((List<Object>) value).get(0);
            }
        }
        if (value == null && query.containsKey(key)) {
            value = query.get(key);
            if (value instanceof List) {
                value = ((List<Object>) value).get(0);
            }
        }
        if (value == null) return null;
        // 类型恰好匹配(如 String 值 + String.class)→ 直接返回
        if (clazz.isInstance(value)) return (T) value;
        // 类型不匹配:HTTP 查询参数/表单值通常是 String,而调用方常按目标类型请求
        // (如 get("page", Long.class)/get("page", 1L))。此时尝试字符串→目标类型转换,
        // 避免「非 String 默认值永远拿不到真实值、恒返回默认值」的隐性 bug。
        // 转换失败时返回 null(由调用方回退默认值),与转换异常语义一致。
        try {
            String s = String.valueOf(value);
            if (clazz == String.class) return (T) s;
            if (clazz == Long.class || clazz == long.class) return (T) Long.valueOf(s);
            if (clazz == Integer.class || clazz == int.class) return (T) Integer.valueOf(s);
            if (clazz == Double.class || clazz == double.class) return (T) Double.valueOf(s);
            if (clazz == Float.class || clazz == float.class) return (T) Float.valueOf(s);
            if (clazz == Boolean.class || clazz == boolean.class) return (T) Boolean.valueOf(s);
            if (clazz == Short.class || clazz == short.class) return (T) Short.valueOf(s);
            if (clazz == Byte.class || clazz == byte.class) return (T) Byte.valueOf(s);
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<Object> gets(String key) {
        Object value = input.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        if (value != null) {
            return Collections.singletonList(value);
        }
        value = query.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        if (value != null) {
            return Collections.singletonList(value);
        }
        return Collections.emptyList();
    }

    public Map<String, Object> all() {
        Map<String, Object> map = new HashMap<>();
        map.putAll(query());
        map.putAll(input());
        return map;
    }

    public Set<String> queryNames() {
        return query.keySet();
    }

    public Map<String, Object> query() {
        return new LinkedHashMap<>(query);
    }

    /**
     * 取 query 参数(单参数重载)。
     * 缺省或空串(经 ConvertEmptyStringsToNull 中间件转 null)时返回 {@code null},
     * 而不是空串——便于在控制器里直接 {@code if (query("key") != null)} 判断。
     *
     * @param key 参数名
     * @return 参数值;缺省/空串返回 null
     */
    public String query(String key) {
        // 走 String 重载:非 String 原始值(如 addQuery 写入的 Integer)经 toString 兜底
        return query(key, (String) null);
    }

    public String query(String key, String defaultValue) {
        String value = query(key, String.class);
        if (value == null) {
            // 原始值可能非 String(如 replaceQuery 写入的对象),做 toString 兜底
            Object v = query.get(key);
            if (v != null && !(v instanceof List)) {
                value = v.toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T query(String key, T defaultValue) {
        if (defaultValue == null) {
            // 默认值为 null:无法推断 Class<T>,直接返回原始值(缺省/空串 → null)
            Object raw = query.get(key);
            if (raw instanceof List) {
                raw = ((List<Object>) raw).get(0);
            }
            return (T) raw;
        }
        T value = query(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T query(String key, Class<T> clazz) {
        if (query.containsKey(key)) {
            Object value = query.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> queries(String key) {
        Object value = query.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Set<String> inputNames() {
        return input.keySet();
    }

    public Map<String, Object> input() {
        return new LinkedHashMap<>(input);
    }

    /**
     * 取 input 参数(单参数重载)。缺省或空串返回 {@code null}。
     *
     * @param key 参数名
     * @return 参数值;缺省/空串返回 null
     */
    public String input(String key) {
        // 走 String 重载:非 String 原始值(如 addInput 写入的 Integer)经 toString 兜底
        return input(key, (String) null);
    }

    public String input(String key, String defaultValue) {
        String value = input(key, String.class);
        if (value == null) {
            Object v = input.get(key);
            if (v != null && !(v instanceof List)) {
                value = v.toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T input(String key, T defaultValue) {
        if (defaultValue == null) {
            Object raw = input.get(key);
            if (raw instanceof List) {
                raw = ((List<Object>) raw).get(0);
            }
            return (T) raw;
        }
        T value = input(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T input(String key, Class<T> clazz) {
        if (input.containsKey(key)) {
            Object value = input.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> inputs(String key) {
        Object value = input.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Arrays.asList(value);
    }

    public Set<String> fileNames() {
        return file.keySet();
    }

    public Map<String, Object> file() {
        return new LinkedHashMap<>(file);
    }

    public MultipartFile file(String key) {
        Object value = file.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<MultipartFile> list = (List<MultipartFile>) value;
            return list.isEmpty() ? null : list.get(0);
        }
        return (MultipartFile) value;
    }

    public List<MultipartFile> files(String key) {
        Object value = file.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return (List<MultipartFile>) value;
        }
        return Arrays.asList((MultipartFile) value);
    }

    public Set<String> headerNames() {
        return header.keySet();
    }

    public Map<String, Object> header() {
        return new LinkedHashMap<>(header);
    }

    /**
     * 取 header 参数(单参数重载)。缺省返回 {@code null}。
     *
     * @param key 参数名
     * @return 参数值;缺省返回 null
     */
    public String header(String key) {
        // 走 String 重载:非 String 原始值(如 addHeader 写入的 Integer)经 toString 兜底
        return header(key, (String) null);
    }

    public String header(String key, String defaultValue) {
        String value = header(key, String.class);
        if (value == null) {
            Object v = header.get(key);
            if (v != null && !(v instanceof List)) {
                value = v.toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T header(String key, T defaultValue) {
        if (defaultValue == null) {
            Object raw = header.get(key);
            if (raw instanceof List) {
                raw = ((List<Object>) raw).get(0);
            }
            return (T) raw;
        }
        T value = header(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T header(String key, Class<T> clazz) {
        if (header.containsKey(key)) {
            Object value = header.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> headers(String key) {
        Object value = header.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Set<String> cookieNames() {
        Set<String> names = new HashSet<>();
        for (Cookie cookie : cookies) {
            names.add(cookie.getName());
        }
        return names;
    }

    public Map<String, Object> cookie() {
        Map<String, Object> cookieMap = new LinkedHashMap<>();
        for (Cookie cookie : cookies) {
            String name = cookie.getName();
            if (cookieMap.containsKey(name)) {
                Object existing = cookieMap.get(name);
                if (existing instanceof List) {
                    ((List<Object>) existing).add(cookie.getValue());
                } else {
                    List<Object> values = new ArrayList<>();
                    values.add(existing);
                    values.add(cookie.getValue());
                    cookieMap.put(name, values);
                }
            } else {
                cookieMap.put(name, cookie.getValue());
            }
        }
        return cookieMap;
    }

    /**
     * 取 cookie 参数(单参数重载)。缺省返回 {@code null}。
     *
     * @param key 参数名
     * @return 参数值;缺省返回 null
     */
    public String cookie(String key) {
        return cookie(key, (String) null);
    }

    public String cookie(String key, String defaultValue) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                return cookie.getValue();
            }
        }
        return defaultValue;
    }

    public <T> T cookie(String key, T defaultValue) {
        if (defaultValue == null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(key)) {
                    return (T) cookie.getValue();
                }
            }
            return null;
        }
        T value = cookie(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T cookie(String key, Class<T> clazz) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                if (clazz == String.class) {
                    return (T) cookie.getValue();
                }
            }
        }
        return null;
    }

    public List<String> cookies(String key) {
        List<String> values = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                values.add(cookie.getValue());
            }
        }
        return values;
    }

    public Cookie[] getCookieObjects() {
        return cookies.toArray(new Cookie[0]);
    }

    public Cookie[] getNewCookies() {
        return newCookies.toArray(new Cookie[0]);
    }

    public Set<String> sessionNames() {
        return session.keySet();
    }

    public Map<String, Object> session() {
        return new LinkedHashMap<>(session);
    }

    /**
     * 取 session 参数(单参数重载)。缺省返回 {@code null}。
     *
     * @param key 参数名
     * @return 参数值;缺省/空串返回 null
     */
    public String session(String key) {
        // 走 String 重载:非 String 原始值(如 addSession 写入的 Long)经 toString 兜底
        return session(key, (String) null);
    }

    public String session(String key, String defaultValue) {
        String value = session(key, String.class);
        if (value == null) {
            Object v = session.get(key);
            if (v != null && !(v instanceof List)) {
                value = v.toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T session(String key, T defaultValue) {
        if (defaultValue == null) {
            Object raw = session.get(key);
            if (raw instanceof List) {
                raw = ((List<Object>) raw).get(0);
            }
            return (T) raw;
        }
        T value = session(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T session(String key, Class<T> clazz) {
        if (session.containsKey(key)) {
            Object value = session.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> sessions(String key) {
        Object value = session.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public boolean has(String key) {
        return query.containsKey(key) || input.containsKey(key);
    }

    public boolean hasFile(String key) {
        return file.containsKey(key);
    }

    public boolean hasHeader(String key) {
        return header.containsKey(key);
    }

    public boolean hasCookie(String key) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSession(String key) {
        return session.containsKey(key);
    }

    // ---- 属性（Attributes，对齐 Laravel $request->attributes） ----
    // 用于中间件间传递数据，独立于 input/query/header 等

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> clazz) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public Map<String, Object> attributes() {
        return new LinkedHashMap<>(attributes);
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    // ---- 路由参数 ----

    public Map<String, Object> routeParams() {
        return new LinkedHashMap<>(routeParams);
    }

    public String routeParam(String key) {
        Object value = routeParams.get(key);
        return value != null ? value.toString() : null;
    }

    public <T> T routeParam(String key, Class<T> clazz) {
        Object value = routeParams.get(key);
        if (value == null) return null;
        if (clazz.isInstance(value)) return (T) value;
        String str = value.toString();
        if (clazz == Long.class || clazz == long.class) return (T) Long.valueOf(str);
        if (clazz == Integer.class || clazz == int.class) return (T) Integer.valueOf(str);
        if (clazz == String.class) return (T) str;
        if (clazz == Boolean.class || clazz == boolean.class) return (T) Boolean.valueOf(str);
        return (T) value;
    }

    public boolean hasRouteParam(String key) {
        return routeParams.containsKey(key);
    }

    /**
     * 获取客户端 IP 地址，对齐 PHP Laravel 的 $request->ip()。
     * <p>
     * 优先从 X-Forwarded-For 请求头获取（经过反向代理时），
     * 否则使用 HttpServletRequest.getRemoteAddr()。
     *
     * @return 客户端 IP 地址
     */
    public String ip() {
        if (request == null) {
            return "unknown";
        }
        String xff = header("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * 获取原始远程地址（不经过 X-Forwarded-For 处理），对齐 Laravel 的 $request->ip() 在 TrustProxies 之前的行为。
     * <p>
     * 供 TrustProxies 等中间件在处理代理头之前获取真实 TCP 连接地址使用。
     *
     * @return 原始客户端 IP 地址
     */
    public String remoteAddr() {
        if (request == null) {
            return "unknown";
        }
        String addr = request.getRemoteAddr();
        return addr != null ? addr : "unknown";
    }

    /**
     * 获取 HTTP 请求方法，对齐 Laravel 的 $request->method()。
     *
     * @return HTTP 方法（GET/POST/PUT/DELETE 等），request 不可用时返回空串
     */
    public String method() {
        if (request == null) {
            return "";
        }
        return request.getMethod();
    }

    /**
     * 获取请求 URI，对齐 Laravel 的 $request->uri()。
     *
     * @return 请求 URI（如 /api/wire/demo），request 不可用时返回空串
     */
    public String uri() {
        if (request == null) {
            return "";
        }
        return request.getRequestURI();
    }

    /**
     * 获取完整 URL（协议 + 主机 + URI + query），对齐 Laravel 的 $request->fullUrl()。
     * <p>
     * 代理场景下优先采用 X-Forwarded-Proto / X-Forwarded-Host 头还原真实入口地址
     * （与 {@link #ip()} 的代理意识一致）。
     *
     * @return 完整 URL（如 {@code https://example.com/weapp?from=mp}），request 不可用时返回空串
     */
    public String fullUrl() {
        if (request == null) {
            return "";
        }
        String scheme = firstHeaderValue(header("X-Forwarded-Proto"));
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.isSecure() ? "https" : "http";
        }
        String host = firstHeaderValue(header("X-Forwarded-Host"));
        if (host == null || host.isEmpty()) {
            host = header("Host");
        }
        if (host == null || host.isEmpty()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        String url = scheme + "://" + host + (uri().isEmpty() ? "/" : uri());
        String qs = request.getQueryString();
        if (qs != null && !qs.isEmpty()) {
            url = url + "?" + qs;
        }
        return url;
    }

    private static String firstHeaderValue(String joined) {
        if (joined == null || joined.isEmpty()) {
            return null;
        }
        return joined.split(",")[0].trim();
    }

    /**
     * 获取请求路径（Servlet 路径），对齐 Laravel 的 $request->path()。
     *
     * @return 请求路径，request 不可用时返回空串
     */
    public String path() {
        if (request == null) {
            return "";
        }
        String servletPath = request.getServletPath();
        return servletPath != null ? servletPath : "";
    }

    /**
     * 获取请求的 Content-Type，对齐 Laravel 的 $request->contentType()。
     *
     * @return Content-Type 字符串，request 不可用时返回 null
     */
    public String contentType() {
        if (request == null) {
            return null;
        }
        return request.getContentType();
    }

    /**
     * 判断请求是否通过 HTTPS 发起，对齐 Laravel 的 $request->secure()。
     *
     * @return true=HTTPS，false=HTTP，request 不可用时返回 false
     */
    public boolean isSecure() {
        if (request == null) {
            return false;
        }
        return request.isSecure();
    }

    /**
     * 向当前请求的 HttpSession 写入属性值，对齐 Laravel Session 的 put 操作。
     * <p>
     * 同时更新内部 session 缓存，使后续 session(key) 读取能立即取到新值。
     *
     * @param key   session 属性名
     * @param value 属性值
     */
    public void putSession(String key, Object value) {
        if (request != null) {
            HttpSession httpSession = request.getSession(true);
            httpSession.setAttribute(key, value);
        }
        session.put(key, value);
    }

    /**
     * 从当前请求的 HttpSession 中移除属性，对齐 Laravel Session 的 forget 操作。
     *
     * @param key session 属性名
     */
    public void removeSessionAttribute(String key) {
        if (request != null) {
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.removeAttribute(key);
            }
        }
        session.remove(key);
    }

    /**
     * 获取原始 HttpSession 对象（供需要直接操作 session 生命周期的场景使用）。
     * <p>
     * 注意：优先使用 session(key)、putSession(key, value) 等封装方法，
     * 仅在需要 invalidate() 等底层操作时才使用此方法。
     *
     * @param create 是否在 session 不存在时创建新 session
     * @return HttpSession 实例，request 不可用时返回 null
     */
    public HttpSession rawSession(boolean create) {
        if (request == null) {
            return null;
        }
        return request.getSession(create);
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValue = request.getHeaders(headerName);
            while (headerValue.hasMoreElements()) {
                this.addHeader(headerName, headerValue.nextElement());
            }
        }
        Cookie[] requestCookies = request.getCookies();
        if (requestCookies != null) {
            for (Cookie cookie : requestCookies) {
                this.cookies.add(cookie);
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Enumeration<String> sessionNames = session.getAttributeNames();
            while (sessionNames.hasMoreElements()) {
                String sessionName = sessionNames.nextElement();
                this.addSession(sessionName, session.getAttribute(sessionName));
            }
        }
    }

    public static class FluxMultipartFile implements MultipartFile {
        private final Part filePart;
        private final String name;
        private byte[] cachedBytes;

        public FluxMultipartFile(String name, Part filePart) {
            this.name = name;
            this.filePart = filePart;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return filePart.getSubmittedFileName();
        }

        @Override
        public String getContentType() {
            return filePart.getContentType();
        }

        @Override
        public boolean isEmpty() {
            try {
                return getBytes().length == 0;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getSize() {
            try {
                return this.getBytes().length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            if (cachedBytes != null) {
                return cachedBytes;
            }
            try (InputStream inputStream = filePart.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                // 调用自定义的copy方法替代transferTo
                byte[] buffer = new byte[4096];
                int bytesRead;
                // 循环读取字节到缓冲区，直到读取完毕（返回-1）
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // 将缓冲区中的字节写入输出流（注意只写实际读取到的字节数）
                    outputStream.write(buffer, 0, bytesRead);
                }
                // 刷新输出流，确保所有数据都被写入
                outputStream.flush();
                return cachedBytes = outputStream.toByteArray();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(File dest) throws IllegalStateException {
        }
    }
}