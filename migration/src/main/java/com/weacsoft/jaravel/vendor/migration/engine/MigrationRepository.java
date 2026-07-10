package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.JdbcExecutor;
import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

/**
 * 迁移记录仓库，对齐 Laravel 的 {@code Illuminate\Database\Migrations\DatabaseMigrationRepository}。
 * <p>
 * 维护 {@code migrations} 表，记录已执行的迁移，支持查询已运行列表、记录、删除、获取批次号。
 * <p>
 * 方言相关逻辑（建表 SQL、标识符引用）全部委托给 {@link Dialect} 实现，
 * 支持 MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle。
 */
public class MigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(MigrationRepository.class);

    private final JdbcExecutor jdbc;
    private final String table;
    private final Dialect dialect;

    public MigrationRepository(DataSource dataSource, String table) {
        this.jdbc = new JdbcExecutor(dataSource);
        this.table = table;
        this.dialect = DialectFactory.detect(dataSource);
    }

    /** 按方言对标识符加引号 */
    private String quote(String identifier) {
        return dialect.quote(identifier);
    }

    /**
     * 创建 migrations 记录表（如不存在）。
     * <p>
     * 建表 SQL 由 {@link Dialect#createRepositoryTableSql} 生成。
     * 对于不支持 CREATE TABLE IF NOT EXISTS 的方言（SQL Server、Oracle），
     * 由 {@link Dialect#needsCheckTableExistsBeforeCreateRepository} 控制先检查再创建。
     */
    public void createRepository() {
        if (dialect.needsCheckTableExistsBeforeCreateRepository()) {
            // SQL Server / Oracle 不支持 IF NOT EXISTS，先检查表是否存在
            String hasSql = dialect.hasTableSql();
            if (hasSql != null) {
                Integer cnt = jdbc.queryForObject(hasSql, Integer.class, table);
                if (cnt != null && cnt > 0) {
                    log.info("[migration] 迁移记录表已存在: {}", table);
                    return;
                }
            }
        }
        String sql = dialect.createRepositoryTableSql(table);
        log.info("[migration] 创建迁移记录表: {}", sql);
        jdbc.execute(sql);
    }

    /** 获取已执行的迁移名称列表（按时间正序） */
    public List<String> getRan() {
        return jdbc.queryForList(
            "SELECT migration FROM " + quote(table) + " ORDER BY id ASC", String.class);
    }

    /** 获取最后一批执行的迁移（按 id 倒序） */
    public List<String> getLast() {
        Integer lastBatch = getLastBatchNumber();
        if (lastBatch == null) {
            return Collections.emptyList();
        }
        return jdbc.queryForList(
            "SELECT migration FROM " + quote(table) + " WHERE batch = ? ORDER BY id DESC", String.class, lastBatch);
    }

    /** 记录一条已执行迁移 */
    public void log(String migration, int batch) {
        jdbc.update(
            "INSERT INTO " + quote(table) + " (migration, batch) VALUES (?, ?)", migration, batch);
    }

    /** 删除一条迁移记录（回滚时） */
    public void delete(String migration) {
        jdbc.update(
            "DELETE FROM " + quote(table) + " WHERE migration = ?", migration);
    }

    /** 获取下一批次号 */
    public int getNextBatchNumber() {
        Integer max = getLastBatchNumber();
        return (max == null ? 0 : max) + 1;
    }

    private Integer getLastBatchNumber() {
        try {
            return jdbc.queryForObject("SELECT MAX(batch) FROM " + quote(table), Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getTable() {
        return table;
    }
}
