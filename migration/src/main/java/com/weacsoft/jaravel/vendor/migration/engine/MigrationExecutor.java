package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.Schema;
import com.weacsoft.jaravel.vendor.migration.autoconfigure.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 迁移执行器，对齐 Laravel artisan 的 migrate 系列命令。
 * <p>
 * <b>独立于 SpringBoot</b>：此类不实现 {@code CommandLineRunner}，不依赖任何 Spring 注解，
 * 可在纯 Java 环境中直接实例化并调用 {@link #execute(String...)} 执行迁移。
 * <p>
 * 通过 {@link MigrationScanner} 根据配置的 {@link MigrationSource} 选择迁移加载方式：
 * <ul>
 *   <li>{@link MigrationSource#DIRECTORY}：编译迁移目录下的 {@code .java} 文件（需要 JDK）</li>
 *   <li>{@link MigrationSource#DIRECTORY_CLASSES}：从预编译目录加载 {@code .class} 文件（只需要 JRE）</li>
 *   <li>{@link MigrationSource#PACKAGED}：从预编译 zip 包加载迁移类（只需要 JRE）</li>
 *   <li>{@link MigrationSource#JAR}：从 JAR 文件加载预编译的迁移类（只需要 JRE）</li>
 *   <li>{@link MigrationSource#CLASSPATH}：从 classpath 扫描迁移类（内置迁移）</li>
 * </ul>
 * 通过 {@link Migrator} 执行迁移，完成后调用 {@link MigrationScanner#finish()} 释放资源。
 * <p>
 * 支持的命令参数：
 * <ul>
 *   <li>{@code migrate}             执行迁移</li>
 *   <li>{@code rollback[=N]}        回滚最近 N 批（默认 1）</li>
 *   <li>{@code reset}               回滚全部</li>
 *   <li>{@code refresh}             回滚全部并重新迁移</li>
 *   <li>{@code status}              查看状态</li>
 * </ul>
 * 当 {@code autoRun=true} 时，无显式命令也会自动执行 migrate。
 */
public class MigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);

    private final DataSource dataSource;
    private final MigrationProperties properties;

    /**
     * 构造迁移执行器。
     *
     * @param dataSource 数据源（用于创建 Schema 和 MigrationRepository）
     * @param properties 迁移配置
     */
    public MigrationExecutor(DataSource dataSource, MigrationProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * 执行迁移命令，对齐 Laravel artisan migrate 系列命令。
     * <p>
     * 此方法可在纯 Java 环境中直接调用，无需 SpringBoot 容器。
     *
     * @param args 命令参数，如 {@code "migrate"}、{@code "rollback=5"}、{@code "status"}
     */
    public void execute(String... args) {
        if (!properties.isEnabled()) {
            return;
        }
        List<String> argList = args != null ? Arrays.asList(args) : Collections.<String>emptyList();

        boolean hasCommand = argList.contains("migrate")
            || argList.contains("--jaravel.migrate")
            || argList.stream().anyMatch(a -> a.startsWith("rollback") || a.startsWith("--jaravel.rollback"))
            || argList.contains("reset") || argList.contains("--jaravel.reset")
            || argList.contains("refresh") || argList.contains("--jaravel.refresh")
            || argList.contains("status") || argList.contains("--jaravel.migration-status");

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
                case DIRECTORY_CLASSES:
                    log.info("[migration] 从预编译目录加载迁移: {}", properties.getClassesDir());
                    scanner.loadFromDirectoryClasses(properties.getClassesDir());
                    break;
                case PACKAGED:
                    log.info("[migration] 从打包文件加载迁移: {}", properties.getPackagePath());
                    scanner.loadFromZip(properties.getPackagePath());
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

            // 执行命令（兼容 --jaravel.xxx 和裸命令两种格式）
            if (argList.contains("migrate") || argList.contains("--jaravel.migrate")) {
                migrator.run();
            }
            if (argList.stream().anyMatch(a -> a.startsWith("rollback") || a.startsWith("--jaravel.rollback"))) {
                int steps = extractSteps(argList, 1, "rollback", "jaravel.rollback");
                migrator.rollback(steps);
            }
            if (argList.contains("reset") || argList.contains("--jaravel.reset")) {
                migrator.reset();
            }
            if (argList.contains("refresh") || argList.contains("--jaravel.refresh")) {
                migrator.refresh();
            }
            if (argList.contains("status") || argList.contains("--jaravel.migration-status")) {
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

    private int extractSteps(List<String> args, int defaultVal, String... prefixes) {
        for (String a : args) {
            for (String prefix : prefixes) {
                String fullPrefix = a.startsWith("--") ? "--" + prefix + "=" : prefix + "=";
                if (a.startsWith(fullPrefix)) {
                    try {
                        return Integer.parseInt(a.substring(fullPrefix.length()));
                    } catch (NumberFormatException e) {
                        return defaultVal;
                    }
                }
            }
        }
        return defaultVal;
    }
}
