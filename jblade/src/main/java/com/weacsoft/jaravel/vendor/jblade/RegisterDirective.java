package com.weacsoft.jaravel.vendor.jblade;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册 Blade 自定义指令，对齐 Laravel 的
 * {@code Blade::directive()} 与 {@code Blade::if()}。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回
 * {@link BladeDirectives.Handler}（输出指令）或
 * {@link BladeDirectives.Condition}（条件指令）。
 * {@code BladeDirectiveRegistrar} 会在所有 Bean 初始化完成后扫描此注解，
 * 调用方法并把返回的处理器注册到 {@link BladeDirectives}。
 *
 * <h3>与 SessionStore / QueueDriver 的区别</h3>
 * 指令是<b>命名多实例</b>组件：一个应用可以注册任意多个不同名字的指令，
 * 因此本注解带名称参数 {@link #value()}，且不做唯一性限制——
 * 同名指令后注册者覆盖先注册者，与 Laravel 行为一致。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class ViewConfig {
 *
 *     // 输出指令：@datetime($order.createdAt)
 *     &#64;RegisterDirective("datetime")
 *     public BladeDirectives.Handler datetime() {
 *         return args -&gt; new SimpleDateFormat("yyyy-MM-dd").format(args[0]);
 *     }
 *
 *     // 条件指令：@admin ... @endadmin
 *     &#64;RegisterDirective(value = "admin", condition = true)
 *     public BladeDirectives.Condition admin(AuthManager auth) {
 *         return args -&gt; auth.guard().check();
 *     }
 * }
 * </pre>
 *
 * <h3>注册时机</h3>
 * 编译器需在<b>编译期</b>知道 {@code @xxx} 是条件指令还是输出指令，
 * 因此指令必须在模板编译前注册。本注解在所有单例 Bean 就绪后立即扫描，
 * 早于首次模板渲染，可满足要求。
 *
 * @see BladeDirectives
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterDirective {

    /**
     * 指令名称，不含 {@code @} 前缀。
     * <p>
     * 例如 {@code "datetime"} 对应模板中的 {@code @datetime(...)}。
     *
     * @return 指令名
     */
    String value();

    /**
     * 是否为条件指令。
     * <p>
     * {@code false}（默认）表示输出指令，方法须返回
     * {@link BladeDirectives.Handler}，返回值直接输出到模板；
     * {@code true} 表示条件指令，方法须返回
     * {@link BladeDirectives.Condition}，模板中可使用
     * {@code @name(...) ... @elsename(...) ... @endname}。
     *
     * @return 是否为条件指令，默认 {@code false}
     */
    boolean condition() default false;
}
