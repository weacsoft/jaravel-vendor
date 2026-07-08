package com.weacsoft.jaravel.vendor.plugin.jar.registrar;

import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件路由处理器 —— 所有插件 HTTP 请求的统一入口。
 * <p>
 * 通过 {@link PluginRouteRegistrar} 动态注册到 Spring MVC 的
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}，
 * 所有插件路由请求最终由 {@link #handleRequest(NativeWebRequest)} 统一分发。
 * <p>
 * 使用 {@link NativeWebRequest} 替代 {@code HttpServletRequest}/{@code HttpServletResponse}，
 * 消除对 {@code javax.servlet}/{@code jakarta.servlet} 的编译期依赖，
 * 使同一构件可运行在 SpringBoot 2.7（javax.servlet）和 3.x（jakarta.servlet）环境。
 *
 * @author lijialong
 */
public class PluginRouteHandler {

    private static final Logger log = LoggerFactory.getLogger(PluginRouteHandler.class);

    private final PluginBeanRegistrar beanRegistrar;
    private final PluginIntegration integration;

    /** 路由表：method + ":" + path → RouteInfo */
    private final Map<String, RouteInfo> routeRegistry = new ConcurrentHashMap<>();

    public PluginRouteHandler(PluginBeanRegistrar beanRegistrar, PluginIntegration integration) {
        this.beanRegistrar = beanRegistrar;
        this.integration = integration;
    }

    // ──────────────────────────── 路由注册 ────────────────────────────

    public void registerRouteInfo(RouteInfo route) {
        routeRegistry.put(routeKey(route), route);
        log.info("注册插件路由: {} {} → bean={}, method={}",
                route.getMethod(), route.getPath(), route.getBeanName(), route.getMethodName());
    }

    /**
     * 查询已注册的路由信息。
     *
     * @param method HTTP 方法名（如 "GET"、"POST"）
     * @param path   路由路径
     * @return 路由信息，不存在返回 null
     */
    public RouteInfo getRouteInfo(String method, String path) {
        if (method == null || path == null) {
            return null;
        }
        return routeRegistry.get(routeKey(method, path));
    }

    /**
     * 注销路由信息。
     *
     * @param method HTTP 方法名（如 "GET"、"POST"）
     * @param path   路由路径
     */
    public void unregisterRouteInfo(String method, String path) {
        if (method == null || path == null) {
            return;
        }
        routeRegistry.remove(routeKey(method, path));
        log.info("注销插件路由: {} {}", method, path);
    }

    // ──────────────────────────── 请求分发 ────────────────────────────

    /**
     * 所有插件路由请求的统一入口。
     * <p>
     * Spring MVC 将请求分发到此方法后，根据 HTTP 方法和路径查找 {@link RouteInfo}，
     * 反射调用对应插件 Bean 的方法，并将返回值交给 Spring 的 {@code @ResponseBody} 机制序列化。
     *
     * @param webRequest Spring 原生 Web 请求对象（兼容 SB2/SB3）
     * @return 插件方法返回值（由 Spring 序列化），或 null（当集成层已自行写入响应）
     */
    @ResponseBody
    public Object handleRequest(NativeWebRequest webRequest) throws Exception {
        String method = extractHttpMethod(webRequest);
        String path = extractPath(webRequest);
        String key = routeKey(method, path);

        RouteInfo route = routeRegistry.get(key);
        if (route == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Route not found: " + method + " " + path);
        }

        try {
            Object bean = beanRegistrar.getApplicationContext().getBean(route.getBeanName());
            if (bean == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Bean not found: " + route.getBeanName());
            }
            Method targetMethod = findMethod(bean.getClass(), route.getMethodName());
            if (targetMethod == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Method not found: " + route.getMethodName() + " on bean: " + route.getBeanName());
            }

            Object[] args = resolveArguments(targetMethod, webRequest);
            Object result = targetMethod.invoke(bean, args);

            // 优先交给集成层处理响应
            Object nativeResponse = getNativeResponse(webRequest);
            if (nativeResponse != null
                    && integration.writePluginResponse(result, nativeResponse, route.getProduces())) {
                return null; // 集成层已自行写入响应
            }

            // 默认：返回结果，由 Spring @ResponseBody 序列化
            return result;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理插件路由失败: {} {}", method, path, e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    cause.getMessage() != null ? cause.getMessage() : "Internal error");
        }
    }

    // ──────────────────────────── 参数解析 ────────────────────────────

    /**
     * 解析插件方法参数。
     * <p>
     * 按以下优先级匹配：
     * <ol>
     *   <li>Servlet 原生请求类型（运行时由 javax/jakarta 决定，通过 isInstance 检查）</li>
     *   <li>jaravel Request 类型（当集成层可用时）</li>
     *   <li>请求参数 / 路径变量 → 按类型转换</li>
     * </ol>
     *
     * @param method      插件方法
     * @param webRequest  Spring Web 请求
     * @return 方法参数数组
     */
    public Object[] resolveArguments(Method method, NativeWebRequest webRequest) {
        Parameter[] parameters = method.getParameters();
        List<Object> args = new ArrayList<>(parameters.length);
        Map<String, String> pathVariables = extractPathVariables(webRequest);
        Object nativeRequest = webRequest.getNativeRequest();
        Object pluginRequest = integration.createPluginRequest(nativeRequest);

        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            Class<?> type = parameter.getType();

            // 1. Servlet 原生请求类型（运行时通过 isInstance 兼容 javax/jakarta）
            if (nativeRequest != null && type.isInstance(nativeRequest)) {
                args.add(nativeRequest);
                continue;
            }

            // 2. jaravel Request 类型
            if (pluginRequest != null && isJaravelRequestType(type)) {
                args.add(pluginRequest);
                continue;
            }

            // 3. 请求参数 / 路径变量
            String value = webRequest.getParameter(name);
            if (value == null && pathVariables != null) {
                value = pathVariables.get(name);
            }
            args.add(convertValue(value, type, nativeRequest));
        }
        return args.toArray();
    }

    // ──────────────────────────── 私有工具 ────────────────────────────

    private String routeKey(String method, String path) {
        return method.toUpperCase() + ":" + path;
    }

    private String routeKey(RouteInfo route) {
        return routeKey(route.getMethod().name(), route.getPath());
    }

    public List<String> getAllRouteKeys() {
        return new ArrayList<>(routeRegistry.keySet());
    }

    /**
     * 从 NativeWebRequest 提取 HTTP 方法名。
     * <p>
     * Spring 6.0+ 的 {@code WebRequest.getHttpMethod()} 在 Spring 5.3 中不存在，
     * 因此通过反射从原生 Servlet 请求获取 {@code getMethod()}，
     * 保证同一构件可在 SB2.7（Spring 5.3）和 SB3.x（Spring 6）下编译运行。
     *
     * @param webRequest Spring Web 请求
     * @return HTTP 方法名（大写），无法获取时返回 "GET"
     */
    private String extractHttpMethod(NativeWebRequest webRequest) {
        Object nativeRequest = webRequest.getNativeRequest();
        if (nativeRequest != null) {
            try {
                Method m = nativeRequest.getClass().getMethod("getMethod");
                Object result = m.invoke(nativeRequest);
                if (result != null) {
                    return result.toString().toUpperCase();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return "GET";
    }

    /**
     * 从 NativeWebRequest 提取请求 URI。
     * <p>
     * 优先使用 {@link NativeWebRequest#getDescription}（返回 "uri=/path"），
     * 回退到反射调用 nativeRequest.getRequestURI()。
     */
    private String extractPath(NativeWebRequest webRequest) {
        String description = webRequest.getDescription(false);
        if (description != null && description.startsWith("uri=")) {
            return description.substring(4);
        }
        // 回退：反射获取
        Object nativeRequest = webRequest.getNativeRequest();
        if (nativeRequest != null) {
            try {
                Method m = nativeRequest.getClass().getMethod("getRequestURI");
                return (String) m.invoke(nativeRequest);
            } catch (Exception ignored) {
                // ignore
            }
        }
        return "/";
    }

    /**
     * 从 NativeWebRequest 获取底层 Servlet Response（兼容 javax/jakarta）。
     * NativeWebRequest 自身提供 getNativeResponse() 方法。
     */
    private Object getNativeResponse(NativeWebRequest webRequest) {
        return webRequest.getNativeResponse();
    }

    /**
     * 提取 Spring MVC 路径变量。
     * <p>
     * Spring MVC 在路由匹配后将路径变量存入 request attribute，
     * key 为 {@code HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE}。
     * 使用字符串常量避免对 spring-webmvc 类的编译期依赖。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractPathVariables(NativeWebRequest webRequest) {
        try {
            Object pathVars = webRequest.getAttribute(
                    "org.springframework.web.servlet.HandlerMapping.uriTemplateVariables",
                    RequestAttributes.SCOPE_REQUEST);
            if (pathVars instanceof Map) {
                return (Map<String, String>) pathVars;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    /**
     * 将字符串值转换为目标参数类型。
     *
     * @param value         字符串值（来自请求参数或路径变量）
     * @param type          目标类型
     * @param nativeRequest 原生请求对象（用于注入 Servlet 请求类型参数）
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    private Object convertValue(String value, Class<?> type, Object nativeRequest) {
        // Servlet 原生请求类型
        if (nativeRequest != null && type.isInstance(nativeRequest)) {
            return nativeRequest;
        }

        if (value == null) {
            return defaultValue(type);
        }

        String strValue = value.toString();

        if (type == String.class) {
            return strValue;
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(strValue);
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(strValue);
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(strValue);
        }
        if (type == Float.class || type == float.class) {
            return Float.parseFloat(strValue);
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(strValue);
        }
        if (type == Short.class || type == short.class) {
            return Short.parseShort(strValue);
        }
        if (type == Byte.class || type == byte.class) {
            return Byte.parseByte(strValue);
        }
        if (type == Character.class || type == char.class) {
            return strValue.length() > 0 ? strValue.charAt(0) : '\0';
        }
        if (type == Object.class) {
            return strValue;
        }
        // 枚举
        if (type.isEnum()) {
            for (Object enumConstant : type.getEnumConstants()) {
                if (((Enum<?>) enumConstant).name().equals(strValue)) {
                    return enumConstant;
                }
            }
        }
        return strValue;
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        // 检查父类
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findMethod(superClass, methodName);
        }
        return null;
    }

    private boolean isJaravelRequestType(Class<?> type) {
        return type.getName().startsWith("com.weacsoft.jaravel.")
                && type.getSimpleName().endsWith("Request");
    }
}
