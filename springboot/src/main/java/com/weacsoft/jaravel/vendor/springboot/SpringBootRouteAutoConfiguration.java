package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.route.Route;
import com.weacsoft.jaravel.vendor.route.Router;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SpringBoot 路由自动装配（核心）：将 Jaravel {@link Router} 中注册的路由转换为
 * Spring {@link RouterFunction}，并在请求处理时执行中间件链。
 * <p>
 * 适配 Spring Boot 3.2.5 / Spring 6.x（jakarta.servlet、{@code org.springframework.web.servlet.function}）。
 * <p>
 * 同时负责自动扫描 {@link MiddlewareAlias} 注解的中间件 Bean，注册到
 * {@link MiddlewareAliasRegistry} 全局注册表，使路由可通过别名、类对象或类名引用中间件。
 * <p>
 * 流程：
 * <ol>
 *   <li>扫描容器中所有 {@link MiddlewareAlias} 注解的 Bean，注册到 {@link MiddlewareAliasRegistry}（别名非空时注册别名，始终注册类映射）</li>
 *   <li>遍历 {@link Router#getAllRoutes()}，为每条路由构造 {@link RequestPredicate}（HTTP 方法 + 完整 URI）</li>
 *   <li>构造 {@link HandlerFunction}：通过 {@link RequestFactory#buildFromServerRequest} 构建 Laravel 风格
 *       {@link Request}，通过 {@link RouteAuthHandler} 设置认证上下文，再以逆序折叠路由中间件链，终点调用 {@code Route.getAction()::handle}</li>
 *   <li>将 {@link Response} 的状态码、响应头、内容转为 Spring {@link ServerResponse}</li>
 * </ol>
 */
@AutoConfiguration
public class SpringBootRouteAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpringBootRouteAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Router baseRouter() {
        Router router = new Router();
        return router;
    }

    /**
     * 认证处理器 bean：当 auth 模块在 classpath 且 AuthManager bean 存在时启用。
     * <p>
     * 封装 {@code AuthContext} 和 {@code AuthManager} 的调用，使主路由逻辑不直接
     * 引用 auth 模块的类，避免 auth 不在 classpath 时的 {@code NoClassDefFoundError}。
     * <p>
     * 使用 {@code @ConditionalOnClass(name = ...)} 字符串形式，不触发 AuthManager 类加载；
     * 方法参数使用全限定名，无需 import。Spring 通过 ASM 读取注解元数据，
     * 仅在条件满足时才调用此方法，此时 AuthManager 必然在 classpath。
     */
    @Bean
    @ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.auth.AuthManager")
    @ConditionalOnBean(type = "com.weacsoft.jaravel.vendor.auth.AuthManager")
    @ConditionalOnMissingBean(RouteAuthHandler.class)
    public RouteAuthHandler authRouteAuthHandler(
            com.weacsoft.jaravel.vendor.auth.AuthManager authManager) {
        return new AuthRouteAuthHandler(authManager);
    }

    /**
     * 认证处理器 bean（fallback）：当 auth 模块不在 classpath 时使用 no-op 实现。
     */
    @Bean
    @ConditionalOnMissingBean(RouteAuthHandler.class)
    public RouteAuthHandler defaultRouteAuthHandler() {
        return new DefaultRouteAuthHandler();
    }

    @Bean
    public RouterFunction<ServerResponse> jaravelRouterFunction(Router router,
            RouteAuthHandler routeAuthHandler, ApplicationContext applicationContext) {
        // 扫描 @MiddlewareAlias 注解的中间件 Bean，注册到全局别名注册表
        scanMiddlewareAliases(applicationContext);

        List<Route> routes = router.getAllRoutes();
        RouterFunctions.Builder builder = RouterFunctions.route();
        routes.forEach(route -> {
            builder.route(createRoutePredicate(route), createRouteFunction(route, routeAuthHandler));
        });
        return builder.build();
    }

    /**
     * 扫描容器中所有 {@link MiddlewareAlias} 注解的 Bean，注册到 {@link MiddlewareAliasRegistry} 全局注册表。
     * <p>
     * 三种场景：
     * <ul>
     *   <li>未标注 {@code @MiddlewareAlias}：视为用户自建，不扫描</li>
     *   <li>标注但未填别名（{@code @MiddlewareAlias}）：仅注册类映射，路由通过类对象或类名引用</li>
     *   <li>标注且填了别名（{@code @MiddlewareAlias("auth")}）：同时注册别名映射和类映射</li>
     * </ul>
     */
    void scanMiddlewareAliases(ApplicationContext applicationContext) {
        if (applicationContext == null) return;
        MiddlewareAliasRegistry registry = MiddlewareAliasRegistry.getGlobal();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(MiddlewareAlias.class);
        beans.forEach((beanName, bean) -> {
            if (bean instanceof Middleware) {
                MiddlewareAlias annotation = bean.getClass().getAnnotation(MiddlewareAlias.class);
                if (annotation != null) {
                    String alias = annotation.value();
                    registry.register(alias, (Middleware) bean);
                }
            }
        });
    }

    private RequestPredicate createRoutePredicate(Route route) {
        return RequestPredicates.method(HttpMethod.valueOf(route.getMethod()))
                .and(RequestPredicates.path(route.generateFullUri()));
    }

    private HandlerFunction<ServerResponse> createRouteFunction(Route route, RouteAuthHandler routeAuthHandler) {
        return springRequest -> {
            try {
                Request customRequest = RequestFactory.buildFromServerRequest(springRequest);
                // 提取路径参数（如 /api/users/{id} 中的 id）
                try {
                    Map<String, String> pathVars = springRequest.pathVariables();
                    if (pathVars != null && !pathVars.isEmpty()) {
                        Map<String, Object> routeParams = new LinkedHashMap<>();
                        pathVars.forEach(routeParams::put);
                        customRequest.setRouteParams(routeParams);
                    }
                } catch (Exception ignored) {
                }
                // 设置认证上下文（当 auth 模块存在时设置 AuthContext，否则 no-op）
                routeAuthHandler.setupAuth(customRequest);
                try {
                    // 获取路由中间件（含根 Router 全局中间件 + 路由组中间件 + 路由级中间件）
                    List<Middleware> allMiddlewares = route.getMiddlewares();

                    // 逆序折叠中间件链
                    Middleware.NextFunction finalHandler = route.getAction()::handle;
                    for (int i = allMiddlewares.size() - 1; i >= 0; i--) {
                        final Middleware middleware = allMiddlewares.get(i);
                        final Middleware.NextFunction next = finalHandler;
                        finalHandler = request -> middleware.handle(request, next);
                    }

                    Response response = finalHandler.apply(customRequest);
                    return createResponse(response, customRequest);
                } finally {
                    routeAuthHandler.clearAuth();
                }
            } catch (Exception e) {
                log.error("路由处理异常 [{} {}]", route.getMethod(), route.generateFullUri(), e);
                return ServerResponse.status(500).body(
                        Map.of("error", e.getClass().getSimpleName(),
                               "message", e.getMessage() != null ? e.getMessage() : "no message"));
            }
        };
    }

    private ServerResponse createResponse(Response response, Request request) {
        ServerResponse.BodyBuilder builder = ServerResponse.status(response.getStatus());
        response.getHeaders().forEach((key, value) -> {
            builder.header(key, value.toArray(new String[0]));
        });

        // 兜底 Content-Type：如果 Response 没有设置，使用 getContentType() 的默认值（text/plain）
        if (response.getHeaders().get("Content-Type") == null) {
            builder.header("Content-Type", response.getContentType());
        }

        // 写入 Cookie
        if (response.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : response.getCookies()) {
                String cookieStr = cookie.getName() + "=" + cookie.getValue()
                        + "; Path=" + (cookie.getPath() != null ? cookie.getPath() : "/");
                if (cookie.getMaxAge() > 0) {
                    cookieStr += "; Max-Age=" + cookie.getMaxAge();
                }
                builder.header("Set-Cookie", cookieStr);
            }
        }

        String content = response.getContent();
        if (content == null) {
            byte[] bytes = response.getBytes();
            if (bytes != null) {
                return builder.body(bytes);
            }
            return builder.build();
        }
        return builder.body(content);
    }
}
