package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * 迁移模块自动装配。
 * <p>
 * <b>重要变更</b>：不再注入 {@code List<Migration>}，也不再注册 {@link Schema}、
 * {@link MigrationRepository}、{@link Migrator} 为 Bean。
 * 迁移文件不再是 Spring 组件，而是通过 {@link MigrationScanner} 在运行时
 * 加载（DIRECTORY 内存编译 / JAR 加载 / CLASSPATH 扫描）、反射实例化、执行后自动释放。
 * <p>
 * 本类仅注册 {@link MigrationRunner} 一个 Bean，注入 {@link DataSource} 与
 * {@link MigrationProperties}。{@link MigrationRunner} 在运行时创建
 * {@link MigrationScanner}、{@link Schema}、{@link MigrationRepository}、
 * {@link Migrator}，执行完毕后调用 {@link MigrationScanner#finish()} 释放资源。
 * <p>
 * 通过 {@link AutoConfigureAfter} 显式声明在 {@link DataSourceAutoConfiguration} 之后装配，
 * 确保 {@code @ConditionalOnBean(DataSource.class)} 能正确匹配到已注册的数据源。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(Migrator.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "jaravel.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MigrationAutoConfiguration.class);

    /**
     * 注册迁移命令行运行器。
     * <p>
     * 仅注入 {@link DataSource} 与 {@link MigrationProperties}，
     * 在运行时由 {@link MigrationRunner} 创建 {@link MigrationScanner} 等组件。
     * <p>
     * 根据配置的 {@link MigrationSource} 选择迁移加载方式：
     * <ul>
     *   <li>{@link MigrationSource#DIRECTORY}：目录模式，运行时内存编译（需要 JDK）</li>
     *   <li>{@link MigrationSource#JAR}：JAR 模式，加载预编译迁移类（只需要 JRE）</li>
     *   <li>{@link MigrationSource#CLASSPATH}：Classpath 模式，扫描内置迁移（只需要 JRE）</li>
     * </ul>
     *
     * @param dataSource 数据源
     * @param properties 迁移配置
     * @return MigrationRunner 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public MigrationRunner jaravelMigrationRunner(DataSource dataSource, MigrationProperties properties) {
        log.info("[migration] 迁移模块已启用，迁移源模式: {} (directory={}, jar-path={})",
            properties.getSource(), properties.getDirectory(), properties.getJarPath());
        return new MigrationRunner(dataSource, properties);
    }
}
