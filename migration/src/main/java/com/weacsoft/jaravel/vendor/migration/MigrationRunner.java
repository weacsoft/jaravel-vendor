package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import javax.sql.DataSource;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 迁移命令运行器，对齐 Laravel artisan 的 migrate 系列命令。
 * <p>
 * <b>重要变更</b>：不再通过 Spring DI 注入 {@code List<Migration>}，
 * 而是在运行时创建 {@link MigrationScanner}，根据 {@link MigrationSource} 配置
 * 选择不同的加载方式：
 * <ul>
 *   <li>{@link MigrationSource#DIRECTORY}：编译迁移目录下的 {@code .java} 文件（需要 JDK）</li>
 *   <li>{@link MigrationSource#JAR}：从 JAR 文件加载预编译的迁移类（只需要 JRE）</li>
 *   <li>{@link MigrationSource#CLASSPATH}：从 classpath 扫描迁移类（内置迁移）</li>
 * </ul>
 * 通过 {@link Migrator} 执行迁移，完成后调用 {@link MigrationScanner#finish()} 释放资源。
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

    private final DataSource dataSource;
    private final MigrationProperties properties;

    /**
     * 构造迁移命令运行器。
     *
     * @param dataSource 数据源（用于创建 Schema 和 MigrationRepository）
     * @param properties 迁移配置
     */
    public MigrationRunner(DataSource dataSource, MigrationProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }
        List<String> argList = Arrays.asList(args);

        boolean hasCommand = argList.contains("--jaravel.migrate");
        if (argList.stream().anyMatch(a -> a.startsWith("--jaravel.rollback"))) {
            hasCommand = true;
        }
        if (argList.contains("--jaravel.reset")) {
            hasCommand = true;
        }
        if (argList.contains("--jaravel.refresh")) {
            hasCommand = true;
        }
        if (argList.contains("--jaravel.migration-status")) {
            hasCommand = true;
        }

        // 无显式命令时，若开启 auto-run 则自动迁移
        if (!hasCommand && !properties.isAutoRun()) {
            return;
        }

        // 创建 MigrationScanner，根据配置选择加载方式
        MigrationScanner scanner = new MigrationScanner();
        try {
            switch (properties.getSource()) {
                case DIRECTORY:
                    log.info("[migration] 从目录编译迁移: {}", properties.getDirectory());
                    scanner.compileFromDirectory(properties.getDirectory());
                    break;
                case JAR:
                    log.info("[migration] 从 JAR 加载迁移: {}", properties.getJarPath());
                    scanner.loadFromJar(new File(properties.getJarPath()));
                    break;
                case CLASSPATH:
                    log.info("[migration] 从 classpath 扫描迁移");
                    scanner.loadFromClasspath();
                    break;
                default:
                    throw new IllegalStateException("未知的迁移源类型: " + properties.getSource());
            }

            // 创建 Schema、MigrationRepository、Migrator
            Schema schema = new Schema(dataSource);
            MigrationRepository repository = new MigrationRepository(dataSource, properties.getTable());
            Migrator migrator = new Migrator(repository, schema, scanner);

            // 执行命令
            if (argList.contains("--jaravel.migrate")) {
                migrator.run();
            }
            if (argList.stream().anyMatch(a -> a.startsWith("--jaravel.rollback"))) {
                int steps = extractSteps(argList, "--jaravel.rollback", 1);
                migrator.rollback(steps);
            }
            if (argList.contains("--jaravel.reset")) {
                migrator.reset();
            }
            if (argList.contains("--jaravel.refresh")) {
                migrator.refresh();
            }
            if (argList.contains("--jaravel.migration-status")) {
                migrator.status();
            }

            // 无显式命令时，若开启 auto-run 则自动迁移
            if (!hasCommand && properties.isAutoRun()) {
                log.info("[migration] auto-run enabled, executing migrate...");
                migrator.run();
            }
        } catch (Exception e) {
            log.error("[migration] 迁移执行失败", e);
            throw new RuntimeException(e);
        } finally {
            // 释放资源：清除编译产物、已加载类与类加载器
            scanner.finish();
        }
    }

    private int extractSteps(List<String> args, String prefix, int defaultVal) {
        for (String a : args) {
            if (a.startsWith(prefix + "=")) {
                try {
                    return Integer.parseInt(a.substring(prefix.length() + 1));
                } catch (NumberFormatException e) {
                    return defaultVal;
                }
            }
        }
        return defaultVal;
    }
}
