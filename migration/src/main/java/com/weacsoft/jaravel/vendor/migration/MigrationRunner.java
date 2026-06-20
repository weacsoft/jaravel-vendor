package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import java.util.Arrays;
import java.util.List;

/**
 * 迁移命令运行器，对齐 Laravel artisan 的 migrate 系列命令。
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

    private final Migrator migrator;
    private final MigrationProperties properties;

    public MigrationRunner(Migrator migrator, MigrationProperties properties) {
        this.migrator = migrator;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }
        List<String> argList = Arrays.asList(args);

        boolean hasCommand = false;
        if (argList.contains("--jaravel.migrate")) {
            hasCommand = true;
            migrator.run();
        }
        if (argList.stream().anyMatch(a -> a.startsWith("--jaravel.rollback"))) {
            hasCommand = true;
            int steps = extractSteps(argList, "--jaravel.rollback", 1);
            migrator.rollback(steps);
        }
        if (argList.contains("--jaravel.reset")) {
            hasCommand = true;
            migrator.reset();
        }
        if (argList.contains("--jaravel.refresh")) {
            hasCommand = true;
            migrator.refresh();
        }
        if (argList.contains("--jaravel.migration-status")) {
            hasCommand = true;
            migrator.status();
        }

        // 无显式命令时，若开启 auto-run 则自动迁移
        if (!hasCommand && properties.isAutoRun()) {
            log.info("[migration] auto-run enabled, executing migrate...");
            migrator.run();
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
