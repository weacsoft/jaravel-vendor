package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.AuthManager;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.route.Route;
import com.weacsoft.jaravel.vendor.route.Router;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * 流程：
 * <ol>
 *   <li>遍历 {@link Router#getAllRoutes()}，为每条路由构造 {@link RequestPredicate}（HTTP 方法 + 完整 URI）</li>
 *   <li>构造 {@link HandlerFunction}：通过 {@link RequestFactory#buildFromServerRequest} 构建 Laravel 风格
 *       {@link Request}，设置 {@link AuthContext}，再以逆序折叠路由中间件链，终点调用 {@code Route.getAction()::handle}</li>
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

    @Bean
    public RouterFunction<ServerResponse> jaravelRouterFunction(Router router,
            ObjectProvider<AuthManager> authManagerProvider,
            ObjectProvider<GlobalMiddlewareRegistry> globalMiddlewareProvider) {
        AuthManager authManager = authManagerProvider.getIfAvailable();
        GlobalMiddlewareRegistry globalMiddleware = globalMiddlewareProvider.getIfAvailable();
        List<Route> routes = router.getAllRoutes();
        RouterFunctions.Builder builder = RouterFunctions.route();
        routes.forEach(route -> {
            builder.route(createRoutePredicate(route), createRouteFunction(route, authManager, globalMiddleware));
        });
        return builder.build();
    }

    private RequestPredicate createRoutePredicate(Route route) {
        return RequestPredicates.method(HttpMethod.valueOf(route.getMethod()))
                .and(RequestPredicates.path(route.generateFullUri()));
    }

    private HandlerFunction<ServerResponse> createRouteFunction(Route route, AuthManager authManager,
            GlobalMiddlewareRegistry globalMiddleware) {
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
                // 设置 AuthContext，使认证中间件能读取 Request 中的 Authorization 头
                AuthContext.set(customRequest);
                try {
                    // 合并中间件：全局中间件 + 路由中间件
                    List<Middleware> allMiddlewares = new ArrayList<>();
                    if (globalMiddleware != null) {
                        allMiddlewares.addAll(globalMiddleware.getMiddlewares());
                    }
                    allMiddlewares.addAll(route.getMiddlewares());

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
                    AuthContext.clear();
                    if (authManager != null) {
                        authManager.clear();
                    }
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
