package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 迁移引擎，对齐 Laravel 的 {@code Illuminate\Database\Migrations\Migrator}。
 * <p>
 * 收集容器中所有 {@link Migration} 实例，按名称排序，与 {@link MigrationRepository} 记录比对，
 * 执行待运行的迁移（{@link #run()}）或回滚（{@link #rollback(int)}）。
 */
public class Migrator {

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);

    private final MigrationRepository repository;
    private final Schema schema;
    private final List<Migration> migrations;
    private final ApplicationContext applicationContext;

    public Migrator(MigrationRepository repository, Schema schema, List<Migration> migrations,
                    ApplicationContext applicationContext) {
        this.repository = repository;
        this.schema = schema;
        this.migrations = migrations == null ? List.of() : migrations;
        this.applicationContext = applicationContext;
    }

    /** 执行所有待运行迁移，返回已执行的迁移名称列表 */
    public List<String> run() {
        repository.createRepository();
        List<String> ran = repository.getRan();
        int batch = repository.getNextBatchNumber();
        List<String> executed = new ArrayList<>();
        for (Migration migration : sortedMigrations()) {
            if (!ran.contains(migration.getName())) {
                log.info("[migration] Migrating: {}", migration.getName());
                migration.up(resolveSchema(migration));
                repository.log(migration.getName(), batch);
                executed.add(migration.getName());
            }
        }
        if (executed.isEmpty()) {
            log.info("[migration] Nothing to migrate.");
        } else {
            log.info("[migration] Migrated {} migration(s).", executed.size());
        }
        return executed;
    }

    /** 回滚指定步数（批次），返回已回滚的迁移名称列表 */
    public List<String> rollback(int steps) {
        repository.createRepository();
        List<String> last = repository.getLast();
        List<String> rolledBack = new ArrayList<>();
        int count = 0;
        for (String name : last) {
            if (steps > 0 && count >= steps) {
                break;
            }
            Migration migration = findMigration(name);
            if (migration == null) {
                log.warn("[migration] Migration class not found for: {}, skipping.", name);
                continue;
            }
            log.info("[migration] Rolling back: {}", name);
            migration.down(resolveSchema(migration));
            repository.delete(name);
            rolledBack.add(name);
            count++;
        }
        if (rolledBack.isEmpty()) {
            log.info("[migration] Nothing to rollback.");
        } else {
            log.info("[migration] Rolled back {} migration(s).", rolledBack.size());
        }
        return rolledBack;
    }

    /** 回滚所有迁移 */
    public List<String> reset() {
        repository.createRepository();
        List<String> ran = repository.getRan();
        List<String> reset = new ArrayList<>();
        // 倒序回滚
        List<String> reversed = new ArrayList<>(ran);
        java.util.Collections.reverse(reversed);
        for (String name : reversed) {
            Migration migration = findMigration(name);
            if (migration == null) {
                continue;
            }
            log.info("[migration] Resetting: {}", name);
            migration.down(resolveSchema(migration));
            repository.delete(name);
            reset.add(name);
        }
        log.info("[migration] Reset {} migration(s).", reset.size());
        return reset;
    }

    /** 回滚所有并重新迁移 */
    public List<String> refresh() {
        reset();
        return run();
    }

    /** 输出迁移状态 */
    public void status() {
        repository.createRepository();
        List<String> ran = repository.getRan();
        log.info("[migration] Status:");
        log.info("  Ran?  Migration");
        for (Migration migration : sortedMigrations()) {
            String flag = ran.contains(migration.getName()) ? "  [Y]  " : "  [N]  ";
            log.info("{}{}", flag, migration.getName());
        }
    }

    /** 获取待运行迁移名称列表 */
    public List<String> pending() {
        repository.createRepository();
        List<String> ran = repository.getRan();
        return sortedMigrations().stream()
            .map(Migration::getName)
            .filter(n -> !ran.contains(n))
            .collect(Collectors.toList());
    }

    private List<Migration> sortedMigrations() {
        List<Migration> list = new ArrayList<>(migrations);
        list.sort(Comparator.comparing(Migration::getName));
        return list;
    }

    private Migration findMigration(String name) {
        for (Migration m : migrations) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 解析迁移使用的 Schema。
     * <p>
     * 如果迁移通过 {@link Migration#getDataSourceName()} 指定了数据源 Bean 名称，
     * 则从 Spring 容器中查找对应名称的 {@link DataSource}，创建独立的 {@link Schema}。
     * 否则使用默认 Schema（Primary 数据源）。
     *
     * @param migration 迁移实例
     * @return 对应的 Schema 实例
     */
    private Schema resolveSchema(Migration migration) {
        String dsName = migration.getDataSourceName();
        if (dsName != null && !dsName.isEmpty()) {
            try {
                DataSource dataSource = applicationContext.getBean(dsName, DataSource.class);
                log.info("[migration] 使用数据源 '{}' 执行迁移: {}", dsName, migration.getName());
                return new Schema(dataSource);
            } catch (Exception e) {
                log.warn("[migration] 未找到数据源 '{}'，回退到默认数据源: {}", dsName, e.getMessage());
            }
        }
        return schema;
    }
}
