package com.weacsoft.jaravel.vendor.migration.autoconfigure;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.migration.ReverseModelGenerator;
import com.weacsoft.jaravel.vendor.migration.artisan.MakeModelFromTableCommand;
import com.weacsoft.jaravel.vendor.migration.artisan.MigrateCommand;
import com.weacsoft.jaravel.vendor.migration.artisan.MigrateRefreshCommand;
import com.weacsoft.jaravel.vendor.migration.artisan.MigrateResetCommand;
import com.weacsoft.jaravel.vendor.migration.artisan.MigrateRollbackCommand;
import com.weacsoft.jaravel.vendor.migration.artisan.MigrateStatusCommand;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 迁移模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）和
 * {@link MigrationExecutor}（migration 模块）时，自动注册迁移命令为
 * Artisan 命令 Bean，使开发者可通过 {@code artisan.call("migrate")} 等方式
 * 在代码中调用迁移命令，或通过命令行 {@code java -jar app.jar artisan migrate} 执行。
 * 当 {@link MakeCodeProperties} 可用时，额外注册 {@code make:model-from-table}
 * 反向工程命令。
 * <p>
 * 注册的命令：
 * <ul>
 *   <li>{@code migrate}                    — 执行迁移</li>
 *   <li>{@code migrate:rollback}           — 回滚迁移（支持 {@code --step=N}）</li>
 *   <li>{@code migrate:reset}              — 回滚所有迁移</li>
 *   <li>{@code migrate:refresh}            — 回滚全部并重新迁移</li>
 *   <li>{@code migrate:status}             — 查看迁移状态</li>
 *   <li>{@code make:model-from-table}      — 从数据库表反向生成 Model 类</li>
 * </ul>
 * <p>
 * 这些命令委托给 {@link MigrationExecutor} 执行，与 {@link MigrationRunner}
 * 共享同一个 {@link MigrationExecutor} Bean 实例。
 */
@AutoConfiguration
@AutoConfigureAfter(MigrationAutoConfiguration.class)
@ConditionalOnClass(ArtisanCommand.class)
@ConditionalOnBean(MigrationExecutor.class)
public class MigrationArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MigrationArtisanAutoConfiguration.class);

    @Bean
    public MigrateCommand migrateCommand(MigrationExecutor executor) {
        log.info("[migration-artisan] 注册命令: migrate");
        return new MigrateCommand(executor);
    }

    @Bean
    public MigrateRollbackCommand migrateRollbackCommand(MigrationExecutor executor) {
        log.info("[migration-artisan] 注册命令: migrate:rollback");
        return new MigrateRollbackCommand(executor);
    }

    @Bean
    public MigrateResetCommand migrateResetCommand(MigrationExecutor executor) {
        log.info("[migration-artisan] 注册命令: migrate:reset");
        return new MigrateResetCommand(executor);
    }

    @Bean
    public MigrateRefreshCommand migrateRefreshCommand(MigrationExecutor executor) {
        log.info("[migration-artisan] 注册命令: migrate:refresh");
        return new MigrateRefreshCommand(executor);
    }

    @Bean
    public MigrateStatusCommand migrateStatusCommand(MigrationExecutor executor) {
        log.info("[migration-artisan] 注册命令: migrate:status");
        return new MigrateStatusCommand(executor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReverseModelGenerator reverseModelGenerator(DataSource dataSource) {
        return new ReverseModelGenerator(dataSource);
    }

    @Bean
    @ConditionalOnBean(MakeCodeProperties.class)
    public MakeModelFromTableCommand makeModelFromTableCommand(ReverseModelGenerator generator, MakeCodeProperties properties) {
        log.info("[migration-artisan] 注册命令: make:model-from-table");
        return new MakeModelFromTableCommand(generator, properties);
    }
}
