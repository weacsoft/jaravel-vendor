package com.weacsoft.jaravel.vendor.core.registrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 注解驱动注册器基类：统一 auth / cache / storage / session / view / queue 等模块
 * 「扫描注解方法 → 调用 → 注册产物」的通用流程。
 * <p>
 * 实现 {@link SmartInitializingSingleton}，在所有单例 Bean 初始化完成后执行扫描，
 * 此时容器已就绪，可安全地将其他 Bean 作为注解方法的参数注入。
 *
 * <h3>为什么产物不进 Spring 容器</h3>
 * {@code @Bean("admin")} 的 bean name 在容器内全局唯一，多个模块若都想注册名为
 * {@code admin} 的组件会触发 {@code BeanDefinitionOverrideException}。
 * 本机制把「组件名称」与「bean name」解耦：注解方法本身写在 Spring 配置类上，
 * 但其<b>返回的产物</b>只存入各模块自己的 Manager，不注册为 Spring Bean。
 *
 * <h3>子类职责</h3>
 * 子类只需实现 {@link #register(Object, Method, Annotation)}，把调用结果登记到
 * 各自的 Manager；扫描、参数注入、异常包装等由本类统一处理。
 *
 * @param <A> 注解类型，如 {@code @RegisterCacheStore}
 */
public abstract class AnnotationDrivenRegistrar<A extends Annotation>
        implements SmartInitializingSingleton {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ApplicationContext context;

    private final Class<A> annotationType;

    protected AnnotationDrivenRegistrar(ApplicationContext context, Class<A> annotationType) {
        this.context = context;
        this.annotationType = annotationType;
    }

    @Override
    public void afterSingletonsInstantiated() {
        beforeScan();
        scanAnnotatedMethods();
        afterScan();
    }

    /**
     * 扫描前回调，通常用于注册驱动、加载配置式定义。
     */
    protected void beforeScan() {
    }

    /**
     * 扫描后回调，通常用于注册兜底默认实现（「无则回退」）。
     */
    protected void afterScan() {
    }

    /**
     * 遍历容器中所有 Bean，查找标注了目标注解的方法。
     */
    private void scanAnnotatedMethods() {
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = resolveBeanQuietly(beanName);
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            // 使用 getMethods() 而非 getDeclaredMethods()：@Configuration 类会被 CGLIB 代理，
            // 且注解方法可能继承自父类，两种情况下 getDeclaredMethods() 都会漏扫。
            for (Method method : targetClass.getMethods()) {
                A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
                if (annotation != null) {
                    invokeAndRegister(bean, method, annotation);
                }
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
     * 调用注解方法（自动注入参数）并交由子类登记产物。
     */
    private void invokeAndRegister(Object bean, Method method, A annotation) {
        Object result;
        try {
            method.setAccessible(true);
            result = method.invoke(bean, resolveArguments(method));
        } catch (Exception e) {
            throw new RegistrarException("调用 @" + annotationType.getSimpleName() + " 方法失败: "
                    + describe(method), e);
        }

        if (result == null) {
            log.warn("@{} 方法 {} 返回 null，已跳过", annotationType.getSimpleName(), describe(method));
            return;
        }
        register(result, method, annotation);
    }

    /**
     * 按类型从容器解析方法参数，行为与 {@code @Bean} 方法参数注入一致。
     */
    private Object[] resolveArguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = context.getBean(types[i]);
        }
        return args;
    }

    /**
     * 登记注解方法的返回产物。
     *
     * @param result     方法返回值，保证非 null
     * @param method     注解所在方法，用于异常信息
     * @param annotation 注解实例，可读取名称等属性
     */
    protected abstract void register(Object result, Method method, A annotation);

    /**
     * 生成 {@code 类名#方法名} 形式的描述，用于日志与异常信息。
     */
    protected String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }

    /**
     * 校验返回值类型，不匹配时抛出带有清晰上下文的异常。
     *
     * @return 强转后的结果
     */
    protected <T> T requireType(Object result, Class<T> expected, Method method) {
        if (!expected.isInstance(result)) {
            throw new RegistrarException("@" + annotationType.getSimpleName() + " 方法 "
                    + describe(method) + " 的返回类型必须是 " + expected.getSimpleName()
                    + "，实际为 " + result.getClass().getName());
        }
        return expected.cast(result);
    }
}
