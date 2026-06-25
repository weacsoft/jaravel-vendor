package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 迁移引擎，对齐 Laravel 的 {@code Illuminate\Database\Migrations\Migrator}。
 * <p>
 * <b>重要变更</b>：不再通过 Spring DI 注入 {@code List<Migration>}，
 * 而是通过 {@link MigrationScanner} 在运行时编译迁移文件、反射实例化、执行后自动释放。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>从 {@link MigrationScanner} 获取所有已编译的迁移类名</li>
 *   <li>反射加载类、检查 {@link MigrationAnnotation} 标记</li>
 *   <li>通过 {@code clazz.getDeclaredConstructor().newInstance()} 实例化</li>
 *   <li>执行 {@link Migration#up(Schema)} 或 {@link Migration#down(Schema)}</li>
 *   <li>所有操作完成后调用 {@link #finish()} 释放资源</li>
 * </ol>
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的运行时内存编译模式。
 */
public class Migrator {

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);

    private final MigrationRepository repository;
    private final Schema schema;
    private final MigrationScanner scanner;

    /**
     * 构造迁移引擎。
     *
     * @param repository 迁移记录仓库（跟踪已执行的迁移）
     * @param schema    默认 Schema 实例（使用 Primary 数据源）
     * @param scanner   迁移扫描器（已编译完成的迁移文件）
     */
    public Migrator(MigrationRepository repository, Schema schema, MigrationScanner scanner) {
        this.repository = repository;
        this.schema = schema;
        this.scanner = scanner;
    }

    /** 执行所有待运行迁移，返回已执行的迁移名称列表 */
    public List<String> run() {
        repository.createRepository();
        List<String> ran = repository.getRan();
        int batch = repository.getNextBatchNumber();
        List<String> executed = new ArrayList<>();
        for (Migration migration : sortedMigrations()) {
            String name = migration.getName();
            if (!ran.contains(name)) {
                log.info("[migration] Migrating: {}", name);
                migration.up(schema);
                repository.log(name, batch);
                executed.add(name);
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
            migration.down(schema);
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
            migration.down(schema);
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

    /**
     * 释放资源：调用 {@link MigrationScanner#finish()} 清除编译产物与类加载器。
     * <p>
     * 在所有迁移操作完成后调用，确保内存中的编译产物被回收。
     */
    public void finish() {
        if (scanner != null) {
            scanner.finish();
        }
    }

    /**
     * 获取所有已编译的迁移实例（按名称排序）。
     * <p>
     * 遍历 {@link MigrationScanner#getAllMigrationClassNames()}，
     * 反射加载每个类，检查是否标注 {@link MigrationAnnotation}，
     * 若是则通过 {@code clazz.getDeclaredConstructor().newInstance()} 实例化为 {@link Migration}。
     *
     * @return 排序后的迁移实例列表
     */
    private List<Migration> sortedMigrations() {
        List<Migration> list = new ArrayList<>();
        for (String className : scanner.getAllMigrationClassNames()) {
            try {
                Class<?> clazz = scanner.getCompiledClass(className);
                // 检查是否标注了 @MigrationAnnotation
                if (!clazz.isAnnotationPresent(MigrationAnnotation.class)) {
                    continue;
                }
                // 检查是否实现了 Migration 接口
                if (!Migration.class.isAssignableFrom(clazz)) {
                    continue;
                }
                // 反射实例化
                Migration migration = (Migration) clazz.getDeclaredConstructor().newInstance();
                // 若注解指定了名称，则使用注解名称
                MigrationAnnotation annotation = clazz.getAnnotation(MigrationAnnotation.class);
                if (annotation != null && !annotation.name().isEmpty()) {
                    final String annotatedName = annotation.name();
                    Migration wrapper = new Migration() {
                        private final Migration delegate = migration;
                        @Override
                        public void up(Schema schema) { delegate.up(schema); }
                        @Override
                        public void down(Schema schema) { delegate.down(schema); }
                        @Override
                        public String getName() { return annotatedName; }
                    };
                    list.add(wrapper);
                } else {
                    list.add(migration);
                }
            } catch (Exception e) {
                log.warn("[migration] 无法加载迁移类 {}: {}", className, e.getMessage());
            }
        }
        list.sort(Comparator.comparing(Migration::getName));
        return list;
    }

    /**
     * 按名称查找迁移实例。
     *
     * @param name 迁移名称
     * @return 迁移实例，未找到时返回 null
     */
    private Migration findMigration(String name) {
        for (Migration m : sortedMigrations()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
}
