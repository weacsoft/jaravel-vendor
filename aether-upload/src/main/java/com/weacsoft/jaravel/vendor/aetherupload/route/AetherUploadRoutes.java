package com.weacsoft.jaravel.vendor.aetherupload.route;

import com.weacsoft.jaravel.vendor.aetherupload.AetherUploadManager;
import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import com.weacsoft.jaravel.vendor.core.Facade;
import com.weacsoft.jaravel.vendor.route.Route;

import java.util.ArrayList;
import java.util.List;

/**
 * AetherUpload 路由注册器。
 * <p>
 * 框架的路由树由应用的 RouteServiceProvider 构建（{@code Route.setRootRouter}），
 * 因此模块路由需要应用在路由定义处显式挂载 —— 这同时天然支持「自定义上传端点」：
 * <pre>
 * // RouteServiceProvider / routes 定义中：
 *
 * // 1. 默认注册（前缀取配置 jaravel.aether-upload.route-prefix，默认 aetherupload）
 * AetherUploadRoutes.register();
 *
 * // 2. 自定义前缀（端点）
 * AetherUploadRoutes.register("api/upload");
 *
 * // 3. 自定义前缀 + 附加中间件（别名或中间件类）
 * AetherUploadRoutes.register("api/upload", "auth:api", "throttle:60,1");
 * </pre>
 * 每个上传组注册一组独立路由 {@code {prefix}/{group}/...}，
 * 组配置中的 {@code middleware} 与全局 {@code jaravel.aether-upload.middleware}
 * 及 register 传入的附加中间件叠加生效。
 */
public final class AetherUploadRoutes {

    /** 控制器全限定名（ControllerRegistry 按全限定名解析，无需依赖扫描包配置）；
     *  控制器位于 springboot 模块（AetherUploadController 使用 Spring Web MultipartFile） */
    private static final String C =
            "com.weacsoft.jaravel.vendor.springboot.aetherupload.AetherUploadController";

    private AetherUploadRoutes() {
    }

    /**
     * 使用配置的 route-prefix 注册上传路由。
     */
    public static void register() {
        register(null);
    }

    /**
     * 使用自定义前缀注册上传路由。
     *
     * @param prefix          路由前缀（null 时取配置 route-prefix）
     * @param extraMiddleware 附加中间件（String 别名表达式或 Class 中间件类），应用到所有上传端点
     */
    public static void register(String prefix, Object... extraMiddleware) {
        AetherUploadProperties props = Facade.resolve(AetherUploadProperties.class);
        AetherUploadManager manager = Facade.resolve(AetherUploadManager.class);
        String routePrefix = (prefix == null || prefix.isEmpty()) ? props.getRoutePrefix() : prefix;

        // 全局中间件（配置） + register 附加中间件
        List<Object> globalMiddleware = new ArrayList<>();
        if (props.getMiddleware() != null) {
            globalMiddleware.addAll(props.getMiddleware());
        }
        if (extraMiddleware != null) {
            for (Object m : extraMiddleware) {
                if (m != null) {
                    globalMiddleware.add(m);
                }
            }
        }

        Route.prefix(routePrefix).group(() -> {
            // 前端组件与演示页（不加业务中间件）
            Route.get("aether-upload.js", C + "::script").name("aetherupload.script");
            Route.get("demo", C + "::demo").name("aetherupload.demo");

            // 每个上传组一套端点，叠加 全局 + 组 中间件
            for (String groupName : manager.groupNames()) {
                List<Object> middlewares = new ArrayList<>(globalMiddleware);
                List<String> groupMw = manager.group(groupName).config.getMiddleware();
                if (groupMw != null) {
                    middlewares.addAll(groupMw);
                }

                Runnable endpoints = () -> {
                    Route.get("config", C + "::config").name("aetherupload." + groupName + ".config");
                    Route.post("prepare", C + "::prepare").name("aetherupload." + groupName + ".prepare");
                    Route.post("chunk", C + "::chunk").name("aetherupload." + groupName + ".chunk");
                    Route.get("progress", C + "::progress").name("aetherupload." + groupName + ".progress");
                    Route.post("abort", C + "::abort").name("aetherupload." + groupName + ".abort");
                    Route.post("sync", C + "::sync").name("aetherupload." + groupName + ".sync");
                };

                if (middlewares.isEmpty()) {
                    Route.prefix(groupName).group(endpoints);
                } else {
                    Route.GroupBuilder builder = Route.prefix(groupName);
                    for (Object m : middlewares) {
                        if (m instanceof Class<?>) {
                            builder.middleware((Class<?>) m);
                        } else {
                            builder.middleware(String.valueOf(m));
                        }
                    }
                    builder.group(endpoints);
                }
            }
        });
    }
}
