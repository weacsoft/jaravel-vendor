package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.ControllerActionResolver;
import com.weacsoft.jaravel.vendor.http.controller.ControllerRegistry;
import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpringBootRouteAutoConfiguration} 中间件别名自动扫描 + 控制器扫描测试。
 * <p>
 * 验证 classpath 扫描机制能正确发现 {@code @MiddlewareAlias} 注解的中间件类（非 Spring Bean），
 * 反射实例化后注册到 {@link MiddlewareAliasRegistry} 全局注册表。
 * 同时验证控制器扫描注册到 {@link ControllerRegistry}，以及 {@link ControllerActionResolver}
 * 的字符串/类对象引用解析。
 * <p>
 * 覆盖三种中间件场景：有别名、无别名、null 上下文。
 */
class SpringBootRouteAutoConfigurationTest {

    private SpringBootRouteAutoConfiguration configuration;
    private static final String TEST_PACKAGE = "com.weacsoft.jaravel.vendor.springboot";

    @BeforeEach
    void setUp() {
        MiddlewareAliasRegistry.getGlobal().clear();
        ControllerRegistry.getGlobal().clear();
        ControllerRegistry.setFallbackResolver(null);
        ControllerActionResolver.clearCache();
        configuration = new SpringBootRouteAutoConfiguration();
    }

    @AfterEach
    void tearDown() {
        MiddlewareAliasRegistry.getGlobal().clear();
        ControllerRegistry.getGlobal().clear();
        ControllerRegistry.setFallbackResolver(null);
        ControllerActionResolver.clearCache();
    }

    // ========== 中间件 classpath 扫描测试 ==========

    @Test
    void testScanWithNullContext() {
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(null),
                "applicationContext 为 null 时应安全跳过");
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(null, List.of(TEST_PACKAGE)),
                "applicationContext 为 null 时应安全跳过（带 basePackages 重载）");
    }

    @Test
    void testScanWithNullBasePackages() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(context, null),
                "basePackages 为 null 时应安全跳过");
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(context, List.of()),
                "basePackages 为空列表时应安全跳过");
    }

    @Test
    void testScanWithoutAutoConfigurationPackagesSkipsScanning() {
        // StaticApplicationContext 没有 AutoConfigurationPackages，
        // scanMiddlewareAliases(context) 应安全跳过（不抛异常）
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();
        assertDoesNotThrow(() -> configuration.scanMiddlewareAliases(context),
                "无 AutoConfigurationPackages 时应安全跳过");
    }

    @Test
    void testScanSingleAliasMiddleware() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        // 使用 classpath 扫描（非 Bean 扫描），中间件不需要注册为 Spring Bean
        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"),
                "应注册别名 'auth'");
        assertTrue(MiddlewareAliasRegistry.getGlobal().isClassRegistered(TestAuthMiddleware.class),
                "应同时注册类映射");
    }

    @Test
    void testScanMultipleAliasMiddlewares() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("auth"));
        assertTrue(MiddlewareAliasRegistry.getGlobal().isRegistered("log"));
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size());
        assertEquals(3, MiddlewareAliasRegistry.getGlobal().getRegisteredClasses().size(),
                "应注册 3 个类映射（auth + log + noAlias）");
    }

    @Test
    void testScanNoAliasMiddleware() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        // 无别名的中间件不应注册别名
        assertFalse(MiddlewareAliasRegistry.getGlobal().isRegistered("TestNoAliasMiddleware"),
                "无别名中间件不应注册别名");
        // 但应注册类映射
        assertTrue(MiddlewareAliasRegistry.getGlobal().isClassRegistered(TestNoAliasMiddleware.class),
                "无别名中间件应注册类映射");
    }

    @Test
    void testScanMixedAliasAndNoAliasMiddlewares() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        // 只有 auth 和 log 注册了别名
        assertEquals(2, MiddlewareAliasRegistry.getGlobal().getRegisteredAliases().size(),
                "应有 2 个别名（auth + log）");
        // 三个类都注册了
        assertEquals(3, MiddlewareAliasRegistry.getGlobal().getRegisteredClasses().size(),
                "应注册 3 个类映射（auth + log + noAlias）");
    }

    @Test
    void testScannedAliasMiddlewareReceivesParams() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("auth:api");
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("api", response.getHeaders().get("X-Auth-Guard").get(0),
                "params[0] 应为 'api'");
    }

    @Test
    void testScannedNoAliasMiddlewareResolvedByClass() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        // 通过类对象解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve(TestNoAliasMiddleware.class);
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("true", response.getHeaders().get("X-NoAlias-Called").get(0),
                "无别名中间件应能通过类对象解析并执行");
    }

    @Test
    void testScannedNoAliasMiddlewareResolvedByClassName() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        // 通过类简名解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve("TestNoAliasMiddleware");
        assertNotNull(resolved, "应能通过类简名解析无别名中间件");
    }

    @Test
    void testScannedAliasMiddlewareAlsoResolvedByClass() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.scanMiddlewareAliases(context, List.of(TEST_PACKAGE));

        // 有别名的中间件也能通过类对象解析
        Middleware resolved = MiddlewareAliasRegistry.getGlobal().resolve(TestAuthMiddleware.class, "admin");
        assertNotNull(resolved);

        Middleware.NextFunction finalHandler = req -> ResponseBuilder.ok();
        Response response = resolved.handle(null, finalHandler);
        assertEquals("admin", response.getHeaders().get("X-Auth-Guard").get(0),
                "有别名的中间件也应能通过类对象+参数解析");
    }

    // ========== 控制器扫描测试 ==========

    @Test
    void testScanControllersWithNullContext() {
        assertDoesNotThrow(() -> configuration.scanControllers(null),
                "applicationContext 为 null 时应安全跳过");
    }

    @Test
    void testScanControllersRegistersBeans() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("testController", TestController.class);
        context.refresh();

        configuration.scanControllers(context);

        assertTrue(ControllerRegistry.getGlobal().isClassRegistered(TestController.class),
                "应注册 TestController 类映射");
        assertTrue(ControllerRegistry.getGlobal().isNameRegistered("TestController"),
                "应注册 TestController 名称映射");
    }

    @Test
    void testControllerActionResolverStringForm() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("testController", TestController.class);
        context.refresh();

        configuration.scanControllers(context);

        Controllers.Runner runner = ControllerActionResolver.resolve("TestController::ping");
        assertNotNull(runner, "字符串形式应解析成功");

        // 执行 Runner（使用 mock Request）
        Response response = runner.handle(null);
        assertNotNull(response);
        assertEquals("pong", response.getContent());
    }

    @Test
    void testControllerActionResolverClassForm() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("testController", TestController.class);
        context.refresh();

        configuration.scanControllers(context);

        Controllers.Runner runner = ControllerActionResolver.resolve(TestController.class, "ping");
        assertNotNull(runner, "类对象形式应解析成功");

        Response response = runner.handle(null);
        assertNotNull(response);
        assertEquals("pong", response.getContent());
    }

    @Test
    void testControllerActionResolverCaching() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("testController", TestController.class);
        context.refresh();

        configuration.scanControllers(context);

        Controllers.Runner runner1 = ControllerActionResolver.resolve("TestController::ping");
        Controllers.Runner runner2 = ControllerActionResolver.resolve("TestController::ping");
        assertSame(runner1, runner2, "相同引用应返回缓存的 Runner 实例");
    }

    @Test
    void testControllerActionResolverInvalidFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerActionResolver.resolve("InvalidFormat"),
                "格式错误应抛出 IllegalArgumentException");
    }

    @Test
    void testControllerActionResolverUnregisteredController() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerActionResolver.resolve("NonExistentController::method"),
                "未注册控制器应抛出 IllegalArgumentException");
    }

    @Test
    void testControllerActionResolverMethodNotFound() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("testController", TestController.class);
        context.refresh();

        configuration.scanControllers(context);

        assertThrows(IllegalArgumentException.class,
                () -> ControllerActionResolver.resolve(TestController.class, "nonExistentMethod"),
                "方法不存在应抛出 IllegalArgumentException");
    }

    // ========== Spring @Controller 扫描测试 ==========

    @Test
    void testScanControllersRegistersSpringAnnotatedBeans() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("springTestController", SpringTestController.class);
        context.refresh();

        configuration.scanControllers(context);

        assertTrue(ControllerRegistry.getGlobal().isClassRegistered(SpringTestController.class),
                "应注册标注了 @Controller 的 SpringTestController 类映射");
        assertTrue(ControllerRegistry.getGlobal().isNameRegistered("SpringTestController"),
                "应注册 SpringTestController 名称映射");
    }

    @Test
    void testSpringControllerActionResolverStringForm() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("springTestController", SpringTestController.class);
        context.refresh();

        configuration.scanControllers(context);

        Controllers.Runner runner = ControllerActionResolver.resolve("SpringTestController::index");
        assertNotNull(runner, "Spring @Controller 字符串形式应解析成功");

        Response response = runner.handle(null);
        assertNotNull(response);
        assertEquals("spring-index", response.getContent());
    }

    @Test
    void testScanControllersByClasspathFindsSpringAnnotated() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        // classpath 扫描应同时发现 Controllers 实现类和 @Controller 标注类
        configuration.scanControllersByClasspath(context, List.of(TEST_PACKAGE));

        assertTrue(ControllerRegistry.getGlobal().isNameRegistered("TestController"),
                "应通过 classpath 扫描注册 TestController");
        assertTrue(ControllerRegistry.getGlobal().isNameRegistered("SpringTestController"),
                "应通过 classpath 扫描注册 SpringTestController（@Controller 标注）");
    }

    // ========== 回退解析器测试 ==========

    @Test
    void testFallbackResolverResolvesUnregisteredController() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("springTestController", SpringTestController.class);
        context.refresh();

        // 设置回退解析器但不调用 scanControllers
        configuration.setupControllerFallbackResolver(context);

        // 通过回退解析器解析未扫描注册的控制器
        Object controller = ControllerRegistry.getGlobal().resolve("SpringTestController");
        assertNotNull(controller, "回退解析器应能从 Spring 容器解析未注册的控制器");
        assertTrue(controller instanceof SpringTestController);
    }

    @Test
    void testFallbackResolverResolvesByFullName() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("springTestController", SpringTestController.class);
        context.refresh();

        configuration.setupControllerFallbackResolver(context);

        String fullName = SpringTestController.class.getName();
        Object controller = ControllerRegistry.getGlobal().resolve(fullName);
        assertNotNull(controller, "回退解析器应能通过全限定名解析控制器");
    }

    @Test
    void testFallbackResolverReturnsNullForNonController() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        configuration.setupControllerFallbackResolver(context);

        // 非控制器名称应抛出异常（回退解析器返回 null）
        assertThrows(IllegalArgumentException.class,
                () -> ControllerRegistry.getGlobal().resolve("NonExistentController"),
                "回退解析器找不到时应抛出 IllegalArgumentException");
    }

    @Test
    void testFallbackResolverCachesResult() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("springTestController", SpringTestController.class);
        context.refresh();

        configuration.setupControllerFallbackResolver(context);

        // 首次解析触发回退，自动注册到注册表
        Object controller1 = ControllerRegistry.getGlobal().resolve("SpringTestController");
        assertNotNull(controller1);

        // 第二次解析应直接命中注册表缓存
        Object controller2 = ControllerRegistry.getGlobal().resolve("SpringTestController");
        assertSame(controller1, controller2, "回退解析后应缓存到注册表");
    }

    @Test
    void testSetupFallbackResolverWithNullContext() {
        assertDoesNotThrow(() -> configuration.setupControllerFallbackResolver(null),
                "applicationContext 为 null 时应安全跳过");
    }
}
