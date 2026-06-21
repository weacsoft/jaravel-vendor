package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为插件组件（Bean）。
 * <p>
 * 被此注解标记的类将在插件启用时被实例化并注册为 Spring Bean，
 * 可通过 {@link #value()} 指定 Bean 名称，默认使用类名首字母小写。
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @PluginComponent}
 * public class UserController {
 *     {@literal @PluginMapping(path = "/users", method = HttpMethod.GET)}
 *     public Response list(Request request) { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginComponent {

    /**
     * Bean 名称，默认为空字符串（表示使用类名首字母小写作为 Bean 名称）。
     */
    String value() default "";
}
