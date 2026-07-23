package com.weacsoft.jaravel.vendor.migration.autoconfigure;


import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * 迁移模块自动装配（SpringBoot 适配层）。
 * <p>
 * <b>设计说明</b>：迁移核心逻辑（{@link MigrationExecutor}、{@link Migrator}、
 * {@link Schema}、{@link MigrationRepository}、{@link MigrationScanner}）已完全独立于 SpringBoot，
 * 可通过 {@link MigrationCLI} 在纯 Java 环境中运行。本类仅作为 SpringBoot 适配层，
 * 将 {@link DataSource} 和配置注入到 {@link MigrationExecutor}。
 * <p>
 * 本类注册两个 Bean：
 * <ul>
 *   <li>{@link MigrationProperties}：通过 {@code @ConfigurationProperties} 绑定 {@code jaravel.migration.*} 配置</li>
 *   <li>{@link MigrationRunner}：实现 {@code CommandLineRunner}，启动时根据命令参数执行迁移</li>
 * </ul>
 * <p>
 * 通过 {@link AutoConfigureAfter} 显式声明在 {@link DataSourceAutoConfiguration} 之后装配，
 * 确保 {@code @ConditionalOnBean(DataSource.class)} 能正确匹配到已注册的数据源。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(MigrationExecutor.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "jaravel.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MigrationAutoConfiguration.class);

    /**
     * 迁移配置 Bean，绑定 {@code jaravel.migration.*} 配置。
     * <p>
     * {@link MigrationProperties} 本身为纯 POJO（无 Spring 注解），
     * 通过此 {@code @Bean} 方法上的 {@code @ConfigurationProperties} 完成属性绑定。
     *
     * @return 迁移配置
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.migration")
    @ConditionalOnMissingBean
    public MigrationProperties jaravelMigrationProperties() {
        return new MigrationProperties();
    }

    /**
     * 迁移执行器 Bean。
     * <p>
     * 将核心迁移逻辑注册为 Spring Bean，供 {@link MigrationRunner} 和
     * Artisan 迁移命令（{@code migrate}、{@code migrate:rollback} 等）共享。
     *
     * @param dataSource 数据源
     * @param properties 迁移配置
     * @return MigrationExecutor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MigrationExecutor migrationExecutor(DataSource dataSource, MigrationProperties properties) {
        return new MigrationExecutor(dataSource, properties);
    }

    /**
     * 注册迁移命令行运行器（SpringBoot 适配）。
     * <p>
     * 内部委托给 {@link MigrationExecutor}，仅实现 {@code CommandLineRunner}
     * 以便在 SpringBoot 启动后自动执行迁移。
     *
     * @param executor 迁移执行器
     * @return MigrationRunner 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public MigrationRunner jaravelMigrationRunner(MigrationExecutor executor) {
        log.info("[migration] 迁移模块已启用，迁移源模式: {} (directory={}, jar-path={})",
            executor.getProperties().getSource(), executor.getProperties().getDirectory(), executor.getProperties().getJarPath());
        return new MigrationRunner(executor);
    }
}
