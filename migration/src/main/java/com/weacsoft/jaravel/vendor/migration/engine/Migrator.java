package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.migration.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 迁移引擎，对齐 Laravel 的 {@code Illuminate\Database\Migrations\Migrator}。
 * <p>
 * <b>重要变更</b>：不再通过 Spring DI 注入 {@code List<Migration>}，
 * 而是通过 {@link MigrationScanner} 在运行时编译迁移文件、反射实例化、执行后自动释放。
 * <p>
 * <b>多数据库支持</b>：引擎持有「连接别名 → DataSource」的映射。执行每个迁移时，
 * 根据 {@link Migration#connection()} 返回的别名选择对应的 {@link DataSource}，
 * 并以此创建 {@link Schema} 与 {@link MigrationRepository}（迁移记录表会写入该连接的数据库）。
 * 这样即可让不同的迁移作用于不同的数据库（例如 MySQL / SQLite / Oracle / PostgreSQL 混用）。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>从 {@link MigrationScanner} 获取所有已编译的迁移类名</li>
 *   <li>反射加载类、检查 {@link MigrationAnnotation} 标记</li>
 *   <li>通过 {@code clazz.getDeclaredConstructor().newInstance()} 实例化</li>
 *   <li>按 {@link Migration#connection()} 选择对应数据源，执行 {@link Migration#up(Schema)} 或 {@link Migration#down(Schema)}</li>
 *   <li>所有操作完成后调用 {@link #finish()} 释放资源</li>
 * </ol>
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的运行时内存编译模式。
 */
public class Migrator {

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);

    private final Map<String, DataSource> dataSources;
    private final String migrationsTable;
    private final MigrationScanner scanner;

    /**
     * 构造迁移引擎。
     *
     * @param dataSources     连接别名 → DataSource 映射（应至少包含 {@code sqlite}）
     * @param migrationsTable 迁移记录表名（如 {@code migrations}）
     * @param scanner         迁移扫描器（已编译完成的迁移文件）
     */
    public Migrator(Map<String, DataSource> dataSources, String migrationsTable, MigrationScanner scanner) {
        this.dataSources = dataSources;
        this.migrationsTable = migrationsTable;
        this.scanner = scanner;
    }

    /** 按别名获取 DataSource，未知别名回退到 sqlite 默认连接 */
    private DataSource resolveDataSource(String alias) {
        DataSource ds = dataSources.get(alias);
        if (ds == null) {
            ds = dataSources.get("sqlite");
            if (ds == null) {
                throw new IllegalStateException("未找到数据库连接别名: " + alias
                        + "，且不存在 sqlite 默认连接。已配置别名: " + dataSources.keySet());
            }
            log.warn("[migration] 未找到数据库连接别名 '{}'，回退使用 sqlite 默认连接。", alias);
        }
        return ds;
    }

    /** 按别名获取（并缓存）迁移记录仓库 */
    private final Map<String, MigrationRepository> repositoryCache = new HashMap<>();
    private MigrationRepository repositoryFor(String alias) {
        return repositoryCache.computeIfAbsent(alias,
                a -> new MigrationRepository(resolveDataSource(a), migrationsTable));
    }

    /** 执行所有待运行迁移，返回已执行的迁移名称列表 */
    public List<String> run() {
        List<String> executed = new ArrayList<>();
        for (Migration migration : sortedMigrations()) {
            String alias = migration.connection();
            MigrationRepository repository = repositoryFor(alias);
            repository.createRepository();
            List<String> ran = repository.getRan();
            String name = migration.getName();
            if (!ran.contains(name)) {
                log.info("[migration] Migrating [{}]: {}", alias, name);
                migration.up(new Schema(resolveDataSource(alias)));
                repository.log(name, repository.getNextBatchNumber());
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
        List<String> rolledBack = new ArrayList<>();
        int count = 0;
        // 按别名分别处理其最后一批（整体步数控制）
        for (String alias : dataSources.keySet()) {
            MigrationRepository repository = repositoryFor(alias);
            repository.createRepository();
            List<String> last = repository.getLast();
            for (String name : last) {
                if (steps > 0 && count >= steps) {
                    break;
                }
                Migration migration = findMigration(name);
                if (migration == null) {
                    log.warn("[migration] Migration class not found for: {}, skipping.", name);
                    continue;
                }
                log.info("[migration] Rolling back [{}]: {}", alias, name);
                migration.down(new Schema(resolveDataSource(alias)));
                repository.delete(name);
                rolledBack.add(name);
                count++;
            }
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
        List<String> reset = new ArrayList<>();
        for (String alias : dataSources.keySet()) {
            MigrationRepository repository = repositoryFor(alias);
            repository.createRepository();
            List<String> ran = repository.getRan();
            List<String> reversed = new ArrayList<>(ran);
            java.util.Collections.reverse(reversed);
            for (String name : reversed) {
                Migration migration = findMigration(name);
                if (migration == null) {
                    continue;
                }
                log.info("[migration] Resetting [{}]: {}", alias, name);
                migration.down(new Schema(resolveDataSource(alias)));
                repository.delete(name);
                reset.add(name);
            }
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
        log.info("[migration] Status:");
        log.info("  Ran?  Connection   Migration");
        for (Migration migration : sortedMigrations()) {
            String alias = migration.connection();
            MigrationRepository repository = repositoryFor(alias);
            repository.createRepository();
            List<String> ran = repository.getRan();
            String flag = ran.contains(migration.getName()) ? "  [Y]  " : "  [N]  ";
            log.info("{}{:<12}{}", flag, alias, migration.getName());
        }
    }

    /** 获取待运行迁移名称列表 */
    public List<String> pending() {
        List<String> pending = new ArrayList<>();
        for (Migration migration : sortedMigrations()) {
            String alias = migration.connection();
            MigrationRepository repository = repositoryFor(alias);
            repository.createRepository();
            List<String> ran = repository.getRan();
            if (!ran.contains(migration.getName())) {
                pending.add(migration.getName());
            }
        }
        return pending;
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
                    final Migration delegate = migration;
                    Migration wrapper = new Migration() {
                        @Override
                        public void up(Schema schema) { delegate.up(schema); }
                        @Override
                        public void down(Schema schema) { delegate.down(schema); }
                        @Override
                        public String getName() { return annotatedName; }
                        @Override
                        public String connection() { return delegate.connection(); }
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
