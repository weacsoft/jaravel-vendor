package com.weacsoft.jaravel.vendor.plugin.jar.remote.spi;

/**
 * Bean 解析器接口（远程执行系统的 SPI）。
 * <p>
 * 远程执行系统通过此接口获取目标 Bean，从而与具体的 Bean 来源（插件管理器或 Spring 容器）解耦。
 * <p>
 * <h3>两种使用场景</h3>
 * <ul>
 *   <li><b>配合热加载插件</b>：由 {@code HotPluginManager} 实现此接口，
 *       根据 pluginId 从对应插件的 ClassLoader 中获取 Bean</li>
 *   <li><b>独立使用（无热加载）</b>：由 {@code SpringBeanResolver} 实现，
 *       直接从 Spring ApplicationContext 中按 beanName 获取 Bean，忽略 pluginId</li>
 * </ul>
 * <p>
 * <h3>设计目的</h3>
 * 远程执行系统可以独立于热加载插件系统使用：
 * <ul>
 *   <li>仅引入 remote-server + remote-client：使用 SpringBeanResolver，执行普通 Spring Bean</li>
 *   <li>同时引入 plugin-jar-core：使用 HotPluginManager 作为 BeanResolver，执行插件 Bean</li>
 * </ul>
 */
public interface BeanResolver {

    /**
     * 根据 pluginId 和 beanName 获取 Bean 实例。
     * <p>
     * 在插件模式下，pluginId 用于定位插件的 ClassLoader；
     * 在非插件模式下，pluginId 被忽略，直接按 beanName 从 Spring 容器获取。
     *
     * @param pluginId 插件 ID（非插件模式可传 null 或空字符串）
     * @param beanName Bean 名称
     * @return Bean 实例，不存在返回 null
     */
    Object getBean(String pluginId, String beanName);
}
