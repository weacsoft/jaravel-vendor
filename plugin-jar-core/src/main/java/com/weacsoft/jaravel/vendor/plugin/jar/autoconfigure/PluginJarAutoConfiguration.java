package com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.DefaultPluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.JsonMetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * 插件 JAR 系统自动装配。
 * <p>
 * 在 Spring Boot 启动时创建 {@link PluginBeanRegistrar}、{@link PluginRouteRegistrar}、
 * {@link HotPluginManager} 等核心 Bean，并可选地自动恢复已启用的插件。
 * <p>
 * 所有 Bean 均使用 {@code @ConditionalOnMissingBean}，使 {@code plugin-jar-database}
 * 等扩展模块可覆盖默认实现（如提供数据库持久化、jaravel 集成）。
 * <p>
 * <h3>核心 JAR 定位</h3>
 * {@link #locateCoreJarPath()} 定位 plugin-jar-core 所在的 JAR（或 fat jar），
 * 使注解类（{@code com.weacsoft.jaravel.vendor.plugin.jar.annotation}）对插件可见。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PluginJarProperties.class)
@ConditionalOnProperty(prefix = "jaravel.plugin-jar", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginJarAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginJarAutoConfiguration.class);

    /**
     * Bean 注册器。
     *
     * @param applicationContext Spring 应用上下文
     * @return Bean 注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginBeanRegistrar pluginBeanRegistrar(ConfigurableApplicationContext applicationContext) {
        return new PluginBeanRegistrar(applicationContext);
    }

    /**
     * 路由注册器。
     *
     * @param handlerMapping Spring MVC 的 RequestMappingHandlerMapping
     * @param beanRegistrar  Bean 注册器
     * @param integration    jaravel 集成
     * @return 路由注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginRouteRegistrar pluginRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                                                     PluginBeanRegistrar beanRegistrar,
                                                     PluginIntegration integration) {
        return new PluginRouteRegistrar(handlerMapping, beanRegistrar, integration);
    }

    /**
     * 元数据持久化（默认 JSON 文件实现）。
     * <p>
     * {@code plugin-jar-database} 模块可覆盖此 Bean 提供数据库持久化。
     *
     * @param properties   配置属性
     * @param objectMapper Jackson ObjectMapper
     * @return 元数据持久化
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataPersistence metadataPersistence(PluginJarProperties properties,
                                                   ObjectProvider<ObjectMapper> objectMapper) {
        Path pluginsDir = resolvePluginsDir(properties);
        ObjectMapper mapper = objectMapper.getIfAvailable();
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return new JsonMetadataPersistence(pluginsDir, mapper);
    }

    /**
     * Jaravel 集成（默认空操作实现）。
     * <p>
     * jaravel starter 可覆盖此 Bean 提供具体集成。
     *
     * @param environment Spring 环境
     * @return Jaravel 集成
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginIntegration pluginIntegration(org.springframework.core.env.Environment environment) {
        DefaultPluginIntegration integration = new DefaultPluginIntegration();
        integration.setEnvironment(environment);
        return integration;
    }

    /**
     * 热插件管理器。
     * <p>
     * 创建后定位核心 JAR 路径，初始化共享 ClassLoader，并可选地自动恢复插件。
     *
     * @param properties     配置属性
     * @param beanRegistrar  Bean 注册器
     * @param routeRegistrar 路由注册器
     * @param persistence    元数据持久化
     * @param integration    Jaravel 集成
     * @return 热插件管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public HotPluginManager hotPluginManager(PluginJarProperties properties,
                                             PluginBeanRegistrar beanRegistrar,
                                             PluginRouteRegistrar routeRegistrar,
                                             MetadataPersistence persistence,
                                             PluginIntegration integration) {
        Path pluginsDir = resolvePluginsDir(properties);
        HotPluginManager manager = new HotPluginManager(
                pluginsDir, beanRegistrar, routeRegistrar, persistence, integration,
                properties.isAutoRegister());

        // 定位核心 JAR 路径（使注解类对插件可见）
        Path coreJarPath = locateCoreJarPath();
        if (coreJarPath != null) {
            manager.setCoreJarPath(coreJarPath);
            // 初始化共享 ClassLoader（仅含 core JAR，integration 提供的额外 JAR 由 initSharedClassLoader 处理）
            manager.initSharedClassLoader(coreJarPath, "0.1.1");
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
     * <p>
     * 相对路径基于工作目录解析，并确保目录存在。
     *
     * @param properties 配置属性
     * @return 插件目录路径
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
     * 通过 {@link PluginJarAutoConfiguration} 类的 {@link ProtectionDomain} 获取其所在 JAR/目录：
     * <ul>
     *   <li>fat jar 运行：返回 fat jar 路径。</li>
     *   <li>独立 JAR 运行：返回 plugin-jar-core JAR 路径。</li>
     *   <li>开发模式（exploded classes）：返回 classes 目录路径（URLClassLoader 支持目录 URL）。</li>
     * </ul>
     *
     * @return 核心 JAR/目录路径，无法定位返回 {@code null}
     */
    private Path locateCoreJarPath() {
        try {
            ProtectionDomain pd = PluginJarAutoConfiguration.class.getProtectionDomain();
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
            // JAR 文件直接返回
            if (file.isFile() && file.getName().endsWith(".jar")) {
                return file.toPath();
            }
            // 开发模式：classes 目录，URLClassLoader 支持目录 URL，直接返回
            if (file.isDirectory()) {
                log.debug("开发模式：使用 classes 目录作为核心路径 {}", file);
                return file.toPath();
            }
            return null;
        } catch (Exception e) {
            log.warn("定位核心 JAR 路径失败", e);
            return null;
        }
    }
}
