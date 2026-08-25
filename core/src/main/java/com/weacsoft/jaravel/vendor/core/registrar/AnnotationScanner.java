package com.weacsoft.jaravel.vendor.core.registrar;

import com.weacsoft.jaravel.vendor.core.lookup.BeanLookup;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * 注解方法扫描工具：遍历宿主容器中的 Bean，找出标注了指定注解的方法，
 * 反射调用（参数按类型自动注入）后把结果交给回调处理（零 Spring 依赖）。
 * <p>
 * 供需要扫描<b>多种注解</b>或需要<b>控制扫描顺序</b>的注册器使用
 * （如 auth 需先扫 {@code @RegisterProvider} 再扫 {@code @RegisterGuard}）。
 * 只扫描单一注解的场景请直接继承 {@link AnnotationDrivenRegistrar}。
 * <p>
 * <h3>Bean 解析</h3>
 * 构造时接受 {@link BeanLookup}（宿主注入；Spring 宿主传 {@code ContextBeanProvider}，
 * 保留 CGLIB 代理解包与合并注解解析）。
 *
 * <h3>设计说明</h3>
 * 扫描到的产物<b>不注册为宿主 Bean</b>，仅回传给调用方存入各模块自己的 Manager，
 * 因此组件名称与 bean name 解耦，避免同名 Bean 冲突。
 */
public class AnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotationScanner.class);

    private final BeanLookup lookup;

    public AnnotationScanner(BeanLookup lookup) {
        if (lookup == null) {
            throw new IllegalArgumentException("AnnotationScanner 需要非空 BeanLookup（宿主未安装可用 GlobalLookup.require()）");
        }
        this.lookup = lookup;
    }

    /**
     * 构造器：从 {@link GlobalLookup} 全局安装点取当前提供者（未安装时抛异常）。
     */
    public AnnotationScanner() {
        this(GlobalLookup.require());
    }

    /**
     * 扫描容器中所有标注了 {@code annotationType} 的方法并逐个调用。
     *
     * @param annotationType 目标注解类型
     * @param consumer       回调，接收（方法返回值, 注解实例）；返回值为 null 时不回调
     * @param <A>            注解类型
     */
    public <A extends Annotation> void scan(Class<A> annotationType, BiConsumer<Object, A> consumer) {
        scan(annotationType, (result, annotation, method) -> consumer.accept(result, annotation));
    }

    /**
     * 扫描容器中所有标注了 {@code annotationType} 的方法并逐个调用（回调可获知来源方法）。
     *
     * @param annotationType 目标注解类型
     * @param callback       回调，接收（方法返回值, 注解实例, 来源方法）
     * @param <A>            注解类型
     */
    public <A extends Annotation> void scan(Class<A> annotationType, ScanCallback<A> callback) {
        for (String beanName : lookup.beanNames()) {
            Object bean = lookup.beanQuiet(beanName);
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = lookup.targetClass(bean);
            for (Method method : targetClass.getMethods()) {
                A annotation = lookup.findAnnotation(method, annotationType);
                if (annotation == null) {
                    continue;
                }
                Object result = invoke(bean, method, annotationType);
                if (result == null) {
                    log.warn("@{} 方法 {}#{} 返回 null，已跳过",
                            annotationType.getSimpleName(),
                            targetClass.getSimpleName(), method.getName());
                    continue;
                }
                callback.accept(result, annotation, method);
            }
        }
    }

    /**
     * 反射调用注解方法，参数从容器按类型注入。
     */
    private Object invoke(Object bean, Method method, Class<? extends Annotation> annotationType) {
        try {
            method.setAccessible(true);
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                args[i] = lookup.bean(types[i]);
            }
            return method.invoke(bean, args);
        } catch (Exception e) {
            throw new RegistrarException("调用 @" + annotationType.getSimpleName() + " 方法失败: "
                    + method.getDeclaringClass().getName() + "." + method.getName() + "()", e);
        }
    }

    /**
     * 扫描回调。
     *
     * @param <A> 注解类型
     */
    @FunctionalInterface
    public interface ScanCallback<A extends Annotation> {

        /**
         * @param result     方法返回值，保证非 null
         * @param annotation 注解实例
         * @param method     来源方法
         */
        void accept(Object result, A annotation, Method method);
    }
}
