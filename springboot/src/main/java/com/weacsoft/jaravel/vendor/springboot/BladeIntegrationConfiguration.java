package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.request.RequestFactory;
import com.weacsoft.jaravel.vendor.http.middleware.VerifyCsrfToken;
import com.weacsoft.jaravel.vendor.jblade.BladeFunctions;
import com.weacsoft.jaravel.vendor.route.RouteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * jblade（Blade 模板引擎）集成装配 —— <b>P2-J 内部配置类</b>。
 * <p>
 * 仅当 classpath 存在 jblade 的 {@code BladeFunctions} 时整体生效；
 * 没有 jblade 的应用不加载任何 Blade 类（{@code SpringBootRouteAutoConfiguration} 的
 * 路由内核保持 jblade-free 编译期零引用）。
 * <p>
 * 职责（从 {@link SpringBootRouteAutoConfiguration} 拆出，行为与原先一致）：
 * <ul>
 *   <li>注册 {@link BladeDirectiveRegistrar}：扫描 {@code @RegisterDirective} 注解方法，
 *       把自定义指令注册到 {@code BladeDirectives}；</li>
 *   <li>向模板引擎注册开箱即用辅助函数 {@code csrf_token()} / {@code route()} / {@code url()}，
 *       注册后立即自检（硬保证：任一注册未落地则启动失败，而非静默不可用）。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.jblade.BladeFunctions")
public class BladeIntegrationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BladeIntegrationConfiguration.class);

    /**
     * 注册 {@link BladeDirectiveRegistrar}，扫描 {@code @RegisterDirective}
     * 注解方法并把自定义指令注册到 {@code BladeDirectives}。
     * <p>
     * 未声明任何 {@code @RegisterDirective} 时不注册任何指令，不影响启动。
     * <p>
     * P3 起 {@link BladeDirectiveRegistrar} 为 core 纯扫描器（零 Spring）：
     * 扫描时机由下方 {@code SmartInitializingSingleton} 触发（保持原「所有单例就绪后扫描」时序）。
     */
    @Bean
    @ConditionalOnMissingBean
    public BladeDirectiveRegistrar bladeDirectiveRegistrar() {
        return new BladeDirectiveRegistrar();
    }

    /**
     * 指令注册器扫描触发：所有单例初始化完成后执行 {@code @RegisterDirective} 扫描。
     */
    @Bean
    public SmartInitializingSingleton bladeDirectiveRegistrarScanner(BladeDirectiveRegistrar registrar) {
        return registrar::scan;
    }

    /**
     * 开箱即用模板辅助函数注册（依赖 jblade {@link BladeFunctions}）。
     * <p>
     * <b>硬保证（注册即可用）</b>：注册后立即自检——只要走了注册流程，模板辅助函数
     * 就必须确实生效；任一注册未落地将抛出 {@link IllegalStateException} 使应用启动失败
     * （而非悄悄留下“空 value / 空路由”的不可用状态），
     * 从而满足“一旦注册就必须自动可用、开发者零额外代码”的契约。
     */
    @Bean
    @ConditionalOnMissingBean
    public Runnable jaravelBladeTemplateBuiltins() {
        // 1) csrf_token() 辅助函数：从当前请求 session 读取/生成 token，与中间件校验同源
        BladeFunctions.register("csrf_token", args -> {
            try {
                Request req = RequestFactory.getCurrentRequest();
                return VerifyCsrfToken.currentToken(req);
            } catch (Exception e) {
                // 失败可见：记录 ERROR 而非静默吞掉，避免开发者拿到空 value 却无感知
                log.error("[builtin] csrf_token() 生成令牌失败", e);
                return "";
            }
        });

        // 2) route() 辅助函数：按路由别名解析 URL（对齐 Laravel route('name')）
        //    委托 RouteHelper.route()，与 Java 侧 AppConfig.app().route().route(...) 共用同一实现
        BladeFunctions.register("route", args -> {
            String name = String.valueOf(args[0]);
            Object params = args.length > 1 ? args[1] : null;
            return RouteHelper.route(name, params);
        });

        // 3) url() 辅助函数：按路径生成 URL，不校验是否存在（对齐 Laravel url('/path')）
        //    委托 RouteHelper.url()，与 Java 侧 AppConfig.app().route().url(...) 共用同一实现
        BladeFunctions.register("url", args -> RouteHelper.url(String.valueOf(args[0])));

        // 自检：模板辅助函数必须确实注册成功（硬保证，避免静默不可用）
        if (!BladeFunctions.has("csrf_token")) {
            throw new IllegalStateException("[builtin] 模板辅助函数 csrf_token() 注册失败，csrf_field() 将不可用。");
        }
        if (!BladeFunctions.has("route")) {
            throw new IllegalStateException("[builtin] 模板辅助函数 route() 注册失败，route() 将不可用。");
        }
        if (!BladeFunctions.has("url")) {
            throw new IllegalStateException("[builtin] 模板辅助函数 url() 注册失败，url() 将不可用。");
        }

        log.info("[builtin] 已注册模板辅助函数 csrf_token() / csrf_field() / route() / url()（开箱即用，自检通过）");
        return () -> { };
    }
}
