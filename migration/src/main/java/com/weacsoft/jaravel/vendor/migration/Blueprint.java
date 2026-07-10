package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 表结构蓝图，对齐 Laravel 的 {@code Illuminate\Database\Schema\Blueprint}。
 * <p>
 * 通过流式 API 声明表结构，最终由 {@link Schema} 执行生成 SQL DDL。
 * <p>
 * 方言相关逻辑（标识符引用、自增语法、类型映射等）全部委托给 {@link Dialect} 实现，
 * 支持 MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle。
 * 通过 {@link #setDialect(Dialect)} 注入方言（由 {@link Schema} 自动完成），
 * 也兼容 {@link #setDatabaseType(String)} 旧接口（内部转换为 Dialect）。
 * <pre>
 * schema.create("users", table -> {
 *     table.id();
 *     table.string("name");
 *     table.string("email").unique();
 *     table.timestamps();
 *     table.softDeletes();
 * });
 * </pre>
 * <p>
 * <b>多表支持</b>：单个 {@link Schema#create(String, java.util.function.Consumer)} 调用只处理一张表，
 * 但一次迁移的 {@code up()} 可连续调用多次 {@code create()} 或 {@code table()} 处理多张表。
 * Blueprint 同时支持 CREATE（建表）与 ALTER（改表）两种模式：
 * <ul>
 *   <li>CREATE 模式：由 {@link Schema#create(String, java.util.function.Consumer)} 触发，调用 {@link #toCreateSql()}；</li>
 *   <li>ALTER 模式：由 {@link Schema#table(String, java.util.function.Consumer)} 触发，
 *       通过 {@link #getColumns()}（含 {@link ColumnDefinition#isChange()} 标记）与 {@link #toAlterSql()} 生成 ALTER 语句。</li>
 * </ul>
 */
public class Blueprint {

    private final String table;
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
    private final List<String> indexCommands = new ArrayList<>();
    private final List<String> dropCommands = new ArrayList<>();
    /** 建表附加选项，由 Dialect.tableOptions() 提供 */
    private String tableOptions = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    /** 数据库产品名（小写），用于方言判断，默认 mysql */
    private String databaseType = "mysql";
    /** 当前方言实现，由 Schema 注入或由 setDatabaseType 间接创建 */
    private Dialect dialect;

    public Blueprint(String table) {
        this.table = table;
    }

    /** 设置建表附加选项（如引擎、字符集），供 Schema 按数据库方言调整 */
    public void setTableOptions(String tableOptions) {
        this.tableOptions = tableOptions == null ? "" : tableOptions;
    }

    /**
     * 注入方言实现（由 {@link Schema} 自动调用），同时同步 databaseType 和 tableOptions。
     * <p>
     * 这是推荐的方言注入方式，直接传入 {@link Dialect} 实例。
     */
    void setDialect(Dialect dialect) {
        this.dialect = dialect;
        this.databaseType = dialect.getName();
    }

    /** 获取当前方言（延迟初始化，兼容旧 setDatabaseType 路径） */
    Dialect getDialect() {
        if (dialect == null) {
            dialect = DialectFactory.create(databaseType);
        }
        return dialect;
    }

    /**
     * 设置数据库产品名（方言），兼容旧接口。
     * <p>
     * 内部通过 {@link DialectFactory#create(String)} 转换为 {@link Dialect} 实现，
     * 推荐使用 {@link #setDialect(Dialect)} 直接注入。
     *
     * @param databaseType 数据库产品名（大小写不敏感）
     */
    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType == null ? "mysql" : databaseType.toLowerCase();
        this.dialect = DialectFactory.create(this.databaseType);
    }

    /** 获取数据库产品名（小写） */
    public String getDatabaseType() {
        return databaseType;
    }

    /** 是否为 SQLite 方言 */
    public boolean isSqlite() {
        return getDialect().getName().contains("sqlite");
    }

    /** 是否为 H2 方言 */
    public boolean isH2() {
        return getDialect().getName().contains("h2");
    }

    /** 是否为 SQL Server 方言 */
    public boolean isSqlServer() {
        return getDialect().getName().contains("sql server");
    }

    /** 是否为 MySQL 方言 */
    public boolean isMysql() {
        return getDialect().getName().contains("mysql");
    }

    /** 是否为 PostgreSQL 方言 */
    public boolean isPostgresql() {
        return getDialect().getName().contains("postgresql");
    }

    /** 是否为 Oracle 方言 */
    public boolean isOracle() {
        return getDialect().getName().contains("oracle");
    }

    /**
     * 按方言对标识符加引号，委托给 {@link Dialect#quote}。
     *
     * @param identifier 标识符（表名/列名/索引名等）
     * @return 加引号后的标识符
     */
    public String quote(String identifier) {
        return getDialect().quote(identifier);
    }

    /** 返回主键列数量（用于区分单列主键与复合主键） */
    int getPrimaryKeyCount() {
        int count = 0;
        for (ColumnDefinition c : columns) {
            if (c.isPrimary()) {
                count++;
            }
        }
        return count;
    }

    // ==================== 字段类型（对齐 Laravel） ====================

    /** BIGINT 自增主键，等价于 Laravel id() */
    public ColumnDefinition id() {
        return bigIncrements("id");
    }

    /** BIGINT 自增主键 */
    public ColumnDefinition bigIncrements(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("bigInteger");
        c.autoIncrement();
        c.primary();
        columns.add(c);
        return c;
    }

    /** INT 自增主键 */
    public ColumnDefinition increments(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("integer");
        c.autoIncrement();
        c.primary();
        columns.add(c);
        return c;
    }

    /** VARCHAR(255) 或指定长度 */
    public ColumnDefinition string(String column) {
        return string(column, 255);
    }

    public ColumnDefinition string(String column, int length) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("string");
        c.setLength(length);
        columns.add(c);
        return c;
    }

    /** CHAR */
    public ColumnDefinition charColumn(String column, int length) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("char");
        c.setLength(length);
        columns.add(c);
        return c;
    }

    /** INT */
    public ColumnDefinition integer(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("integer");
        columns.add(c);
        return c;
    }

    /** BIGINT */
    public ColumnDefinition bigInteger(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("bigInteger");
        columns.add(c);
        return c;
    }

    /** TINYINT */
    public ColumnDefinition tinyInteger(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("tinyInteger");
        columns.add(c);
        return c;
    }

    /** SMALLINT */
    public ColumnDefinition smallInteger(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("smallInteger");
        columns.add(c);
        return c;
    }

    /** TEXT */
    public ColumnDefinition text(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("text");
        columns.add(c);
        return c;
    }

    /** MEDIUMTEXT */
    public ColumnDefinition mediumText(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("mediumText");
        columns.add(c);
        return c;
    }

    /** LONGTEXT */
    public ColumnDefinition longText(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("longText");
        columns.add(c);
        return c;
    }

    /** BOOLEAN (TINYINT(1)) */
    public ColumnDefinition booleanColumn(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("boolean");
        columns.add(c);
        return c;
    }

    /** DECIMAL(8,2) 或指定精度 */
    public ColumnDefinition decimal(String column) {
        return decimal(column, 8, 2);
    }

    public ColumnDefinition decimal(String column, int precision, int scale) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("decimal");
        c.setPrecision(precision);
        c.setScale(scale);
        columns.add(c);
        return c;
    }

    /** FLOAT */
    public ColumnDefinition floatColumn(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("float");
        columns.add(c);
        return c;
    }

    /** DOUBLE */
    public ColumnDefinition doubleColumn(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("double");
        columns.add(c);
        return c;
    }

    /** DATE */
    public ColumnDefinition date(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("date");
        columns.add(c);
        return c;
    }

    /** DATETIME */
    public ColumnDefinition dateTime(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("dateTime");
        columns.add(c);
        return c;
    }

    /** TIMESTAMP */
    public ColumnDefinition timestamp(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("timestamp");
        columns.add(c);
        return c;
    }

    /** TIME */
    public ColumnDefinition time(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("time");
        columns.add(c);
        return c;
    }

    /** YEAR */
    public ColumnDefinition year(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("year");
        columns.add(c);
        return c;
    }

    /** LONGBLOB */
    public ColumnDefinition binary(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("binary");
        columns.add(c);
        return c;
    }

    /** JSON */
    public ColumnDefinition json(String column) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("json");
        columns.add(c);
        return c;
    }

    /** 通用枚举字段（用 VARCHAR 模拟） */
    public ColumnDefinition enumColumn(String column, String... allowed) {
        ColumnDefinition c = new ColumnDefinition(this, column);
        c.setType("string");
        c.setLength(255);
        columns.add(c);
        return c;
    }

    // ==================== 时间戳与软删除 ====================

    /** 添加 created_at、updated_at 两个时间戳字段 */
    public void timestamps() {
        timestamp("created_at").nullable();
        timestamp("updated_at").nullable();
    }

    /** 添加 deleted_at 软删除字段 */
    public ColumnDefinition softDeletes() {
        return timestamp("deleted_at").nullable();
    }

    /** remember_token VARCHAR(100) NULL */
    public ColumnDefinition rememberToken() {
        return string("remember_token", 100).nullable();
    }

    // ==================== 索引与外键 ====================

    /** 单列普通索引 */
    public void index(String column) {
        indexCommands.add("CREATE INDEX " + quote(table + "_" + column + "_index")
            + " ON " + quote(table) + "(" + quote(column) + ")");
    }

    /** 复合索引 */
    public void index(String... columns) {
        StringBuilder name = new StringBuilder(table);
        for (String c : columns) name.append("_").append(c);
        name.append("_index");
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) cols.append(",");
            cols.append(quote(columns[i]));
        }
        indexCommands.add("CREATE INDEX " + quote(name.toString()) + " ON " + quote(table) + "(" + cols + ")");
    }

    /** 单列唯一索引 */
    public void unique(String column) {
        indexCommands.add("CREATE UNIQUE INDEX " + quote(table + "_" + column + "_unique")
            + " ON " + quote(table) + "(" + quote(column) + ")");
    }

    /** 复合唯一索引 */
    public void unique(String... columns) {
        StringBuilder name = new StringBuilder(table);
        for (String c : columns) name.append("_").append(c);
        name.append("_unique");
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) cols.append(",");
            cols.append(quote(columns[i]));
        }
        indexCommands.add("CREATE UNIQUE INDEX " + quote(name.toString()) + " ON " + quote(table) + "(" + cols + ")");
    }

    /** 外键 */
    public ForeignKeyDefinition foreign(String column) {
        ForeignKeyDefinition fk = new ForeignKeyDefinition(column);
        fk.setBlueprint(this);
        foreignKeys.add(fk);
        return fk;
    }

    /** 删除列（ALTER TABLE DROP COLUMN） */
    public void dropColumn(String column) {
        dropCommands.add("ALTER TABLE " + quote(table) + " DROP COLUMN " + quote(column));
    }

    /** 删除索引（按方言生成 DROP INDEX 语法） */
    public void dropIndex(String indexName) {
        dropCommands.add(getDialect().dropIndexSql(indexName, quote(table)));
    }

    /** 重命名列 */
    public void renameColumn(String from, String to) {
        // SQLite 3.25.0+ / SQL Server / MySQL 8+ / H2 均支持 RENAME COLUMN 语法
        dropCommands.add("ALTER TABLE " + quote(table) + " RENAME COLUMN " + quote(from) + " TO " + quote(to));
    }

    // ==================== SQL 生成 ====================

    /** 生成 CREATE TABLE 语句 */
    public String toCreateSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quote(table)).append(" (");
        List<String> parts = new ArrayList<>();
        for (ColumnDefinition c : columns) {
            parts.add(c.toSql());
        }
        // 复合主键支持（若有多个 primary 列）
        List<String> pkCols = new ArrayList<>();
        for (ColumnDefinition c : columns) {
            if (c.isPrimary()) pkCols.add(c.getName());
        }
        if (pkCols.size() > 1) {
            StringBuilder pk = new StringBuilder("PRIMARY KEY (");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) pk.append(",");
                pk.append(quote(pkCols.get(i)));
            }
            pk.append(")");
            parts.add(pk.toString());
        }
        sb.append(String.join(", ", parts));
        sb.append(")");
        sb.append(tableOptions);
        return sb.toString();
    }

    /** 生成建表后的索引语句列表 */
    public List<String> toIndexSql() {
        return new ArrayList<>(indexCommands);
    }

    /** 生成外键语句列表 */
    public List<String> toForeignKeySql() {
        List<String> list = new ArrayList<>();
        for (ForeignKeyDefinition fk : foreignKeys) {
            list.add(fk.toSql(table));
        }
        return list;
    }

    /** 生成 DROP TABLE 语句 */
    public String toDropSql() {
        return "DROP TABLE IF EXISTS " + quote(table);
    }

    /** 生成 ALTER 相关语句（dropColumn/renameColumn/dropIndex） */
    public List<String> toAlterSql() {
        return new ArrayList<>(dropCommands);
    }

    public String getTable() { return table; }
    public List<ColumnDefinition> getColumns() { return columns; }
}
