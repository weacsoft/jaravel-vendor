package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Schema 构建器，对齐 Laravel 的 {@code Illuminate\Support\Facades\Schema}。
 * <p>
 * 通过 {@link Blueprint} 声明表结构，由本类执行生成的 SQL DDL。
 * 自动适配 MySQL、SQLite、H2、SQL Server 等多种数据库方言：
 * <ul>
 *   <li>标识符引用：MySQL/SQLite/H2 用反引号，SQL Server 用方括号；</li>
 *   <li>自增：MySQL/H2 用 AUTO_INCREMENT，SQLite 用 AUTOINCREMENT，SQL Server 用 IDENTITY(1,1)；</li>
 *   <li>建表选项：MySQL 追加 ENGINE/CHARSET，其余方言为空；</li>
 *   <li>系统目录：MySQL 查 information_schema，SQLite 查 sqlite_master，H2 查 INFORMATION_SCHEMA，
 *       SQL Server 查 sys.tables / sys.columns；</li>
 *   <li>重命名：MySQL 用 RENAME TABLE，SQLite/H2 用 ALTER TABLE RENAME TO，SQL Server 用 sp_rename；</li>
 *   <li>修改字段：MySQL 用 ALTER TABLE MODIFY，H2 用 ALTER TABLE ALTER COLUMN，
 *       SQL Server 用 ALTER TABLE ALTER COLUMN（仅类型+可空性），SQLite 走重建表流程。</li>
 * </ul>
 * <pre>
 * schema.create("users", table -> table.id().string("name"));
 * schema.table("users", table -> table.string("phone").nullable());
 * schema.dropIfExists("users");
 * </pre>
 * <p>
 * <b>多表支持</b>：一次迁移的 {@code up()} 可连续调用多次 {@link #create(String, Consumer)}
 * 或 {@link #table(String, Consumer)} 处理多张表，{@code down()} 应对称删除/回滚。
 */
public class Schema {

    private static final Logger log = LoggerFactory.getLogger(Schema.class);

    private final JdbcTemplate jdbcTemplate;
    /** 数据库产品名（小写），用于方言判断 */
    private final String databaseProductName;

    public Schema(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
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

    /** 是否为 SQLite 方言 */
    private boolean isSqlite() {
        return databaseProductName.contains("sqlite");
    }

    /** 是否为 H2 方言 */
    private boolean isH2() {
        return databaseProductName.contains("h2");
    }

    /** 是否为 SQL Server 方言 */
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

    /** 根据方言返回建表附加选项 */
    private String tableOptions() {
        // H2、SQLite、SQL Server 均不支持 ENGINE/CHARSET 语法，返回空串
        if (isH2() || isSqlite() || isSqlServer()) {
            return "";
        }
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    /**
     * 创建表，对齐 Laravel Schema::create。
     * <p>
     * 一次 up() 可多次调用本方法创建多张表。
     *
     * @param table      表名
     * @param definition 表结构定义回调
     */
    public void create(String table, Consumer<Blueprint> definition) {
        Blueprint blueprint = new Blueprint(table);
        blueprint.setDatabaseType(databaseProductName);
        blueprint.setTableOptions(tableOptions());
        definition.accept(blueprint);
        String sql = blueprint.toCreateSql();
        log.info("[migration] CREATE TABLE: {}", sql);
        jdbcTemplate.execute(sql);
        for (String indexSql : blueprint.toIndexSql()) {
            log.info("[migration] INDEX: {}", indexSql);
            jdbcTemplate.execute(indexSql);
        }
        for (String fkSql : blueprint.toForeignKeySql()) {
            log.info("[migration] FOREIGN KEY: {}", fkSql);
            jdbcTemplate.execute(fkSql);
        }
    }

    /**
     * 修改已有表，对齐 Laravel Schema::table。
     * <p>
     * 支持增加字段（{@code table.string("phone")}）、修改字段（{@code table.string("name").change()}）、
     * 删除字段（{@code table.dropColumn("phone")}）、重命名字段（{@code table.renameColumn("old","new")}）等操作。
     * <p>
     * 按方言生成对应 ALTER TABLE 语法：
     * <ul>
     *   <li>增加字段：所有方言均用 {@code ALTER TABLE t ADD col_def}；</li>
     *   <li>修改字段：MySQL 用 {@code MODIFY}，H2 用 {@code ALTER COLUMN}，
     *       SQL Server 用 {@code ALTER COLUMN}（仅类型+可空性），SQLite 走重建表流程；</li>
     *   <li>删除字段：所有方言均用 {@code ALTER TABLE t DROP COLUMN col}。</li>
     * </ul>
     *
     * @param table      表名
     * @param definition 表结构修改回调
     */
    public void table(String table, Consumer<Blueprint> definition) {
        Blueprint blueprint = new Blueprint(table);
        blueprint.setDatabaseType(databaseProductName);
        blueprint.setTableOptions(tableOptions());
        definition.accept(blueprint);

        // 区分新增字段与修改字段
        List<ColumnDefinition> addColumns = new ArrayList<>();
        List<ColumnDefinition> modifyColumns = new ArrayList<>();
        for (ColumnDefinition c : blueprint.getColumns()) {
            if (c.isChange()) {
                modifyColumns.add(c);
            } else {
                addColumns.add(c);
            }
        }

        // 1. 新增字段：所有方言均支持 ALTER TABLE ADD
        for (ColumnDefinition c : addColumns) {
            String sql = "ALTER TABLE " + quote(table) + " ADD " + c.toSql();
            log.info("[migration] ADD COLUMN: {}", sql);
            jdbcTemplate.execute(sql);
        }

        // 2. 修改字段：按方言处理
        if (!modifyColumns.isEmpty()) {
            if (isSqlite()) {
                // SQLite 不支持 MODIFY，走重建表流程
                sqliteRecreateTableForModify(table, blueprint, modifyColumns);
            } else {
                for (ColumnDefinition c : modifyColumns) {
                    String sql;
                    if (isSqlServer()) {
                        // SQL Server: ALTER TABLE [t] ALTER COLUMN [col] type [NULL|NOT NULL]
                        sql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + c.toModifyColumnFragment();
                    } else if (isH2()) {
                        // H2: ALTER TABLE `t` ALTER COLUMN `col` def
                        sql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + c.toModifyColumnFragment();
                    } else {
                        // MySQL: ALTER TABLE `t` MODIFY `col` def
                        sql = "ALTER TABLE " + quote(table) + " MODIFY " + c.toModifyColumnFragment();
                    }
                    log.info("[migration] MODIFY COLUMN: {}", sql);
                    jdbcTemplate.execute(sql);
                }
            }
        }

        // 3. 索引
        for (String indexSql : blueprint.toIndexSql()) {
            log.info("[migration] INDEX: {}", indexSql);
            jdbcTemplate.execute(indexSql);
        }
        // 4. 外键
        for (String fkSql : blueprint.toForeignKeySql()) {
            log.info("[migration] FOREIGN KEY: {}", fkSql);
            jdbcTemplate.execute(fkSql);
        }
        // 5. 其它 ALTER（dropColumn / renameColumn / dropIndex）
        for (String alterSql : blueprint.toAlterSql()) {
            log.info("[migration] ALTER: {}", alterSql);
            jdbcTemplate.execute(alterSql);
        }
    }

    /**
     * SQLite 修改字段的重建表流程。
     * <p>
     * SQLite 不支持 ALTER TABLE MODIFY，需通过以下步骤实现：
     * <ol>
     *   <li>通过 PRAGMA table_info 读取现有列定义；</li>
     *   <li>创建临时表 _jaravel_temp，列定义为「现有列中未被修改的保持原样 + 被修改列使用新定义」；</li>
     *   <li>将原表数据按列名复制到临时表；</li>
     *   <li>删除原表；</li>
     *   <li>将临时表重命名为原表名。</li>
     * </ol>
     * 注意：此流程会丢失原表上的索引与外键，如需保留请在迁移中重新声明。
     *
     * @param table          表名
     * @param blueprint      蓝图（用于引用标识符）
     * @param modifyColumns  需修改的列定义列表
     */
    private void sqliteRecreateTableForModify(String table, Blueprint blueprint, List<ColumnDefinition> modifyColumns) {
        log.info("[migration] SQLite 重建表以修改字段: {}", table);
        List<Map<String, Object>> existingCols = jdbcTemplate.queryForList("PRAGMA table_info(" + quote(table) + ")");

        String tempTable = table + "_jaravel_temp";
        StringBuilder createSql = new StringBuilder("CREATE TABLE " + quote(tempTable) + " (");
        List<String> colDefs = new ArrayList<>();
        List<String> colNames = new ArrayList<>();

        for (Map<String, Object> row : existingCols) {
            String colName = String.valueOf(row.get("name"));
            String colType = String.valueOf(row.get("type"));
            int notnull = row.get("notnull") != null ? ((Number) row.get("notnull")).intValue() : 0;
            Object dfltObj = row.get("dflt_value");
            String dflt = dfltObj != null ? String.valueOf(dfltObj) : null;
            int pk = row.get("pk") != null ? ((Number) row.get("pk")).intValue() : 0;

            // 查找该列是否在修改列表中
            ColumnDefinition modifyCol = null;
            for (ColumnDefinition c : modifyColumns) {
                if (c.getName().equals(colName)) {
                    modifyCol = c;
                    break;
                }
            }

            if (modifyCol != null) {
                // 使用新定义（toSql 已按 SQLite 方言生成）
                colDefs.add(modifyCol.toSql());
            } else {
                // 从 PRAGMA 重建列定义
                StringBuilder def = new StringBuilder();
                def.append(quote(colName)).append(" ").append(colType);
                if (pk > 0) {
                    if ("INTEGER".equalsIgnoreCase(colType)) {
                        def.append(" PRIMARY KEY AUTOINCREMENT");
                    } else {
                        def.append(" PRIMARY KEY");
                    }
                } else if (notnull == 1) {
                    def.append(" NOT NULL");
                } else {
                    def.append(" NULL");
                }
                if (dflt != null) {
                    def.append(" DEFAULT ").append(dflt);
                }
                colDefs.add(def.toString());
            }
            colNames.add(colName);
        }

        createSql.append(String.join(", ", colDefs)).append(")");
        log.info("[migration] SQLite 重建临时表: {}", createSql);
        jdbcTemplate.execute(createSql.toString());

        String cols = colNames.stream().map(this::quote).collect(Collectors.joining(", "));
        String copySql = "INSERT INTO " + quote(tempTable) + " (" + cols + ") SELECT " + cols + " FROM " + quote(table);
        log.info("[migration] SQLite 复制数据: {}", copySql);
        jdbcTemplate.execute(copySql);

        String dropSql = "DROP TABLE " + quote(table);
        log.info("[migration] SQLite 删除原表: {}", dropSql);
        jdbcTemplate.execute(dropSql);

        String renameSql = "ALTER TABLE " + quote(tempTable) + " RENAME TO " + quote(table);
        log.info("[migration] SQLite 重命名临时表: {}", renameSql);
        jdbcTemplate.execute(renameSql);
    }

    /**
     * 删除表（如存在），对齐 Laravel Schema::dropIfExists。
     * <p>
     * 若 down() 中创建了多张表，应多次调用本方法逐一删除。
     *
     * @param table 表名
     */
    public void dropIfExists(String table) {
        String sql = "DROP TABLE IF EXISTS " + quote(table);
        log.info("[migration] DROP: {}", sql);
        jdbcTemplate.execute(sql);
    }

    /** 删除表，对齐 Laravel Schema::drop */
    public void drop(String table) {
        String sql = "DROP TABLE " + quote(table);
        log.info("[migration] DROP: {}", sql);
        jdbcTemplate.execute(sql);
    }

    /**
     * 重命名表，对齐 Laravel Schema::rename。
     * <ul>
     *   <li>MySQL：{@code RENAME TABLE `old` TO `new`}</li>
     *   <li>SQLite / H2：{@code ALTER TABLE `old` RENAME TO `new`}</li>
     *   <li>SQL Server：{@code sp_rename 'old', 'new'}</li>
     * </ul>
     */
    public void rename(String from, String to) {
        String sql;
        if (isSqlServer()) {
            // SQL Server: sp_rename 'old_table', 'new_table'
            sql = "sp_rename '" + from + "', '" + to + "'";
        } else if (isSqlite() || isH2()) {
            // SQLite / H2: ALTER TABLE ... RENAME TO ...
            sql = "ALTER TABLE " + quote(from) + " RENAME TO " + quote(to);
        } else {
            // MySQL: RENAME TABLE ... TO ...
            sql = "RENAME TABLE " + quote(from) + " TO " + quote(to);
        }
        log.info("[migration] RENAME: {}", sql);
        jdbcTemplate.execute(sql);
    }

    /**
     * 判断表是否存在。
     * <ul>
     *   <li>SQLite：查 sqlite_master</li>
     *   <li>H2：查 INFORMATION_SCHEMA.TABLES</li>
     *   <li>SQL Server：查 sys.tables</li>
     *   <li>MySQL：查 information_schema.tables</li>
     * </ul>
     */
    public boolean hasTable(String table) {
        try {
            if (isSqlite()) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    Integer.class, table);
                return count != null && count > 0;
            } else if (isH2()) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = UPPER(?)",
                    Integer.class, table);
                return count != null && count > 0;
            } else if (isSqlServer()) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                    Integer.class, table);
                return count != null && count > 0;
            } else {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断列是否存在。
     * <ul>
     *   <li>SQLite：PRAGMA table_info</li>
     *   <li>H2：查 INFORMATION_SCHEMA.COLUMNS</li>
     *   <li>SQL Server：查 sys.columns</li>
     *   <li>MySQL：查 information_schema.columns</li>
     * </ul>
     */
    public boolean hasColumn(String table, String column) {
        try {
            if (isSqlite()) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "PRAGMA table_info(" + quote(table) + ")");
                return rows.stream()
                    .map(row -> String.valueOf(row.get("name")))
                    .anyMatch(c -> c.equalsIgnoreCase(column));
            } else if (isH2()) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = UPPER(?) AND COLUMN_NAME = UPPER(?)",
                    Integer.class, table, column);
                return count != null && count > 0;
            } else if (isSqlServer()) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id WHERE t.name = ? AND c.name = ?",
                    Integer.class, table, column);
                return count != null && count > 0;
            } else {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    Integer.class, table, column);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
