package com.weacsoft.jaravel.vendor.plugin.jar.remote.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * 基于 Spring ApplicationContext 的 BeanResolver 默认实现。
 * <p>
 * 当热加载插件系统（plugin-jar-core）不在类路径上时，远程执行系统使用此实现，
 * 直接从 Spring 容器中按 beanName 获取 Bean，忽略 pluginId 参数。
 * <p>
 * <h3>使用场景</h3>
 * 用户只引入 remote-server + remote-client（不引入 plugin-jar-core），
 * 即可在任何 SpringBoot 项目中使用远程执行功能，调用普通 Spring Bean 的方法。
 * <p>
 * <h3>与热加载结合</h3>
 * 当 plugin-jar-core 在类路径上时，自动装配会优先使用 HotPluginManager 作为 BeanResolver，
 * 从而支持按 pluginId 从不同插件的 ClassLoader 中获取 Bean。
 */
public class SpringBeanResolver implements BeanResolver {

    private static final Logger log = LoggerFactory.getLogger(SpringBeanResolver.class);

    private final ApplicationContext applicationContext;

    public SpringBeanResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        log.info("SpringBeanResolver 已初始化（无热加载模式，直接从 Spring 容器获取 Bean）");
    }

    @Override
    public Object getBean(String pluginId, String beanName) {
        if (beanName == null || beanName.isEmpty()) {
            return null;
        }
        try {
            return applicationContext.getBean(beanName);
        } catch (BeansException e) {
            log.debug("Spring 容器中未找到 Bean: {}", beanName);
            return null;
        }
    }
}
