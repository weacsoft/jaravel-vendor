package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteHandler;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 租户感知的插件路由注册器。
 * <p>
 * 继承 {@link PluginRouteRegistrar}，在注册路由时根据 {@link TenantContext}
 * 自动对路由路径和 Bean 名称添加租户前缀，避免不同租户的同路径路由冲突。
 * <p>
 * 命名规则：
 * <ul>
 *   <li>路由路径：{@code /tenantId/originalPath}，如 {@code /studentA/blog/list}</li>
 *   <li>Bean 名称：{@code tenantId:originalBeanName}，如 {@code studentA:blogController}</li>
 * </ul>
 * <p>
 * 当 {@link TenantContext#getCurrentTenant()} 返回 null 时（非多租户插件），
 * 行为与父类完全一致，保持向后兼容。
 * <p>
 * 额外维护 {@link #tenantRouteInfos} 映射表，记录每个插件注册的前缀化路由，
 * 以便在 {@link #unregisterRoutes(String)} 时同步清理 {@link PluginRouteHandler}
 * 的路由注册表（原版未实现此清理）。
 */
public class TenantAwarePluginRouteRegistrar extends PluginRouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TenantAwarePluginRouteRegistrar.class);

    /** 租户路由追踪表：pluginId -> 前缀化 RouteInfo 列表，用于注销时清理 routeHandler */
    private final Map<String, List<RouteInfo>> tenantRouteInfos = new ConcurrentHashMap<>();

    /**
     * 构造租户感知的路由注册器。
     *
     * @param handlerMapping Spring MVC 的 RequestMappingHandlerMapping
     * @param beanRegistrar  Bean 注册器
     * @param integration    jaravel 集成
     */
    public TenantAwarePluginRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                                           com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar beanRegistrar,
                                           PluginIntegration integration) {
        super(handlerMapping, beanRegistrar, integration);
    }

    /**
     * 注册单条路由，自动添加租户前缀。
     * <p>
     * 若当前线程有租户上下文，构造前缀化的 {@link RouteInfo}（路径和 Bean 名称均前缀化），
     * 委托父类注册，并记录到 {@link #tenantRouteInfos} 以便后续清理。
     * 否则直接委托父类（向后兼容）。
     *
     * @param pluginId 插件 ID
     * @param route     原始路由信息
     * @return 注册成功返回 true
     */
    @Override
    public boolean registerRoute(String pluginId, RouteInfo route) {
        if (route == null || route.getPath() == null || route.getMethod() == null) {
            return false;
        }
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return super.registerRoute(pluginId, route);
        }
        RouteInfo prefixed = new RouteInfo(
                TenantNaming.prefixRoutePath(tenantId, route.getPath()),
                route.getMethod(),
                TenantNaming.prefixBeanName(tenantId, route.getBeanName()),
                route.getMethodName(),
                route.getProduces()
        );
        boolean result = super.registerRoute(pluginId, prefixed);
        if (result) {
            tenantRouteInfos.computeIfAbsent(pluginId, k -> new CopyOnWriteArrayList<>()).add(prefixed);
            log.debug("租户路由注册: {} {} -> {}",
                    prefixed.getMethod(), prefixed.getPath(), prefixed.getBeanName());
        }
        return result;
    }

    /**
     * 注销插件的所有路由，同步清理 routeHandler 路由表。
     * <p>
     * 先委托父类注销 Spring MVC 映射，再从 {@link #tenantRouteInfos} 取出前缀化路由，
     * 逐条清理 {@link PluginRouteHandler} 的路由注册表。
     *
     * @param pluginId 插件 ID
     */
    @Override
    public void unregisterRoutes(String pluginId) {
        super.unregisterRoutes(pluginId);
        List<RouteInfo> routes = tenantRouteInfos.remove(pluginId);
        if (routes != null && !routes.isEmpty()) {
            PluginRouteHandler handler = getRouteHandler();
            for (RouteInfo route : routes) {
                handler.unregisterRouteInfo(route);
            }
            log.debug("租户路由清理: pluginId={}, count={}", pluginId, routes.size());
        }
    }

    /**
     * 为已注册的路由注册别名路径，自动添加租户前缀。
     * <p>
     * 在多租户模式下，原路由路径已前缀化（如 {@code /studentA/blog/list}），
     * 因此查找原路由时也需使用前缀化路径。别名路径同样需要前缀化。
     *
     * @param pluginId     插件 ID
     * @param existingPath 已注册的原始路由路径（未前缀化）
     * @param aliasPath    别名路由路径（未前缀化）
     * @param httpMethod   HTTP 方法名
     * @return 注册成功返回 true
     */
    @Override
    public boolean registerRouteAlias(String pluginId, String existingPath,
                                       String aliasPath, String httpMethod) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return super.registerRouteAlias(pluginId, existingPath, aliasPath, httpMethod);
        }
        // 前缀化原路径和别名路径后委托父类
        String prefixedExisting = TenantNaming.prefixRoutePath(tenantId, existingPath);
        String prefixedAlias = TenantNaming.prefixRoutePath(tenantId, aliasPath);
        return super.registerRouteAlias(pluginId, prefixedExisting, prefixedAlias, httpMethod);
    }

    /**
     * 为已注册的路由注册别名路径（自动检测 HTTP 方法），自动添加租户前缀。
     */
    @Override
    public boolean registerRouteAlias(String pluginId, String existingPath, String aliasPath) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return super.registerRouteAlias(pluginId, existingPath, aliasPath);
        }
        String prefixedExisting = TenantNaming.prefixRoutePath(tenantId, existingPath);
        String prefixedAlias = TenantNaming.prefixRoutePath(tenantId, aliasPath);
        return super.registerRouteAlias(pluginId, prefixedExisting, prefixedAlias);
    }
}
