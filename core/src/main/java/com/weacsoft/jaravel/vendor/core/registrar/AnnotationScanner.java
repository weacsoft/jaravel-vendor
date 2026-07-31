package com.weacsoft.jaravel.vendor.core.registrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * 注解方法扫描工具：遍历 Spring 容器中的 Bean，找出标注了指定注解的方法，
 * 反射调用（参数按类型自动注入）后把结果交给回调处理。
 * <p>
 * 供需要扫描<b>多种注解</b>或需要<b>控制扫描顺序</b>的注册器使用
 * （如 auth 需先扫 {@code @RegisterProvider} 再扫 {@code @RegisterGuard}）。
 * 只扫描单一注解的场景请直接继承 {@link AnnotationDrivenRegistrar}。
 *
 * <h3>设计说明</h3>
 * 扫描到的产物<b>不会注册为 Spring Bean</b>，仅回传给调用方存入各模块自己的 Manager，
 * 因此组件名称与 bean name 解耦，不会触发 {@code BeanDefinitionOverrideException}。
 */
public class AnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotationScanner.class);

    private final ApplicationContext context;

    public AnnotationScanner(ApplicationContext context) {
        this.context = context;
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
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = resolveBeanQuietly(beanName);
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getMethods()) {
                A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
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
     * 安全获取 Bean，忽略懒加载失败/作用域不匹配等异常，避免影响启动。
     */
    private Object resolveBeanQuietly(String beanName) {
        try {
            return context.getBean(beanName);
        } catch (Exception e) {
            log.trace("跳过无法解析的 Bean: {}", beanName);
            return null;
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
                args[i] = context.getBean(types[i]);
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
