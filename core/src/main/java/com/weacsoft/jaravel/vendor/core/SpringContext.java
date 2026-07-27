package com.weacsoft.jaravel.vendor.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有器。
 * <p>
 * 供 {@link Facade} 门面静态访问容器中的 Bean，模仿 Laravel 的 Facade 机制：
 * 门面是一个静态代理，背后真正干活的是容器里解析出的实例。
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContext.context = applicationContext;
    }

    public static ApplicationContext get() {
        if (context == null) {
            throw new IllegalStateException("SpringContext 尚未初始化，ApplicationContext 未注入");
        }
        return context;
    }

    public static <T> T bean(Class<T> type) {
        return get().getBean(type);
    }

    public static <T> T bean(String name, Class<T> type) {
        return get().getBean(name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T bean(String name) {
        return (T) get().getBean(name);
    }

    public static boolean contains(String name) {
        return get().containsBean(name);
    }

    /**
     * 运行时注册/替换单例 Bean。
     * <p>
     * 如果同名 Bean 已存在，先销毁旧实例再注册新实例，实现「更新」语义。
     * 注册后可通过 {@code getBean(name)} 或 {@code getBean(type)} 获取。
     * <p>
     * <b>注意</b>：此方法在容器刷新后调用，不会触发已注入的 {@code @Autowired} 字段重新绑定，
     * 但后续 {@code getBean()} 调用会返回新实例。
     *
     * @param name Bean 名称
     * @param bean Bean 实例
     */
    public static void registerSingleton(String name, Object bean) {
        ConfigurableListableBeanFactory factory =
                ((ConfigurableApplicationContext) get()).getBeanFactory();
        // 已存在同名单例时先销毁，避免 IllegalStateException
        if (factory.containsSingleton(name)) {
            ((DefaultListableBeanFactory) factory).destroySingleton(name);
        }
        factory.registerSingleton(name, bean);
    }
}
