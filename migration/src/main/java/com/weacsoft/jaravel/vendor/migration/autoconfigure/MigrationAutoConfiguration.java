package com.weacsoft.jaravel.vendor.migration.autoconfigure;


import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * 通过 {@link AutoConfigureAfter} 显式声明在 {@link DataSourceAutoConfiguration} 之后装配。
 *
 * <h3>不再使用 {@code @ConditionalOnBean(DataSource.class)}</h3>
 * 迁移模块不应与 Spring 的 {@code DataSource} Bean 强绑定：jaravel 的连接可能只存在于
 * database 模块的 {@code ConnectionManager} 注册表中（由 {@code @RegisterConnection} 声明）。
 * 因此改为<b>运行时解析</b>——{@link #migrationExecutor} 内部先查注册表再回退 Spring 容器；
 * 若最终一个连接都没有，仅打印告警并注册一个空执行器，<b>不影响应用启动</b>。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(MigrationExecutor.class)
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
     * <h3>连接别名解析顺序</h3>
     * 与 Model 保持一致：<b>先取 database 模块 {@code ConnectionManager} 注册表中
     * 由 {@code @RegisterConnection} 声明的别名，再合并 Spring 容器中的
     * {@code DataSource} bean</b>（bean 名即别名，且不覆盖已注册的同名别名）。
     * 因此 {@code Migration#connection()} 返回的别名与 {@code @DataSource} 语义完全统一。
     *
     * @param properties  迁移配置
     * @param dataSources Spring 容器中的所有 DataSource（bean 名 → 数据源）
     * @return MigrationExecutor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MigrationExecutor migrationExecutor(MigrationProperties properties,
                                                @Autowired(required = false) Map<String, DataSource> dataSources) {
        Map<String, DataSource> aliasMap = new LinkedHashMap<>();

        // 1) 优先：database 模块 @RegisterConnection 注册的别名
        aliasMap.putAll(ConnectionAliasResolver.registeredConnections());

        // 2) 回退：Spring 容器中的 DataSource bean（bean 名即别名），不覆盖已注册别名
        if (dataSources != null) {
            dataSources.forEach(aliasMap::putIfAbsent);
        }

        // 3) 兜底：确保存在 primary 别名，指向容器中的主数据源
        if (!aliasMap.containsKey("primary") && dataSources != null && !dataSources.isEmpty()) {
            DataSource fallback = dataSources.containsKey("dataSource")
                    ? dataSources.get("dataSource")
                    : dataSources.values().iterator().next();
            aliasMap.put("primary", fallback);
        }

        if (aliasMap.isEmpty()) {
            // 没有任何连接不再阻断启动：迁移命令真正执行时会给出明确错误提示
            log.warn("[migration] 未发现任何数据库连接，迁移功能将不可用。"
                    + "如需使用迁移，请用 @RegisterConnection 注册连接，"
                    + "或执行 `artisan vendor:publish --tag=database` 生成默认配置。");
        } else {
            log.info("[migration] 可用连接别名: {}", aliasMap.keySet());
        }
        return new MigrationExecutor(aliasMap, properties);
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
