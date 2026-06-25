package com.weacsoft.jaravel.vendor.plugin.jar.registrar;

import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件路由代理处理器。
 * <p>
 * 所有插件路由的 HTTP 请求最终都由本类 {@link #handleRequest} 处理：
 * <ol>
 *   <li>根据 {@code METHOD:/path} 从 {@link #routeRegistry} 查找 {@link RouteInfo}。</li>
 *   <li>从 Spring 容器获取目标 Bean（通过 {@link PluginBeanRegistrar}）。</li>
 *   <li>反射调用目标方法，参数由 {@link #resolveArguments} 解析。</li>
 *   <li>返回值写入响应。</li>
 * </ol>
 * <p>
 * {@link #routeRegistry} 使用 {@link ConcurrentHashMap}，支持并发读写。
 */
public class PluginRouteHandler {

    private static final Logger log = LoggerFactory.getLogger(PluginRouteHandler.class);

    /** 路由注册表：key = "METHOD:/path"，value = RouteInfo */
    private final ConcurrentHashMap<String, RouteInfo> routeRegistry = new ConcurrentHashMap<>();

    private final PluginBeanRegistrar beanRegistrar;

    private final PluginIntegration integration;

    public PluginRouteHandler(PluginBeanRegistrar beanRegistrar, PluginIntegration integration) {
        this.beanRegistrar = beanRegistrar;
        this.integration = integration;
    }

    /**
     * 生成路由注册表的 key。
     *
     * @param route 路由信息
     * @return key
     */
    private String routeKey(RouteInfo route) {
        return routeKey(route.getMethod().name(), route.getPath());
    }

    /**
     * 生成路由注册表的 key。
     *
     * @param httpMethod HTTP 方法名
     * @param path       路径
     * @return key
     */
    private String routeKey(String httpMethod, String path) {
        return httpMethod + ":" + path;
    }

    /**
     * 注册路由信息。
     *
     * @param route 路由信息
     */
    public void registerRouteInfo(RouteInfo route) {
        if (route == null) {
            return;
        }
        routeRegistry.put(routeKey(route), route);
    }

    /**
     * 注销路由信息。
     *
     * @param route 路由信息
     */
    public void unregisterRouteInfo(RouteInfo route) {
        if (route == null) {
            return;
        }
        routeRegistry.remove(routeKey(route));
    }

    /**
     * 按 HTTP 方法与路径注销路由信息。
     *
     * @param httpMethod HTTP 方法名
     * @param path       路径
     */
    public void unregisterRouteInfo(String httpMethod, String path) {
        routeRegistry.remove(routeKey(httpMethod, path));
    }

    /**
     * 清空所有路由信息。
     */
    public void clearRouteInfos() {
        routeRegistry.clear();
    }

    /**
     * 按 HTTP 方法与路径查找已注册的路由信息。
     * <p>
     * 供 {@link PluginRouteRegistrar#registerRouteAlias} 查找已有路由的 beanName/methodName。
     *
     * @param httpMethod HTTP 方法名
     * @param path       路径
     * @return 路由信息，不存在返回 null
     */
    public RouteInfo getRouteInfo(String httpMethod, String path) {
        return routeRegistry.get(routeKey(httpMethod, path));
    }

    /**
     * 处理 HTTP 请求。
     * <p>
     * 查找路由 -> 获取 Bean -> 反射调用 -> 写响应。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @throws Exception 调用异常
     */
    @ResponseBody
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String key = routeKey(method, path);
        RouteInfo route = routeRegistry.get(key);
        if (route == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Route not found\",\"method\":\"" + method + "\",\"path\":\"" + path + "\"}");
            return;
        }
        try {
            Object bean = beanRegistrar.getApplicationContext().getBean(route.getBeanName());
            if (bean == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Bean not found: " + route.getBeanName() + "\"}");
                return;
            }
            Method targetMethod = findMethod(bean.getClass(), route.getMethodName());
            if (targetMethod == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Method not found: " + route.getMethodName() + "\"}");
                return;
            }
            Object[] args = resolveArguments(targetMethod, request);
            Object result = targetMethod.invoke(bean, args);
            writeResponse(response, result, route.getProduces());
        } catch (Exception e) {
            log.error("处理插件路由失败: {} {}", method, path, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            response.getWriter().write("{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    /**
     * 解析方法参数。
     * <p>
     * 支持的类型：
     * <ul>
     *   <li>{@link String}</li>
     *   <li>{@code int} / {@link Integer}</li>
     *   <li>{@code long} / {@link Long}</li>
     *   <li>{@code boolean} / {@link Boolean}</li>
     *   <li>{@link HttpServletRequest}</li>
     *   <li>jaravel {@code Request}（当 integration 可用时）</li>
     * </ul>
     * 参数值优先从请求参数（query/form）获取，其次从路径变量获取。
     *
     * @param method  目标方法
     * @param request HTTP 请求
     * @return 参数数组
     */
    public Object[] resolveArguments(Method method, HttpServletRequest request) {
        Parameter[] parameters = method.getParameters();
        List<Object> args = new ArrayList<>(parameters.length);
        Map<String, String> pathVariables = extractPathVariables(request);
        // 尝试创建 jaravel Request 对象（不可用时为 null）
        Object pluginRequest = integration.createPluginRequest(request);
        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            Class<?> type = parameter.getType();
            // 1. HttpServletRequest 类型直接注入
            if (type == HttpServletRequest.class) {
                args.add(request);
                continue;
            }
            // 2. jaravel Request 类型（当 integration 可用且类型匹配时注入）
            if (pluginRequest != null && isJaravelRequestType(type)) {
                args.add(pluginRequest);
                continue;
            }
            // 3. 其他类型从请求参数/路径变量解析
            String value = request.getParameter(name);
            // 回退到路径变量
            if (value == null && pathVariables != null) {
                value = pathVariables.get(name);
            }
            args.add(convertValue(value, type, request));
        }
        return args.toArray();
    }

    /**
     * 判断类型是否为 jaravel Request。
     * <p>
     * 使用类名比较，避免直接依赖 jaravel 类（plugin-jar-core 不依赖 jaravel）。
     *
     * @param type 参数类型
     * @return true 表示为 jaravel Request 类型
     */
    private boolean isJaravelRequestType(Class<?> type) {
        return "com.weacsoft.jaravel.vendor.http.request.Request".equals(type.getName());
    }

    /**
     * 从当前请求属性中提取路径变量。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractPathVariables(HttpServletRequest request) {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes) {
                HttpServletRequest current = ((ServletRequestAttributes) attrs).getRequest();
                Object pathVars = current.getAttribute("org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
                if (pathVars instanceof Map) {
                    return (Map<String, String>) pathVars;
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(String value, Class<?> type, HttpServletRequest request) {
        if (type == HttpServletRequest.class) {
            return request;
        }
        if (value == null) {
            return defaultValue(type);
        }
        if (type == String.class) {
            return value;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        // 未知类型返回 null
        return null;
    }

    private Object defaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        return null;
    }

    private Method findMethod(Class<?> beanClass, String methodName) {
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private void writeResponse(HttpServletResponse response, Object result, String produces) throws Exception {
        // 优先尝试由 integration 处理（jaravel Response 等）
        if (integration.writePluginResponse(result, response, produces)) {
            return; // integration 已处理
        }
        // 默认写入逻辑
        if (result == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        String contentType = produces != null ? produces : "application/json";
        if (!contentType.contains("charset")) {
            contentType += ";charset=UTF-8";
        }
        response.setContentType(contentType);
        response.setStatus(HttpServletResponse.SC_OK);
        if (result instanceof String s) {
            response.getWriter().write(s);
        } else if (result instanceof byte[] bytes) {
            response.getOutputStream().write(bytes);
        } else {
            // 简单 toString，实际可由上层 MessageConverter 处理
            response.getWriter().write(result.toString());
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
