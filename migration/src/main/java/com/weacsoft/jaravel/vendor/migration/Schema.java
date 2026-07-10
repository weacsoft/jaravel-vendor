package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>
 * 方言相关逻辑全部委托给 {@link Dialect} 实现（通过 {@link DialectFactory#detect} 自动检测），
 * 支持 MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle。
 * 标识符引用、自增语法、类型映射、系统目录查询、ALTER TABLE 语法等差异由各方言实现类处理，
 * 本类只负责编排执行流程。上层 API 无需感知具体方言。
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

    private final JdbcExecutor jdbc;
    /** 当前数据库方言 */
    private final Dialect dialect;

    public Schema(DataSource dataSource) {
        this.jdbc = new JdbcExecutor(dataSource);
        this.dialect = DialectFactory.detect(dataSource);
    }

    /** 按方言对标识符加引号 */
    private String quote(String identifier) {
        return dialect.quote(identifier);
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
        blueprint.setDialect(dialect);
        blueprint.setTableOptions(dialect.tableOptions());
        definition.accept(blueprint);
        String sql = blueprint.toCreateSql();
        log.info("[migration] CREATE TABLE: {}", sql);
        jdbc.execute(sql);
        for (String indexSql : blueprint.toIndexSql()) {
            log.info("[migration] INDEX: {}", indexSql);
            jdbc.execute(indexSql);
        }
        for (String fkSql : blueprint.toForeignKeySql()) {
            log.info("[migration] FOREIGN KEY: {}", fkSql);
            jdbc.execute(fkSql);
        }
    }

    /**
     * 修改已有表，对齐 Laravel Schema::table。
     * <p>
     * 支持增加字段、修改字段、删除字段、重命名字段等操作。
     * 修改字段的 ALTER TABLE 语法由 {@link Dialect#modifyColumnSql} 生成，
     * SQLite 走重建表流程（{@link Dialect#needsTableRecreationForModify}）。
     *
     * @param table      表名
     * @param definition 表结构修改回调
     */
    public void table(String table, Consumer<Blueprint> definition) {
        Blueprint blueprint = new Blueprint(table);
        blueprint.setDialect(dialect);
        blueprint.setTableOptions(dialect.tableOptions());
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
            jdbc.execute(sql);
        }

        // 2. 修改字段：按方言处理
        if (!modifyColumns.isEmpty()) {
            if (dialect.needsTableRecreationForModify()) {
                // SQLite 不支持 MODIFY，走重建表流程
                sqliteRecreateTableForModify(table, blueprint, modifyColumns);
            } else {
                for (ColumnDefinition c : modifyColumns) {
                    String sql = dialect.modifyColumnSql(quote(table), c);
                    log.info("[migration] MODIFY COLUMN: {}", sql);
                    jdbc.execute(sql);
                }
            }
        }

        // 3. 索引
        for (String indexSql : blueprint.toIndexSql()) {
            log.info("[migration] INDEX: {}", indexSql);
            jdbc.execute(indexSql);
        }
        // 4. 外键
        for (String fkSql : blueprint.toForeignKeySql()) {
            log.info("[migration] FOREIGN KEY: {}", fkSql);
            jdbc.execute(fkSql);
        }
        // 5. 其它 ALTER（dropColumn / renameColumn / dropIndex）
        for (String alterSql : blueprint.toAlterSql()) {
            log.info("[migration] ALTER: {}", alterSql);
            jdbc.execute(alterSql);
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
     */
    private void sqliteRecreateTableForModify(String table, Blueprint blueprint, List<ColumnDefinition> modifyColumns) {
        log.info("[migration] SQLite 重建表以修改字段: {}", table);
        List<Map<String, Object>> existingCols = jdbc.queryForMapList(dialect.pragmaTableInfoSql(quote(table)));

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
        jdbc.execute(createSql.toString());

        String cols = colNames.stream().map(this::quote).collect(Collectors.joining(", "));
        String copySql = "INSERT INTO " + quote(tempTable) + " (" + cols + ") SELECT " + cols + " FROM " + quote(table);
        log.info("[migration] SQLite 复制数据: {}", copySql);
        jdbc.execute(copySql);

        String dropSql = "DROP TABLE " + quote(table);
        log.info("[migration] SQLite 删除原表: {}", dropSql);
        jdbc.execute(dropSql);

        String renameSql = "ALTER TABLE " + quote(tempTable) + " RENAME TO " + quote(table);
        log.info("[migration] SQLite 重命名临时表: {}", renameSql);
        jdbc.execute(renameSql);
    }

    /**
     * 删除表（如存在），对齐 Laravel Schema::dropIfExists。
     *
     * @param table 表名
     */
    public void dropIfExists(String table) {
        String sql = "DROP TABLE IF EXISTS " + quote(table);
        log.info("[migration] DROP: {}", sql);
        jdbc.execute(sql);
    }

    /** 删除表，对齐 Laravel Schema::drop */
    public void drop(String table) {
        String sql = "DROP TABLE " + quote(table);
        log.info("[migration] DROP: {}", sql);
        jdbc.execute(sql);
    }

    /**
     * 重命名表，对齐 Laravel Schema::rename。
     * 完整 SQL 由 {@link Dialect#renameTableSql} 生成。
     */
    public void rename(String from, String to) {
        String sql = dialect.renameTableSql(from, to);
        log.info("[migration] RENAME: {}", sql);
        jdbc.execute(sql);
    }

    /**
     * 判断表是否存在。
     * SQL 由 {@link Dialect#hasTableSql} 提供，SQLite 走 PRAGMA（由 {@link Dialect#usesPragmaForColumnInfo} 判断）。
     */
    public boolean hasTable(String table) {
        try {
            String sql = dialect.hasTableSql();
            if (sql == null) {
                return false;
            }
            Integer count = jdbc.queryForObject(sql, Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断列是否存在。
     * <p>
     * SQLite 使用 PRAGMA table_info 读取列信息（由 {@link Dialect#usesPragmaForColumnInfo} 判断），
     * 其余方言使用标准 SQL 查询（由 {@link Dialect#hasColumnSql} 提供）。
     */
    public boolean hasColumn(String table, String column) {
        try {
            if (dialect.usesPragmaForColumnInfo()) {
                List<Map<String, Object>> rows = jdbc.queryForMapList(
                    dialect.pragmaTableInfoSql(quote(table)));
                return rows.stream()
                    .map(row -> String.valueOf(row.get("name")))
                    .anyMatch(c -> c.equalsIgnoreCase(column));
            } else {
                String sql = dialect.hasColumnSql();
                if (sql == null) {
                    return false;
                }
                Integer count = jdbc.queryForObject(sql, Integer.class, table, column);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    JdbcExecutor getJdbcExecutor() {
        return jdbc;
    }
}
