package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link PluginAlias} 的容器注解，支持 {@code @Repeatable}。
 * <p>
 * 当一个方法上存在多个 {@code @PluginAlias} 时，编译器会自动包装为此注解。
 * 无需手动使用。
 *
 * @see PluginAlias
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginAliases {
    PluginAlias[] value();
}
