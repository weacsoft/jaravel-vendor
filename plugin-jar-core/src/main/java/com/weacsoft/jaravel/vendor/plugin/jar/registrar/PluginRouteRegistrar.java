package com.weacsoft.jaravel.vendor.plugin.jar.registrar;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件路由注册器。
 * <p>
 * 将插件路由（{@link RouteInfo}）注册到 Spring MVC 的 {@link RequestMappingHandlerMapping}，
 * 使插件路由可通过标准 Spring MVC 流程分发。注册的路由实际由
 * {@link PluginRouteHandler#handleRequest} 处理。
 * <p>
 * 注册流程：
 * <ol>
 *   <li>构造 {@link RequestMappingInfo}（HTTP 方法 + 路径）。</li>
 *   <li>将 {@link PluginRouteHandler#handleRequest} 包装为 {@link HandlerMethod} 注册到 handlerMapping。</li>
 *   <li>同时将 {@link RouteInfo} 注册到 {@link PluginRouteHandler} 的路由表。</li>
 * </ol>
 * <p>
 * 线程安全：{@link #registeredMappings} 使用 {@link ConcurrentHashMap}。
 */
public class PluginRouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PluginRouteRegistrar.class);

    private final RequestMappingHandlerMapping handlerMapping;
    private final PluginBeanRegistrar beanRegistrar;
    private final PluginRouteHandler routeHandler;

    /** 已注册的映射：pluginId -> List<RequestMappingInfo> */
    private final Map<String, List<RequestMappingInfo>> registeredMappings = new ConcurrentHashMap<>();

    /** PluginRouteHandler 的 Bean 名称（注册到容器中以便 handlerMapping 查找） */
    private static final String ROUTE_HANDLER_BEAN_NAME = "pluginRouteHandler";

    /**
     * 构造路由注册器。
     *
     * @param handlerMapping Spring MVC 的 RequestMappingHandlerMapping
     * @param beanRegistrar  Bean 注册器
     * @param integration    jaravel 集成（用于创建 jaravel Request/Response）
     */
    public PluginRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                                PluginBeanRegistrar beanRegistrar,
                                PluginIntegration integration) {
        this.handlerMapping = handlerMapping;
        this.beanRegistrar = beanRegistrar;
        this.routeHandler = new PluginRouteHandler(beanRegistrar, integration);
        // 注册 PluginRouteHandler 为 Bean，使 handlerMapping 可解析其方法
        registerRouteHandlerBean();
    }

    /**
     * 返回路由处理器。
     *
     * @return 路由处理器
     */
    public PluginRouteHandler getRouteHandler() {
        return routeHandler;
    }

    /**
     * 批量注册插件路由。
     *
     * @param pluginId 插件 ID
     * @param routes   路由集合
     * @return 成功注册的路由列表
     */
    public List<RouteInfo> registerRoutes(String pluginId, Set<RouteInfo> routes) {
        List<RouteInfo> registered = new ArrayList<>();
        if (routes == null || routes.isEmpty()) {
            return registered;
        }
        for (RouteInfo route : routes) {
            if (registerRoute(pluginId, route)) {
                registered.add(route);
            }
        }
        return registered;
    }

    /**
     * 注册单条插件路由。
     *
     * @param pluginId 插件 ID
     * @param route    路由信息
     * @return 注册成功返回 true，失败返回 false
     */
    public boolean registerRoute(String pluginId, RouteInfo route) {
        if (route == null || route.getPath() == null || route.getMethod() == null) {
            return false;
        }
        try {
            RequestMappingInfo mappingInfo = buildRequestMappingInfo(route);
            Method handlerMethod = PluginRouteHandler.class.getMethod(
                    "handleRequest", HttpServletRequest.class, HttpServletResponse.class);
            handlerMapping.registerMapping(mappingInfo, routeHandler, handlerMethod);
            routeHandler.registerRouteInfo(route);
            registeredMappings.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(mappingInfo);
            log.info("插件 {} 注册路由: {}", pluginId, route);
            return true;
        } catch (Exception e) {
            log.error("插件 {} 注册路由失败: {}", pluginId, route, e);
            return false;
        }
    }

    /**
     * 注销插件的所有路由。
     *
     * @param pluginId 插件 ID
     */
    public void unregisterRoutes(String pluginId) {
        List<RequestMappingInfo> mappings = registeredMappings.remove(pluginId);
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        for (RequestMappingInfo mappingInfo : mappings) {
            try {
                handlerMapping.unregisterMapping(mappingInfo);
            } catch (Exception e) {
                log.warn("注销映射失败: {}", mappingInfo, e);
            }
        }
        // 同步清理 routeHandler 中的路由（按 pluginId 无法直接清理，此处依赖上层维护）
        log.info("插件 {} 注销所有路由", pluginId);
    }

    /**
     * 返回插件已注册的路由数量。
     *
     * @param pluginId 插件 ID
     * @return 路由数量
     */
    public int getRegisteredRouteCount(String pluginId) {
        List<RequestMappingInfo> mappings = registeredMappings.get(pluginId);
        return mappings == null ? 0 : mappings.size();
    }

    /**
     * 构造 {@link RequestMappingInfo}。
     */
    private RequestMappingInfo buildRequestMappingInfo(RouteInfo route) {
        RequestMethod requestMethod = convertHttpMethod(route.getMethod());
        return RequestMappingInfo
                .paths(route.getPath())
                .methods(requestMethod)
                .produces(route.getProduces() != null ? route.getProduces() : "application/json")
                .build();
    }

    /**
     * 将插件 {@link HttpMethod} 转为 Spring {@link RequestMethod}。
     */
    private RequestMethod convertHttpMethod(HttpMethod method) {
        if (method == null) {
            return RequestMethod.GET;
        }
        try {
            return RequestMethod.valueOf(method.name());
        } catch (IllegalArgumentException e) {
            return RequestMethod.GET;
        }
    }

    /**
     * 将 PluginRouteHandler 注册为 Bean，使 Spring 容器可管理。
     */
    private void registerRouteHandlerBean() {
        try {
            if (!beanRegistrar.getApplicationContext().containsBean(ROUTE_HANDLER_BEAN_NAME)) {
                beanRegistrar.getApplicationContext().getBeanFactory()
                        .registerSingleton(ROUTE_HANDLER_BEAN_NAME, routeHandler);
            }
        } catch (Exception e) {
            log.warn("注册 PluginRouteHandler Bean 失败", e);
        }
    }
}
