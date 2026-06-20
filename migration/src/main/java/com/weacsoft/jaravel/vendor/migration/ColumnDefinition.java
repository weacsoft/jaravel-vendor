package com.weacsoft.jaravel.vendor.migration;

/**
 * 列定义，对齐 Laravel Blueprint 中的列修饰链。
 * <p>
 * 由 {@link Blueprint} 的各类字段方法创建，通过链式调用追加修饰：
 * {@code table.string("email").nullable().unique().comment("邮箱");}
 * <p>
 * 支持多数据库方言（MySQL、SQLite、H2、SQL Server），按 {@link Blueprint#getDatabaseType()}
 * 生成对应的自增语法（AUTO_INCREMENT / AUTOINCREMENT / IDENTITY(1,1)）、类型映射与标识符引用。
 */
public class ColumnDefinition {

    private final Blueprint blueprint;
    private final String name;
    private String type;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private boolean nullable = false;
    private String defaultValue;
    private boolean hasDefault = false;
    private String comment;
    private boolean autoIncrement = false;
    private boolean primary = false;
    private boolean unique = false;
    private boolean indexed = false;
    private boolean unsigned = false;
    private String afterColumn;
    private boolean isChange = false;

    public ColumnDefinition(Blueprint blueprint, String name) {
        this.blueprint = blueprint;
        this.name = name;
    }

    /** 标记该列为可空 */
    public ColumnDefinition nullable() {
        this.nullable = true;
        return this;
    }

    /** 标记该列为非空 */
    public ColumnDefinition notNull() {
        this.nullable = false;
        return this;
    }

    /** 设置默认值 */
    public ColumnDefinition defaultValue(Object value) {
        this.defaultValue = value == null ? null : String.valueOf(value);
        this.hasDefault = true;
        return this;
    }

    /** 设置注释 */
    public ColumnDefinition comment(String comment) {
        this.comment = comment;
        return this;
    }

    /** 标记自增（通常配合整数主键） */
    public ColumnDefinition autoIncrement() {
        this.autoIncrement = true;
        return this;
    }

    /** 标记为主键 */
    public ColumnDefinition primary() {
        this.primary = true;
        return this;
    }

    /** 标记唯一索引 */
    public ColumnDefinition unique() {
        this.unique = true;
        return this;
    }

    /** 标记普通索引 */
    public ColumnDefinition index() {
        this.indexed = true;
        return this;
    }

    /** 标记无符号（MySQL 数值类型） */
    public ColumnDefinition unsigned() {
        this.unsigned = true;
        return this;
    }

    /** ALTER TABLE 时将字段置于指定字段之后（MySQL） */
    public ColumnDefinition after(String column) {
        this.afterColumn = column;
        return this;
    }

    /** 标记为修改已有字段（ALTER TABLE MODIFY / ALTER COLUMN） */
    public ColumnDefinition change() {
        this.isChange = true;
        return this;
    }

    /** 按方言引用标识符 */
    private String quote(String identifier) {
        if (blueprint != null) {
            return blueprint.quote(identifier);
        }
        return "`" + identifier + "`";
    }

    /**
     * 返回该列的完整 SQL 片段（不含索引/主键约束，那些由 Blueprint 统一处理）。
     * 按数据库方言生成自增语法、类型映射与标识符引用。
     */
    public String toSql() {
        boolean isSqlite = blueprint != null && blueprint.isSqlite();
        boolean isSqlServer = blueprint != null && blueprint.isSqlServer();
        StringBuilder sb = new StringBuilder();
        sb.append(quote(name)).append(' ');

        // SQLite 自增主键：必须是 `INTEGER PRIMARY KEY AUTOINCREMENT`
        if (autoIncrement && primary && isSqlite) {
            sb.append("INTEGER PRIMARY KEY AUTOINCREMENT");
            appendDefaultClause(sb);
            // SQLite 不支持 COMMENT / AFTER，且 PRIMARY KEY 隐含 NOT NULL，故直接返回
            return sb.toString();
        }

        // SQL Server 自增主键：使用 IDENTITY(1,1)，类型须为整数族
        if (autoIncrement && primary && isSqlServer) {
            sb.append(buildTypeClause()).append(" IDENTITY(1,1) PRIMARY KEY");
            // SQL Server 不支持 COMMENT / AFTER
            return sb.toString();
        }

        sb.append(buildTypeClause());
        if (autoIncrement) {
            if (isSqlite) {
                sb.append(" AUTOINCREMENT");
            } else if (isSqlServer) {
                // SQL Server 非主键自增（较少见）：仍用 IDENTITY(1,1)
                sb.append(" IDENTITY(1,1)");
            } else {
                // MySQL / H2
                sb.append(" AUTO_INCREMENT");
            }
        }
        // 单列主键内联声明；复合主键（>1 列）由 Blueprint 统一生成 PRIMARY KEY(...)
        if (primary && (blueprint == null || blueprint.getPrimaryKeyCount() <= 1)) {
            sb.append(" PRIMARY KEY");
        }
        if (!nullable) {
            sb.append(" NOT NULL");
        } else {
            sb.append(" NULL");
        }
        appendDefaultClause(sb);
        // COMMENT 仅 MySQL 支持
        if (comment != null && !comment.isEmpty() && !isSqlite && !isSqlServer) {
            sb.append(" COMMENT '").append(escape(comment)).append('\'');
        }
        // AFTER 仅 MySQL 支持
        if (afterColumn != null && !afterColumn.isEmpty() && !isSqlite && !isSqlServer) {
            sb.append(" AFTER ").append(quote(afterColumn));
        }
        return sb.toString();
    }

    /**
     * 生成 ALTER COLUMN 修改片段（不含 {@code ALTER TABLE} 前缀），供 {@link Schema#table} 修改字段时使用。
     * <ul>
     *   <li>SQL Server：{@code ALTER COLUMN} 仅支持类型与可空性，不含 PRIMARY KEY / IDENTITY / DEFAULT / COMMENT，
     *       故只返回 {@code [name] type [NULL|NOT NULL]}；</li>
     *   <li>MySQL / H2：返回完整列定义（同 {@link #toSql()}），配合 {@code MODIFY} / {@code ALTER COLUMN} 使用；</li>
     *   <li>SQLite：不直接支持 MODIFY，由 {@link Schema} 走重建表流程，此处返回完整定义用于新表 CREATE。</li>
     * </ul>
     */
    public String toModifyColumnFragment() {
        if (blueprint != null && blueprint.isSqlServer()) {
            StringBuilder sb = new StringBuilder();
            sb.append(quote(name)).append(' ');
            sb.append(buildTypeClause());
            if (!nullable) {
                sb.append(" NOT NULL");
            } else {
                sb.append(" NULL");
            }
            return sb.toString();
        }
        return toSql();
    }

    /** 追加 DEFAULT 子句 */
    private void appendDefaultClause(StringBuilder sb) {
        if (!hasDefault) {
            return;
        }
        if (defaultValue == null) {
            sb.append(" DEFAULT NULL");
        } else if (isNumeric(defaultValue)) {
            sb.append(" DEFAULT ").append(defaultValue);
        } else if ("CURRENT_TIMESTAMP".equalsIgnoreCase(defaultValue)) {
            sb.append(" DEFAULT CURRENT_TIMESTAMP");
        } else {
            sb.append(" DEFAULT '").append(escape(defaultValue)).append('\'');
        }
    }

    /**
     * 按方言构建类型子句。
     * <ul>
     *   <li>MySQL / SQLite / H2：使用 MySQL 风格类型（VARCHAR、TINYINT(1) 等）；</li>
     *   <li>SQL Server：使用 SQL Server 类型（VARCHAR、BIT、DATETIME2、NVARCHAR(MAX) 等），
     *       不支持 UNSIGNED（忽略）、不支持 YEAR（用 SMALLINT 模拟）、TIMESTAMP 用 DATETIME2
     *       （因 SQL Server 的 TIMESTAMP 实为 rowversion，语义不同）。</li>
     * </ul>
     */
    private String buildTypeClause() {
        boolean isSqlServer = blueprint != null && blueprint.isSqlServer();
        if (isSqlServer) {
            return buildSqlServerTypeClause();
        }
        return buildMysqlTypeClause();
    }

    /** MySQL / SQLite / H2 类型映射（原有逻辑） */
    private String buildMysqlTypeClause() {
        switch (type) {
            case "string":
                return "VARCHAR(" + (length != null ? length : 255) + ")";
            case "char":
                return "CHAR(" + (length != null ? length : 255) + ")";
            case "integer":
                return unsigned ? "INT UNSIGNED" : "INT";
            case "bigInteger":
                return unsigned ? "BIGINT UNSIGNED" : "BIGINT";
            case "tinyInteger":
                return unsigned ? "TINYINT UNSIGNED" : "TINYINT";
            case "smallInteger":
                return unsigned ? "SMALLINT UNSIGNED" : "SMALLINT";
            case "mediumInteger":
                return unsigned ? "MEDIUMINT UNSIGNED" : "MEDIUMINT";
            case "text":
                return "TEXT";
            case "mediumText":
                return "MEDIUMTEXT";
            case "longText":
                return "LONGTEXT";
            case "boolean":
                return "TINYINT(1)";
            case "decimal":
                return "DECIMAL(" + (precision != null ? precision : 8) + "," + (scale != null ? scale : 2) + ")";
            case "float":
                return "FLOAT";
            case "double":
                return "DOUBLE";
            case "date":
                return "DATE";
            case "dateTime":
                return "DATETIME";
            case "time":
                return "TIME";
            case "timestamp":
                return "TIMESTAMP";
            case "year":
                return "YEAR";
            case "binary":
                return "LONGBLOB";
            case "json":
                return "JSON";
            default:
                return type;
        }
    }

    /** SQL Server 类型映射 */
    private String buildSqlServerTypeClause() {
        switch (type) {
            case "string":
                return "VARCHAR(" + (length != null ? length : 255) + ")";
            case "char":
                return "CHAR(" + (length != null ? length : 255) + ")";
            case "integer":
                // SQL Server 不支持 UNSIGNED，忽略
                return "INT";
            case "bigInteger":
                return "BIGINT";
            case "tinyInteger":
                return "TINYINT";
            case "smallInteger":
                return "SMALLINT";
            case "mediumInteger":
                // SQL Server 无 MEDIUMINT，用 INT 替代
                return "INT";
            case "text":
                return "VARCHAR(MAX)";
            case "mediumText":
                return "VARCHAR(MAX)";
            case "longText":
                return "VARCHAR(MAX)";
            case "boolean":
                return "BIT";
            case "decimal":
                return "DECIMAL(" + (precision != null ? precision : 8) + "," + (scale != null ? scale : 2) + ")";
            case "float":
                return "FLOAT(24)";
            case "double":
                // SQL Server 无 DOUBLE，用 FLOAT(53) 等价
                return "FLOAT(53)";
            case "date":
                return "DATE";
            case "dateTime":
                return "DATETIME2";
            case "time":
                return "TIME";
            case "timestamp":
                // SQL Server 的 TIMESTAMP 实为 rowversion，语义不同，改用 DATETIME2
                return "DATETIME2";
            case "year":
                // SQL Server 无 YEAR 类型，用 SMALLINT 模拟
                return "SMALLINT";
            case "binary":
                return "VARBINARY(MAX)";
            case "json":
                // SQL Server 无原生 JSON 类型，用 NVARCHAR(MAX) 存储
                return "NVARCHAR(MAX)";
            default:
                return type;
        }
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            new java.math.BigDecimal(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    // ---- getters（供 Blueprint 使用） ----

    public String getName() { return name; }
    public boolean isPrimary() { return primary; }
    public boolean isUnique() { return unique; }
    public boolean isIndexed() { return indexed; }
    public boolean isChange() { return isChange; }
    public Blueprint getBlueprint() { return blueprint; }

    void setType(String type) { this.type = type; }
    void setLength(Integer length) { this.length = length; }
    void setPrecision(Integer precision) { this.precision = precision; }
    void setScale(Integer scale) { this.scale = scale; }
}
