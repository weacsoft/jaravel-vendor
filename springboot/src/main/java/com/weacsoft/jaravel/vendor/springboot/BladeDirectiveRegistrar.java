package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.core.registrar.RegistrarException;
import com.weacsoft.jaravel.vendor.jblade.BladeDirectives;
import com.weacsoft.jaravel.vendor.jblade.RegisterDirective;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterDirective @RegisterDirective} 注解方法，
 * 把返回的处理器注册到 {@link BladeDirectives}。
 * <p>
 * 指令是<b>命名多实例</b>组件，因此继承 {@link AnnotationDrivenRegistrar}
 * 而非单实例注册器：一个应用可注册任意多个不同名字的指令。
 *
 * <h3>为什么放在 springboot 模块</h3>
 * jblade 模块刻意不依赖 core 与 Spring 上下文，保持模板引擎可独立使用。
 * 因此注解定义留在 jblade（供用户引用），而依赖 Spring 容器的扫描逻辑
 * 放在本模块，遵循「模块间有则使用、无则回退」的整体设计。
 */
public class BladeDirectiveRegistrar extends AnnotationDrivenRegistrar<RegisterDirective> {

    public BladeDirectiveRegistrar(ApplicationContext context) {
        super(context, RegisterDirective.class);
    }

    /**
     * 按 {@link RegisterDirective#condition()} 区分注册为条件指令或输出指令。
     */
    @Override
    protected void register(Object result, Method method, RegisterDirective annotation) {
        String name = annotation.value();
        if (name == null || name.isEmpty()) {
            throw new RegistrarException("@RegisterDirective 的指令名不能为空: " + describe(method));
        }

        if (annotation.condition()) {
            BladeDirectives.condition(name,
                    requireType(result, BladeDirectives.Condition.class, method));
            log.info("[jblade] @RegisterDirective 注册条件指令: @{} ... @end{}", name, name);
        } else {
            BladeDirectives.directive(name,
                    requireType(result, BladeDirectives.Handler.class, method));
            log.info("[jblade] @RegisterDirective 注册输出指令: @{}(...)", name);
        }
    }
}
