package com.weacsoft.jaravel.vendor.core.registrar;

import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 单实例注解注册器基类：用于「全局只允许注册一个」的组件，
 * 如 SessionStore、Blade 模板编译器、队列连接等。
 * <p>
 * 与 {@link AnnotationDrivenRegistrar} 的差异在于<b>唯一性约束</b>：
 * 若容器中存在多个同类注解方法，默认直接抛出 {@link RegistrarException}，
 * 避免出现「究竟哪个生效」的隐式歧义。
 *
 * <h3>唯一性冲突的解决方式</h3>
 * 注解可提供 {@code override = true} 属性（由子类通过
 * {@link #isOverride(Annotation)} 暴露）。被标记为覆盖的注册项优先生效，
 * 且允许覆盖一个已有注册，从而支持「框架提供默认、业务工程显式覆盖」的场景。
 *
 * @param <A> 注解类型
 * @param <T> 组件类型
 */
public abstract class SingletonRegistrar<A extends Annotation, T>
        extends AnnotationDrivenRegistrar<A> {

    /** 已登记的实例，null 表示尚未注册。 */
    private T registered;

    /** 已登记实例的来源描述，用于冲突时的报错信息。 */
    private String registeredFrom;

    /** 已登记实例是否由 override 注册项提供。 */
    private boolean registeredByOverride;

    private final Class<T> componentType;

    protected SingletonRegistrar(ApplicationContext context, Class<A> annotationType,
                                 Class<T> componentType) {
        super(context, annotationType);
        this.componentType = componentType;
    }

    @Override
    protected void register(Object result, Method method, A annotation) {
        T instance = requireType(result, componentType, method);
        String from = describe(method);
        boolean override = isOverride(annotation);

        if (registered != null) {
            if (override && registeredByOverride) {
                throw new RegistrarException(componentType.getSimpleName()
                        + " 存在多个 override 注册：" + registeredFrom + " 与 " + from
                        + "，请只保留一个。");
            }
            if (!override && !registeredByOverride) {
                throw new RegistrarException(componentType.getSimpleName()
                        + " 只允许注册一个，但发现多个：" + registeredFrom + " 与 " + from
                        + "。如需覆盖，请在其中一个注解上设置 override = true。");
            }
            // 已有 override 的情况下，忽略普通注册项
            if (!override) {
                log.debug("{} 已由 {} 覆盖注册，忽略 {}",
                        componentType.getSimpleName(), registeredFrom, from);
                return;
            }
        }

        this.registered = instance;
        this.registeredFrom = from;
        this.registeredByOverride = override;
        log.debug("注册 {}: {}{}", componentType.getSimpleName(), from, override ? "（覆盖）" : "");
    }

    @Override
    protected void afterScan() {
        if (registered != null) {
            apply(registered);
        } else {
            applyFallback();
        }
    }

    /**
     * 注解是否声明为覆盖式注册。子类根据自身注解属性返回，默认不支持覆盖。
     */
    protected boolean isOverride(A annotation) {
        return false;
    }

    /**
     * 应用扫描到的唯一实例。
     */
    protected abstract void apply(T instance);

    /**
     * 未扫描到任何注册项时的回退逻辑（「无则回退默认」）。
     * 默认不做任何事，由子类按需注册内存/文件等默认实现。
     */
    protected void applyFallback() {
    }
}
