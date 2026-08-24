package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.autoconfigure.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import javax.sql.DataSource;

/**
 * SpringBoot 命令行运行器适配层。
 * <p>
 * 实现 SpringBoot {@code CommandLineRunner} 接口，在 SpringBoot 启动后自动执行迁移。
 * 内部委托给 {@link MigrationExecutor}（核心逻辑无 SpringBoot 依赖）。
 * <p>
 * <b>独立运行</b>：如需在无 SpringBoot 环境下运行迁移，直接使用 {@link MigrationCLI} 或
 * 实例化 {@link MigrationExecutor} 调用 {@link MigrationExecutor#execute(String...)}。
 * <p>
 * 通过启动参数触发：
 * <ul>
 *   <li>{@code --jaravel.migrate}            执行迁移</li>
 *   <li>{@code --jaravel.rollback[=N]}        回滚最近 N 批（默认 1）</li>
 *   <li>{@code --jaravel.reset}               回滚全部</li>
 *   <li>{@code --jaravel.refresh}             回滚全部并重新迁移</li>
 *   <li>{@code --jaravel.migration-status}    查看状态</li>
 * </ul>
 * 当 {@code jaravel.migration.auto-run=true} 时，启动自动执行 migrate。
 */
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationExecutor executor;

    /**
     * 构造迁移命令行运行器。
     *
     * @param dataSource 数据源
     * @param properties 迁移配置
     */
    public MigrationRunner(DataSource dataSource, MigrationProperties properties) {
        this.executor = new MigrationExecutor(dataSource, properties);
    }

    /**
     * 构造迁移命令行运行器（注入已创建的 MigrationExecutor）。
     *
     * @param executor 迁移执行器
     */
    public MigrationRunner(MigrationExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void run(String... args) {
        executor.execute(args);
    }
}
