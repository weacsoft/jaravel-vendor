package com.weacsoft.jaravel.vendor.plugin.java.autoconfigure;

import com.weacsoft.jaravel.vendor.plugin.java.manager.JavaFilePluginManager;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Java 文件插件自动装配。
 * <p>
 * 条件：
 * <ul>
 *   <li>Web 应用环境</li>
 *   <li>{@code jaravel.plugin-java.enabled=true}（默认 true）</li>
 *   <li>存在 {@link PluginBeanRegistrar} Bean（来自 plugin-jar-core）</li>
 * </ul>
 * <p>
 * 自动装配流程：
 * <ol>
 *   <li>创建 {@link JavaFilePluginManager} Bean。</li>
 *   <li>若 {@code autoScan=true}，扫描源目录下的所有子目录，每个子目录作为一个插件注册。</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(PluginJavaProperties.class)
@ConditionalOnProperty(prefix = "jaravel.plugin-java", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(PluginBeanRegistrar.class)
public class PluginJavaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginJavaAutoConfiguration.class);

    /**
     * 创建 Java 文件插件管理器 Bean。
     *
     * @param properties      插件配置属性
     * @param beanRegistrar   Bean 注册器（来自 plugin-jar-core）
     * @param routeRegistrar  路由注册器（来自 plugin-jar-core）
     * @return 插件管理器实例
     */
    @Bean
    public JavaFilePluginManager javaFilePluginManager(
            PluginJavaProperties properties,
            PluginBeanRegistrar beanRegistrar,
            PluginRouteRegistrar routeRegistrar) {

        Path sourceDir = Paths.get(properties.getSourceDir());
        log.info("初始化 Java 文件插件管理器，源目录: {}, autoRegister: {}",
                sourceDir.toAbsolutePath(), properties.isAutoRegister());

        JavaFilePluginManager manager = new JavaFilePluginManager(
                sourceDir, beanRegistrar, routeRegistrar, properties.isAutoRegister());

        // 自动扫描
        if (properties.isAutoScan()) {
            autoScanPlugins(manager, sourceDir);
        }

        return manager;
    }

    /**
     * 扫描源目录下的所有子目录，注册为插件。
     *
     * @param manager   插件管理器
     * @param sourceDir 源目录
     */
    private void autoScanPlugins(JavaFilePluginManager manager, Path sourceDir) {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            log.info("Java 文件插件源目录不存在，跳过自动扫描: {}", sourceDir.toAbsolutePath());
            return;
        }

        log.info("开始扫描 Java 文件插件目录: {}", sourceDir.toAbsolutePath());

        try (Stream<Path> paths = Files.list(sourceDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(pluginDir -> {
                        try {
                            String pluginId = manager.registerPlugin(pluginDir);
                            manager.enablePlugin(pluginId);
                            log.info("自动注册并启用插件: {}", pluginId);
                        } catch (Exception e) {
                            log.error("自动注册插件失败: {}", pluginDir, e);
                        }
                    });
        } catch (IOException e) {
            log.error("扫描插件目录失败: {}", sourceDir, e);
        }
    }
}
