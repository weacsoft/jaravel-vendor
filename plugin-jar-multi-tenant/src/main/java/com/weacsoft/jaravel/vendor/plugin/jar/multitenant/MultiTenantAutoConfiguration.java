package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure.PluginJarProperties;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * 多租户插件自动装配。
 * <p>
 * 通过 {@link AutoConfigureBefore} 在 {@code PluginJarAutoConfiguration} 之前执行，
 * 提供租户感知版本的 {@link PluginBeanRegistrar}、{@link PluginRouteRegistrar} 和
 * {@link HotPluginManager} Bean。由于原版使用 {@code @ConditionalOnMissingBean}，
 * 原版自动装配会跳过这些 Bean 的创建，仅创建 {@link MetadataPersistence} 和
 * {@link PluginIntegration} 等未被覆盖的 Bean。
 * <p>
 * <h3>激活条件</h3>
 * <ul>
 *   <li>classpath 中存在 {@code plugin-jar-multi-tenant}（引入本 Maven 依赖）</li>
 *   <li>{@code jaravel.plugin-jar.multi-tenant.enabled=true}（默认 true）</li>
 *   <li>Web Servlet 环境</li>
 * </ul>
 * <p>
 * <h3>不引入本模块时</h3>
 * 系统使用原版 {@code PluginJarAutoConfiguration}，行为完全不变（单例插件模式）。
 * <p>
 * <h3>引入本模块后</h3>
 * <ul>
 *   <li>pluginId 不含分隔符（如 {@code blog}）：行为与原版一致（无租户前缀）</li>
 *   <li>pluginId 含分隔符（如 {@code studentA@blog}）：自动按租户隔离 Bean 和路由</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure.PluginJarAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HotPluginManager.class)
@ConditionalOnProperty(prefix = "jaravel.plugin-jar.multi-tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MultiTenantProperties.class)
public class MultiTenantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiTenantAutoConfiguration.class);

    /**
     * 租户感知的 Bean 注册器。
     * <p>
     * 覆盖原版 {@code PluginBeanRegistrar}，在注册/注销 Bean 时自动添加租户前缀。
     *
     * @param applicationContext Spring 应用上下文
     * @return 租户感知的 Bean 注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginBeanRegistrar pluginBeanRegistrar(ConfigurableApplicationContext applicationContext) {
        log.info("启用多租户插件 Bean 注册器");
        return new TenantAwarePluginBeanRegistrar(applicationContext);
    }

    /**
     * 租户感知的路由注册器。
     * <p>
     * 覆盖原版 {@code PluginRouteRegistrar}，在注册路由时自动添加租户前缀到路径和 Bean 名称。
     *
     * @param handlerMapping Spring MVC 的 RequestMappingHandlerMapping
     * @param beanRegistrar  Bean 注册器
     * @param integration    jaravel 集成
     * @return 租户感知的路由注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginRouteRegistrar pluginRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                                                     PluginBeanRegistrar beanRegistrar,
                                                     PluginIntegration integration) {
        log.info("启用多租户插件路由注册器");
        return new TenantAwarePluginRouteRegistrar(handlerMapping, beanRegistrar, integration);
    }

    /**
     * 租户感知的热插件管理器。
     * <p>
     * 覆盖原版 {@code HotPluginManager}在启用/禁用插件时注入租户上下文。
     * 初始化逻辑与原版一致：定位核心 JAR、初始化共享 ClassLoader、自动恢复插件。
     *
     * @param properties     插件配置
     * @param beanRegistrar  Bean 注册器
     * @param routeRegistrar 路由注册器
     * @param persistence    元数据持久化（由原版 PluginJarAutoConfiguration 提供）
     * @param integration    jaravel 集成（由原版 PluginJarAutoConfiguration 提供）
     * @param mtProperties   多租户配置
     * @return 租户感知的热插件管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public HotPluginManager hotPluginManager(PluginJarProperties properties,
                                             PluginBeanRegistrar beanRegistrar,
                                             PluginRouteRegistrar routeRegistrar,
                                             MetadataPersistence persistence,
                                             PluginIntegration integration,
                                             MultiTenantProperties mtProperties) {
        Path pluginsDir = resolvePluginsDir(properties);
        TenantAwareHotPluginManager manager = new TenantAwareHotPluginManager(
                pluginsDir, beanRegistrar, routeRegistrar, persistence, integration,
                properties.isAutoRegister(), mtProperties.getSeparator());

        // 定位核心 JAR 路径（使注解类对插件可见）
        Path coreJarPath = locateCoreJarPath();
        if (coreJarPath != null) {
            manager.setCoreJarPath(coreJarPath);
            manager.initSharedClassLoader(coreJarPath, "0.1.1");
            log.info("多租户插件管理器初始化: separator={}, coreJar={}", mtProperties.getSeparator(), coreJarPath);
        } else {
            log.warn("无法定位核心 JAR 路径，插件注解类可能无法被插件加载");
        }

        // 自动恢复已启用的插件
        if (properties.isAutoRestore()) {
            try {
                manager.loadPersistedPlugins();
            } catch (Exception e) {
                log.error("自动恢复插件失败", e);
            }
        }
        return manager;
    }

    /**
     * 解析插件目录路径。
     */
    private Path resolvePluginsDir(PluginJarProperties properties) {
        Path pluginsDir = Paths.get(properties.getPluginsDir());
        try {
            Files.createDirectories(pluginsDir);
        } catch (Exception e) {
            log.warn("创建插件目录失败: {}", pluginsDir, e);
        }
        return pluginsDir;
    }

    /**
     * 定位核心 JAR 路径。
     * <p>
     * 通过本类的 {@link ProtectionDomain} 获取其所在 JAR/目录。
     * 逻辑与 {@code PluginJarAutoConfiguration.locateCoreJarPath()} 一致，
     * 因原方法为 private，此处复制实现。
     */
    private Path locateCoreJarPath() {
        try {
            ProtectionDomain pd = MultiTenantAutoConfiguration.class.getProtectionDomain();
            if (pd == null) {
                return null;
            }
            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                return null;
            }
            URL location = cs.getLocation();
            if (location == null) {
                return null;
            }
            File file = new File(location.toURI());
            if (!file.exists()) {
                return null;
            }
            if (file.isFile() && file.getName().endsWith(".jar")) {
                return file.toPath();
            }
            if (file.isDirectory()) {
                return file.toPath();
            }
            return null;
        } catch (Exception e) {
            log.warn("定位核心 JAR 路径失败", e);
            return null;
        }
    }
}
