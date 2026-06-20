package com.weacsoft.jaravel.vendor.migration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.List;

/**
 * 迁移模块自动装配。
 * <p>
 * 当容器中存在 {@link DataSource} 且 {@code jaravel.migration.enabled=true}（默认）时，
 * 注册 {@link Schema}、{@link MigrationRepository}、{@link Migrator}、{@link MigrationRunner}。
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

    @Bean
    @ConditionalOnMissingBean
    public Schema jaravelSchema(DataSource dataSource) {
        return new Schema(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public MigrationRepository jaravelMigrationRepository(DataSource dataSource, MigrationProperties properties) {
        return new MigrationRepository(dataSource, properties.getTable());
    }

    @Bean
    @ConditionalOnMissingBean
    public Migrator jaravelMigrator(MigrationRepository repository, Schema schema,
                                    List<Migration> migrations, ApplicationContext applicationContext) {
        return new Migrator(repository, schema, migrations, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public MigrationRunner jaravelMigrationRunner(Migrator migrator, MigrationProperties properties) {
        return new MigrationRunner(migrator, properties);
    }
}
