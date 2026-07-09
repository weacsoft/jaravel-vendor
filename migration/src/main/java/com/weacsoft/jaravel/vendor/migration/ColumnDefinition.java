package com.weacsoft.jaravel.vendor.migration;

/**
 * 列定义，对齐 Laravel Blueprint 中的列修饰链。
 * <p>
 * 由 {@link Blueprint} 的各类字段方法创建，通过链式调用追加修饰：
 * {@code table.string("email").nullable().unique().comment("邮箱");}
 * <p>
 * 方言相关的类型映射、自增语法、标识符引用等全部委托给 {@link Dialect} 实现，
 * 支持 MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle。上层 API 无需感知具体方言。
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

    /** 获取当前方言（从 Blueprint 获取，无 Blueprint 时默认 MySQL） */
    private Dialect getDialect() {
        if (blueprint != null) {
            return blueprint.getDialect();
        }
        return DialectFactory.create("mysql");
    }

    /** 按方言引用标识符 */
    private String quote(String identifier) {
        return getDialect().quote(identifier);
    }

    /**
     * 返回该列的完整 SQL 片段（不含索引/主键约束，那些由 Blueprint 统一处理）。
     * <p>
     * 方言相关逻辑全部委托给 {@link Dialect}：
     * <ul>
     *   <li>自增主键特殊格式（SQLite/PostgreSQL/Oracle）由 {@link Dialect#autoIncrementPrimaryKeyTypeClause} 处理</li>
     *   <li>标准自增子句由 {@link Dialect#autoIncrementClause} 处理</li>
     *   <li>类型映射由 {@link Dialect#mapType} 处理</li>
     *   <li>COMMENT 支持由 {@link Dialect#supportsColumnComment} 控制</li>
     *   <li>AFTER 支持由 {@link Dialect#supportsAfterColumn} 控制</li>
     * </ul>
     */
    public String toSql() {
        Dialect dialect = getDialect();
        StringBuilder sb = new StringBuilder();
        sb.append(quote(name)).append(' ');

        // 自增主键：方言可能有特殊格式（如 SQLite 的 INTEGER PRIMARY KEY AUTOINCREMENT，
        // PostgreSQL 的 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY，
        // Oracle 的 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY）
        if (autoIncrement && primary) {
            String special = dialect.autoIncrementPrimaryKeyTypeClause(type);
            if (special != null) {
                sb.append(special);
                appendDefaultClause(sb);
                // 特殊格式已包含 PRIMARY KEY，且不支持 COMMENT / AFTER，直接返回
                return sb.toString();
            }
        }

        // 标准格式：类型 + 自增子句 + 主键 + 约束
        sb.append(buildTypeClause());
        if (autoIncrement) {
            sb.append(dialect.autoIncrementClause());
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
        if (comment != null && !comment.isEmpty() && dialect.supportsColumnComment()) {
            sb.append(" COMMENT '").append(escape(comment)).append('\'');
        }
        if (afterColumn != null && !afterColumn.isEmpty() && dialect.supportsAfterColumn()) {
            sb.append(" AFTER ").append(quote(afterColumn));
        }
        return sb.toString();
    }

    /**
     * 生成 ALTER COLUMN 修改片段（不含 {@code ALTER TABLE} 前缀）。
     * <p>
     * <b>注意：</b>自方言重构后，字段修改的完整 SQL 由 {@link Dialect#modifyColumnSql} 生成，
     * 此方法保留以兼容已有调用，等价于 {@link #toSql()}。
     */
    public String toModifyColumnFragment() {
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
     * 按方言构建类型子句，委托给 {@link Dialect#mapType}。
     */
    private String buildTypeClause() {
        return getDialect().mapType(type, length, precision, scale, unsigned);
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

    // ---- 包级可见 getters（供同包 Dialect 实现访问字段细节） ----

    String getType() { return type; }
    Integer getLength() { return length; }
    Integer getPrecision() { return precision; }
    Integer getScale() { return scale; }
    boolean isUnsigned() { return unsigned; }
    boolean isNullable() { return nullable; }
    boolean isAutoIncrement() { return autoIncrement; }
    String getDefaultValue() { return defaultValue; }
    boolean hasDefault() { return hasDefault; }
    String getComment() { return comment; }
    String getAfterColumn() { return afterColumn; }
}
