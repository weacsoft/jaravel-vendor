package com.weacsoft.jaravel.vendor.core.registrar;

import com.weacsoft.jaravel.vendor.core.lookup.BeanLookup;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注解驱动注册器基类：统一 auth / cache / storage / session / view / queue 等模块
 * 「扫描注解方法 → 调用 → 注册产物」的通用流程（零 Spring 依赖）。
 * <p>
 * <h3>扫描时机</h3>
 * P3 起扫描与「容器何时就绪」解耦：宿主显式调用 {@link #scan()} 触发
 * （Spring 宿主在各模块自动装配中以 {@code SmartInitializingSingleton}
 * 包装 {@code registrar::scan}，保持「所有单例初始化完成后扫描」的原有时序）。
 * {@code scan()} 幂等：重复调用直接返回，保证唯一性注册器不被二次登记。
 *
 * <h3>Bean 解析</h3>
 * 扫描时读取 {@link GlobalLookup} 安装的 {@link GlobalBeanProvider}：
 * Spring 宿主安装 {@code ContextBeanProvider}（保留 CGLIB 代理解包与合并注解解析），
 * 非 Spring 宿主安装 Map 版实现即可。
 *
 * <h3>为什么产物不进宿主容器</h3>
 * 注解方法本身写在宿主配置类上，但其<b>返回的产物</b>只存入各模块自己的 Manager，
 * 不注册为宿主 Bean，把「组件名称」与「bean name」解耦，避免同名 Bean 冲突。
 *
 * @param <A> 注解类型，如 {@code @RegisterCacheStore}
 */
public abstract class AnnotationDrivenRegistrar<A extends Annotation> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Class<A> annotationType;

    /** 扫描幂等标记：保证唯一性注册器不被二次登记。 */
    private final AtomicBoolean scanned = new AtomicBoolean(false);

    protected AnnotationDrivenRegistrar(Class<A> annotationType) {
        this.annotationType = annotationType;
    }

    /**
     * 执行一次扫描（宿主在 Bean 就绪后调用；幂等，重复调用直接返回）。
     */
    public final void scan() {
        if (!scanned.compareAndSet(false, true)) {
            log.debug("@{} 注册器已扫描过，跳过重复扫描", annotationType.getSimpleName());
            return;
        }
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
     * 遍历容器内所有 Bean，查找标注了目标注解的方法。
     */
    private void scanAnnotatedMethods() {
        BeanLookup lookup = lookup();
        for (String beanName : lookup.beanNames()) {
            Object bean = lookup.beanQuiet(beanName);
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = lookup.targetClass(bean);
            // getMethods() 而非 getDeclaredMethods()：注解方法可能继承自父类，
            // 且 CGLIB 代理子类同样暴露父类方法，两种情况下 getDeclaredMethods() 都会漏扫。
            for (Method method : targetClass.getMethods()) {
                A annotation = lookup.findAnnotation(method, annotationType);
                if (annotation != null) {
                    invokeAndRegister(bean, method, annotation);
                }
            }
        }
    }

    /**
     * 取当前安装的 Bean 提供者（子类需要按类型枚举 Bean 时使用，
     * 如 storage / queue 驱动的兜底解析）。
     *
     * @return 已安装的 GlobalBeanProvider
     * @throws RegistrarException 未安装时
     */
    protected BeanLookup lookup() {
        GlobalBeanProvider provider = GlobalLookup.getIfInstalled();
        if (provider == null) {
            throw new RegistrarException("@" + annotationType.getSimpleName()
                    + " 注册器扫描失败：GlobalBeanProvider 未安装。"
                    + "Spring 宿主请确认 jaravel 核心自动装配已生效；"
                    + "非 Spring 宿主请先调用 GlobalLookup.install(...)。");
        }
        return provider;
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
     * 按类型从容器解析方法参数，行为与宿主 {@code @Bean} 方法参数注入一致。
     */
    private Object[] resolveArguments(Method method) {
        BeanLookup lookup = lookup();
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = lookup.bean(types[i]);
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
     * @param expected 期望类型
     * @param <T>      期望类型变量
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
