package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.ControllerRegistry;
import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.route.Route;
import com.weacsoft.jaravel.vendor.route.Router;
import com.weacsoft.jaravel.vendor.springboot.annotation.MiddlewareAlias;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SpringBoot 路由自动装配（核心）：将 Jaravel {@link Router} 中注册的路由转换为
 * Spring {@link RouterFunction}，并在请求处理时执行中间件链。
 * <p>
 * 适配 Spring Boot 3.2.5 / Spring 6.x（jakarta.servlet、{@code org.springframework.web.servlet.function}）。
 * <p>
 * 同时负责：
 * <ul>
 *   <li><b>中间件别名扫描</b>：通过 classpath 扫描（非 Bean 扫描）发现 {@link MiddlewareAlias} 注解的类，
 *       反射实例化后注册到 {@link MiddlewareAliasRegistry} 全局注册表。
 *       中间件不是 Spring Bean，不需要 {@code @Component}。</li>
 *   <li><b>控制器注册</b>：扫描实现了 {@link Controllers} 接口的类以及标注了
 *       Spring {@code @Controller} / {@code @RestController} 的类，注册到 {@link ControllerRegistry}，
 *       使路由可通过字符串（{@code "ControllerName::method"}）或类对象引用控制器方法。
 *       同时设置回退解析器，当注册表中未找到控制器时从 Spring 容器按需解析。</li>
 * </ul>
 * <p>
 * 流程：
 * <ol>
 *   <li>扫描 classpath 中所有 {@link MiddlewareAlias} 注解的类，反射实例化并注册到 {@link MiddlewareAliasRegistry}</li>
 *   <li>扫描容器中所有 {@link Controllers} Bean，注册到 {@link ControllerRegistry}</li>
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
     * 注册 {@link SpringBootRequestMVCResolver} 为 Bean，使 Spring MVC 自动发现并注册为参数解析器。
     * <p>
     * 不使用 {@code @Component}，与同模块其他解析器风格统一（通过 AutoConfiguration 的 {@code @Bean} 注册）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringBootRequestMVCResolver requestMVCResolver() {
        return new SpringBootRequestMVCResolver();
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
        // 扫描 @MiddlewareAlias 注解的中间件类（classpath 扫描，非 Bean），注册到全局别名注册表
        scanMiddlewareAliases(applicationContext);
        // 扫描控制器并注册到 ControllerRegistry（支持 Controllers 接口和 Spring @Controller/@RestController）
        scanControllers(applicationContext);
        // 设置回退解析器：当控制器未在注册表中找到时，从 Spring 容器按需解析
        setupControllerFallbackResolver(applicationContext);

        List<Route> routes = router.getAllRoutes();
        RouterFunctions.Builder builder = RouterFunctions.route();
        routes.forEach(route -> {
            builder.route(createRoutePredicate(route), createRouteFunction(route, routeAuthHandler));
        });
        return builder.build();
    }

    /**
     * 通过 classpath 扫描发现 {@link MiddlewareAlias} 注解的中间件类，反射实例化后注册到
     * {@link MiddlewareAliasRegistry} 全局注册表。
     * <p>
     * 中间件不是 Spring Bean，不需要 {@code @Component}。扫描使用
     * {@link ClassPathScanningCandidateComponentProvider} + {@link AnnotationTypeFilter}，
     * 基础包由 {@link AutoConfigurationPackages} 确定（即 {@code @SpringBootApplication} 类所在包及其子包）。
     * <p>
     * 三种场景：
     * <ul>
     *   <li>未标注 {@code @MiddlewareAlias}：视为用户自建，不扫描</li>
     *   <li>标注但未填别名（{@code @MiddlewareAlias}）：仅注册类映射，路由通过类对象或类名引用</li>
     *   <li>标注且填了别名（{@code @MiddlewareAlias("auth")}）：同时注册别名映射和类映射</li>
     * </ul>
     *
     * @param applicationContext Spring 应用上下文
     */
    void scanMiddlewareAliases(ApplicationContext applicationContext) {
        if (applicationContext == null) return;

        List<String> basePackages = determineBasePackages(applicationContext);
        if (basePackages.isEmpty()) {
            log.debug("无法从 AutoConfigurationPackages 获取基础包，跳过中间件别名扫描");
            return;
        }
        scanMiddlewareAliases(applicationContext, basePackages);
    }

    /**
     * 扫描指定基础包中的 {@link MiddlewareAlias} 注解中间件类。
     * <p>
     * 供 {@link #scanMiddlewareAliases(ApplicationContext)} 内部调用及测试直接指定包名使用。
     *
     * @param applicationContext Spring 应用上下文（提供 ResourceLoader）
     * @param basePackages       要扫描的基础包列表
     */
    void scanMiddlewareAliases(ApplicationContext applicationContext, List<String> basePackages) {
        if (applicationContext == null || basePackages == null || basePackages.isEmpty()) return;

        MiddlewareAliasRegistry registry = MiddlewareAliasRegistry.getGlobal();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(MiddlewareAlias.class));
        scanner.setResourceLoader(applicationContext);
        if (applicationContext.getEnvironment() != null) {
            scanner.setEnvironment(applicationContext.getEnvironment());
        }

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate.getBeanClassName());
                    if (Middleware.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        Middleware instance = (Middleware) clazz.getDeclaredConstructor().newInstance();
                        MiddlewareAlias annotation = clazz.getAnnotation(MiddlewareAlias.class);
                        if (annotation != null) {
                            String alias = annotation.value();
                            registry.register(alias, instance);
                            log.debug("扫描注册中间件: {} (alias={})", clazz.getSimpleName(),
                                    alias.isEmpty() ? "<无别名>" : alias);
                        }
                    }
                } catch (Exception e) {
                    log.warn("无法实例化中间件: {}", candidate.getBeanClassName(), e);
                }
            }
        }
    }

    /**
     * 扫描控制器并注册到 {@link ControllerRegistry}。
     * <p>
     * 支持两种扫描模式：
     * <ul>
     *   <li><b>手动指定扫描包</b>（推荐）：用户在 RouteServiceProvider 中调用
     *       {@code ControllerRegistry.setScanBasePackages("com.example.controller")} 后，
     *       框架通过 classpath 扫描这些包下所有实现了 {@link Controllers} 的类
     *       以及标注了 Spring {@code @Controller} / {@code @RestController} 的类，
     *       使用 Spring 的 {@code AutowireCapableBeanFactory} 实例化并自动注入依赖。
     *       此模式下控制器不需要标注 {@code @Component}。</li>
     *   <li><b>自动扫描</b>（默认）：若未指定扫描包，框架扫描容器中所有实现了
     *       {@link Controllers} 的 Bean 以及标注了 {@code @Controller} / {@code @RestController}
     *       的 Bean。</li>
     * </ul>
     *
     * @param applicationContext Spring 应用上下文
     */
    void scanControllers(ApplicationContext applicationContext) {
        if (applicationContext == null) return;
        ControllerRegistry registry = ControllerRegistry.getGlobal();

        if (ControllerRegistry.hasScanBasePackages()) {
            // 手动指定扫描包模式：classpath 扫描 + AutowireCapableBeanFactory 实例化
            scanControllersByClasspath(applicationContext, ControllerRegistry.getScanBasePackages());
        } else {
            // 自动模式：扫描容器中所有 Controllers Bean
            Map<String, Controllers> beans = applicationContext.getBeansOfType(Controllers.class);
            beans.values().forEach(controller -> {
                registry.register(controller);
                log.debug("扫描注册控制器: {}", controller.getClass().getSimpleName());
            });
            // 同时扫描标注了 @Controller / @RestController 的 Bean（支持纯 Spring 控制器）
            scanSpringAnnotatedControllerBeans(applicationContext, registry);
        }
    }

    /**
     * 通过 classpath 扫描指定包中的控制器类，使用
     * {@code AutowireCapableBeanFactory} 实例化并自动注入依赖后注册到 {@link ControllerRegistry}。
     * <p>
     * 扫描范围包括：
     * <ul>
     *   <li>实现了 {@link Controllers} 接口的类</li>
     *   <li>标注了 Spring {@code @Controller} 或 {@code @RestController} 的类</li>
     * </ul>
     * 此模式下控制器不需要标注 {@code @Component}，框架通过反射发现类，
     * 通过 Spring 的自动装配能力完成依赖注入。
     *
     * @param applicationContext Spring 应用上下文
     * @param basePackages       要扫描的基础包列表
     */
    void scanControllersByClasspath(ApplicationContext applicationContext, List<String> basePackages) {
        if (applicationContext == null || basePackages == null || basePackages.isEmpty()) return;

        ControllerRegistry registry = ControllerRegistry.getGlobal();
        ConfigurableListableBeanFactory beanFactory =
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();

        // 扫描实现 Controllers 接口的类
        ClassPathScanningCandidateComponentProvider interfaceScanner =
                new ClassPathScanningCandidateComponentProvider(false);
        interfaceScanner.addIncludeFilter(new AssignableTypeFilter(Controllers.class));
        interfaceScanner.setResourceLoader(applicationContext);
        if (applicationContext.getEnvironment() != null) {
            interfaceScanner.setEnvironment(applicationContext.getEnvironment());
        }

        // 扫描标注 @Controller / @RestController 的类
        ClassPathScanningCandidateComponentProvider annotationScanner =
                new ClassPathScanningCandidateComponentProvider(false);
        annotationScanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        annotationScanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        annotationScanner.setResourceLoader(applicationContext);
        if (applicationContext.getEnvironment() != null) {
            annotationScanner.setEnvironment(applicationContext.getEnvironment());
        }

        for (String basePackage : basePackages) {
            // 扫描 Controllers 接口实现类
            Set<BeanDefinition> candidates = interfaceScanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                registerControllerCandidate(beanFactory, registry, candidate);
            }
            // 扫描 @Controller / @RestController 标注类
            Set<BeanDefinition> annotatedCandidates = annotationScanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : annotatedCandidates) {
                registerControllerCandidate(beanFactory, registry, candidate);
            }
        }
    }

    /**
     * 尝试注册一个控制器候选类，已注册的跳过。
     */
    private void registerControllerCandidate(ConfigurableListableBeanFactory beanFactory,
                                             ControllerRegistry registry, BeanDefinition candidate) {
        try {
            Class<?> clazz = Class.forName(candidate.getBeanClassName());
            if (clazz.isInterface()) return;
            // 跳过已注册的（避免 Controllers 接口扫描和注解扫描重复注册同一类）
            if (registry.isClassRegistered(clazz)) return;
            Object controller = beanFactory.createBean(clazz);
            registry.register(controller);
            log.debug("classpath 扫描注册控制器: {}", clazz.getSimpleName());
        } catch (Exception e) {
            log.warn("无法实例化控制器: {}", candidate.getBeanClassName(), e);
        }
    }

    /**
     * 扫描容器中标注了 Spring {@code @Controller} 或 {@code @RestController} 的 Bean，
     * 注册到 {@link ControllerRegistry}。已注册的跳过。
     *
     * @param applicationContext Spring 应用上下文
     * @param registry           控制器注册表
     */
    private void scanSpringAnnotatedControllerBeans(ApplicationContext applicationContext,
                                                     ControllerRegistry registry) {
        // 扫描 @Controller 标注的 Bean
        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(Controller.class);
        controllerBeans.values().forEach(controller -> {
            if (!registry.isClassRegistered(controller.getClass())) {
                registry.register(controller);
                log.debug("扫描注册 Spring @Controller: {}", controller.getClass().getSimpleName());
            }
        });
        // @RestController 被 @Controller 元标注，getBeansWithAnnotation(Controller.class) 已包含
    }

    /**
     * 从 {@link AutoConfigurationPackages} 获取基础包列表。
     * <p>
     * 返回 {@code @SpringBootApplication} 类所在包及其子包。
     * 若上下文未配置 AutoConfigurationPackages（如测试中的 StaticApplicationContext），返回空列表。
     */
    private List<String> determineBasePackages(ApplicationContext applicationContext) {
        try {
            return AutoConfigurationPackages.get(applicationContext);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 设置控制器回退解析器，使 {@link ControllerRegistry} 在注册表中未找到控制器时
     * 能从 Spring 容器按需解析。
     * <p>
     * 这为以下场景提供安全保障：
     * <ul>
     *   <li>使用 Spring {@code @Controller} / {@code @RestController} 但未被扫描到的控制器</li>
     *   <li>扫描时机晚于路由注册的边缘情况</li>
     * </ul>
     * 解析策略：按名称（简名或全限定名）在容器中查找匹配的 Bean。
     *
     * @param applicationContext Spring 应用上下文
     */
    void setupControllerFallbackResolver(ApplicationContext applicationContext) {
        if (applicationContext == null) return;

        ControllerRegistry.setFallbackResolver(name -> {
            // 1. 尝试按全限定名加载类并从容器获取 Bean
            try {
                Class<?> clazz = Class.forName(name);
                try {
                    return applicationContext.getBean(clazz);
                } catch (Exception ignored) {
                    // 不是 Spring Bean，跳过
                }
            } catch (ClassNotFoundException ignored) {
                // 不是全限定名，继续尝试
            }
            // 2. 遍历容器中所有 Bean，按简名或全限定名匹配
            String[] beanNames = applicationContext.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType != null &&
                        (beanType.getSimpleName().equals(name) || beanType.getName().equals(name))) {
                    // 只接受标注了 @Controller / @RestController 或实现了 Controllers 的类
                    if (beanType.isAnnotationPresent(Controller.class) ||
                            beanType.isAnnotationPresent(RestController.class) ||
                            Controllers.class.isAssignableFrom(beanType)) {
                        return applicationContext.getBean(beanName);
                    }
                }
            }
            return null;
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
