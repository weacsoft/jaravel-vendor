package com.weacsoft.jaravel.vendor.artisan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解式注册 Artisan 命令，对齐项目的 @RegisterGuard / @RegisterDisk 等模式。
 * <p>
 * 标注在 @Configuration / @AutoConfiguration 类的方法上，方法返回 {@link ArtisanCommand} 实例。
 * {@link CommandRegistrar} 会在所有单例初始化完成后扫描此注解，调用方法获取命令实例，
 * 并通过 {@link ArtisanApplication#register(ArtisanCommand)} 注册。
 * <p>
 * 命令实例<b>不进入 Spring 容器</b>，只存入 ArtisanApplication 的内部注册表，
 * 与 @RegisterGuard / @RegisterDisk 等注解的设计思路一致。
 *
 * @see ArtisanCommand
 * @see CommandRegistrar
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterCommand {
    /** 命令描述（可选，用于日志） */
    String value() default "";
}
