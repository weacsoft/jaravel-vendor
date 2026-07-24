package com.weacsoft.jaravel.vendor.migration;

/**
 * 从迁移文件解析出的列定义。
 * <p>
 * 由 {@link MigrationFileParser} 从 {@link ColumnDefinition} 转换而来，
 * 保存列名、迁移类型名称（如 {@code "bigInteger"}、{@code "string"}）及修饰符信息，
 * 供 {@link ReverseModelGenerator} 映射为 Java 类型并生成 Model 字段。
 * <p>
 * 迁移类型名称与 {@link Blueprint} 的字段方法一一对应：
 * <ul>
 *   <li>{@code id()} → {@code "bigInteger"} + primary + autoIncrement</li>
 *   <li>{@code string("col")} → {@code "string"}</li>
 *   <li>{@code text("col")} → {@code "text"}</li>
 *   <li>{@code integer("col")} → {@code "integer"}</li>
 *   <li>{@code booleanColumn("col")} → {@code "boolean"}</li>
 *   <li>{@code decimal("col")} → {@code "decimal"}</li>
 *   <li>{@code timestamp("col")} → {@code "timestamp"}</li>
 *   <li>{@code json("col")} → {@code "json"}</li>
 *   <li>... 等</li>
 * </ul>
 *
 * @see ParsedTable
 * @see ColumnDefinition
 * @see Blueprint
 */
public class ParsedColumn {

    private final String name;
    private final String migrationType;
    private final boolean nullable;
    private final boolean primary;
    private final boolean unique;
    private final boolean autoIncrement;
    private final boolean unsigned;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;
    private final String comment;

    /**
     * 创建解析列定义。
     *
     * @param name          列名
     * @param migrationType 迁移类型名称（如 {@code "bigInteger"}、{@code "string"}）
     * @param nullable      是否可空
     * @param primary       是否主键
     * @param unique        是否唯一索引
     * @param autoIncrement 是否自增
     * @param unsigned      是否无符号
     * @param length        长度（VARCHAR 等），可为 null
     * @param precision     精度（DECIMAL 等），可为 null
     * @param scale         标度（DECIMAL 等），可为 null
     * @param comment       列注释，可为 null
     */
    public ParsedColumn(String name, String migrationType, boolean nullable, boolean primary,
                        boolean unique, boolean autoIncrement, boolean unsigned,
                        Integer length, Integer precision, Integer scale, String comment) {
        this.name = name;
        this.migrationType = migrationType;
        this.nullable = nullable;
        this.primary = primary;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
        this.unsigned = unsigned;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.comment = comment;
    }

    /**
     * 从 {@link ColumnDefinition} 创建 {@link ParsedColumn}。
     *
     * @param col ColumnDefinition 对象
     * @return ParsedColumn 对象
     */
    public static ParsedColumn from(ColumnDefinition col) {
        return new ParsedColumn(
            col.getName(),
            col.getType(),
            col.isNullable(),
            col.isPrimary(),
            col.isUnique(),
            col.isAutoIncrement(),
            col.isUnsigned(),
            col.getLength(),
            col.getPrecision(),
            col.getScale(),
            col.getComment()
        );
    }

    public String getName() { return name; }
    public String getMigrationType() { return migrationType; }
    public boolean isNullable() { return nullable; }
    public boolean isPrimary() { return primary; }
    public boolean isUnique() { return unique; }
    public boolean isAutoIncrement() { return autoIncrement; }
    public boolean isUnsigned() { return unsigned; }
    public Integer getLength() { return length; }
    public Integer getPrecision() { return precision; }
    public Integer getScale() { return scale; }
    public String getComment() { return comment; }

    @Override
    public String toString() {
        return "ParsedColumn{" +
            "name='" + name + '\'' +
            ", type='" + migrationType + '\'' +
            ", nullable=" + nullable +
            ", primary=" + primary +
            ", unique=" + unique +
            ", autoIncrement=" + autoIncrement +
            '}';
    }
}
