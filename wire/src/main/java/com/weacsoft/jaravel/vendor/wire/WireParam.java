package com.weacsoft.jaravel.vendor.wire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 WireController action 方法参数上，指定前端传参时的参数名。
 * <p>
 * 优先级最高：有注解时按注解值匹配，无视编译参数名保留设置。
 * <p>
 * 典型用途：方法参数名与前端期望的 key 不一致时，显式指定映射。
 *
 * @see WireController#invokeAction(String, java.util.Map)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireParam {

    /**
     * 参数名。为空时使用运行时参数名（Java 8+ -parameters）。
     */
    String value() default "";
}
