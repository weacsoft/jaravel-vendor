package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

/**
 * 迁移记录仓库，对齐 Laravel 的 {@code Illuminate\Database\Migrations\DatabaseMigrationRepository}。
 * <p>
 * 维护 {@code migrations} 表，记录已执行的迁移，支持查询已运行列表、记录、删除、获取批次号。
 * 自动适配 MySQL / SQLite / H2 / SQL Server 方言。
 */
public class MigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(MigrationRepository.class);

    private final JdbcExecutor jdbc;
    private final String table;
    private final String databaseProductName;

    public MigrationRepository(DataSource dataSource, String table) {
        this.jdbc = new JdbcExecutor(dataSource);
        this.table = table;
        this.databaseProductName = detectProductName(dataSource);
    }

    private String detectProductName(DataSource dataSource) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            log.warn("[migration] 无法识别数据库产品，使用默认 MySQL 方言: {}", e.getMessage());
            return "mysql";
        }
    }

    private boolean isSqlite() {
        return databaseProductName.contains("sqlite");
    }

    private boolean isH2() {
        return databaseProductName.contains("h2");
    }

    private boolean isSqlServer() {
        return databaseProductName.contains("sql server");
    }

    /** 按方言对标识符加引号 */
    private String quote(String identifier) {
        if (isSqlServer()) {
            return "[" + identifier + "]";
        }
        return "`" + identifier + "`";
    }

    /** 创建 migrations 记录表（如不存在） */
    public void createRepository() {
        String sql;
        if (isSqlite()) {
            // SQLite: INTEGER PRIMARY KEY AUTOINCREMENT
            sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`migration` VARCHAR(255) NOT NULL, "
                + "`batch` INT NOT NULL"
                + ")";
        } else if (isH2()) {
            // H2: AUTO_INCREMENT, 无 ENGINE
            sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "`migration` VARCHAR(255) NOT NULL, "
                + "`batch` INT NOT NULL"
                + ")";
        } else if (isSqlServer()) {
            // SQL Server: IDENTITY(1,1)，无 IF NOT EXISTS 语法（旧版），用方括号引用
            // 先检查表是否存在，不存在则创建
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.tables WHERE name = ?", Integer.class, table);
            if (cnt != null && cnt > 0) {
                log.info("[migration] 迁移记录表已存在: {}", table);
                return;
            }
            sql = "CREATE TABLE [" + table + "] ("
                + "[id] INT IDENTITY(1,1) PRIMARY KEY, "
                + "[migration] VARCHAR(255) NOT NULL, "
                + "[batch] INT NOT NULL"
                + ")";
        } else {
            // MySQL
            sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "`migration` VARCHAR(255) NOT NULL, "
                + "`batch` INT NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
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
