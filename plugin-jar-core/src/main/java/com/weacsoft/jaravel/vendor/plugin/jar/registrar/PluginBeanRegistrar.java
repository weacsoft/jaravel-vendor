package com.weacsoft.jaravel.vendor.plugin.jar.registrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Set;

/**
 * 插件 Bean 注册器。
 * <p>
 * 负责将插件中的组件类（标注 {@code @PluginComponent}）注册为 Spring Bean，
 * 以及在插件禁用时注销这些 Bean。
 * <p>
 * 使用 {@link DefaultListableBeanFactory} 注册单例 Bean，通过 {@link GenericBeanDefinition}
 * 描述 Bean 元信息。注册时立即实例化并放入单例缓存，避免重复创建。
 */
public class PluginBeanRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PluginBeanRegistrar.class);

    private final ConfigurableApplicationContext applicationContext;

    /**
     * 构造 Bean 注册器。
     *
     * @param applicationContext Spring 应用上下文
     */
    public PluginBeanRegistrar(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 返回应用上下文。
     *
     * @return 应用上下文
     */
    public ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 注册单例 Bean。
     * <p>
     * 使用 {@link GenericBeanDefinition} 注册 Bean 定义，并立即实例化放入单例缓存。
     * 若同名 Bean 已存在，先销毁再注册，以支持热重载。
     *
     * @param beanName  Bean 名称
     * @param beanClass Bean 类（由插件 ClassLoader 加载）
     * @return 注册成功返回 true，实例化失败返回 false
     */
    public boolean registerBean(String beanName, Class<?> beanClass) {
        DefaultListableBeanFactory beanFactory = getDefaultBeanFactory();
        try {
            // 若已存在则先销毁
            if (beanFactory.containsBeanDefinition(beanName)) {
                beanFactory.removeBeanDefinition(beanName);
            }
            GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(beanClass);
            definition.setScope("singleton");
            definition.setAutowireCandidate(true);
            beanFactory.registerBeanDefinition(beanName, definition);
            // 立即实例化，便于后续直接获取
            beanFactory.getBean(beanName);
            log.debug("注册插件 Bean: {} -> {}", beanName, beanClass.getName());
            return true;
        } catch (Exception e) {
            log.error("注册 Bean 失败: {}", beanName, e);
            // 注册失败时清理已注册的定义
            if (beanFactory.containsBeanDefinition(beanName)) {
                beanFactory.removeBeanDefinition(beanName);
            }
            return false;
        }
    }

    /**
     * 注销单个 Bean。
     *
     * @param beanName Bean 名称
     */
    public void unregisterBean(String beanName) {
        if (beanName == null) {
            return;
        }
        DefaultListableBeanFactory beanFactory = getDefaultBeanFactory();
        try {
            if (beanFactory.containsBeanDefinition(beanName)) {
                beanFactory.removeBeanDefinition(beanName);
            }
        } catch (Exception e) {
            log.error("注销 Bean 失败: {}", beanName, e);
        }
    }

    /**
     * 批量注销 Bean。
     *
     * @param beanNames Bean 名称集合
     */
    public void unregisterBeans(Set<String> beanNames) {
        if (beanNames == null || beanNames.isEmpty()) {
            return;
        }
        for (String beanName : beanNames) {
            unregisterBean(beanName);
        }
    }

    private DefaultListableBeanFactory getDefaultBeanFactory() {
        return (DefaultListableBeanFactory) applicationContext.getBeanFactory();
    }
}
