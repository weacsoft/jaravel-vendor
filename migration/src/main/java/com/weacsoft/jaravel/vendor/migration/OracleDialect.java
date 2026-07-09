package com.weacsoft.jaravel.vendor.migration;

/**
 * Oracle 数据库方言实现。
 * <p>
 * 适配 Oracle 的标识符引用、类型映射、自增列、系统目录查询等特性，供
 * {@link Schema}、{@link Blueprint}、{@link MigrationRepository} 通过 {@link Dialect} 委托调用。
 * <ul>
 *   <li>标识符使用双引号 {@code "name"} 引用；</li>
 *   <li>数值类型统一使用 {@code NUMBER(p)} / {@code NUMBER(p,s)}，无专用 TINYINT/SMALLINT 等；</li>
 *   <li>文本大对象使用 CLOB，二进制使用 BLOB，JSON 以 CLOB 存储（兼容性更佳）；</li>
 *   <li>自增主键使用 {@code GENERATED ALWAYS AS IDENTITY}（Oracle 12c+）；</li>
 *   <li>修改字段使用 {@code ALTER TABLE ... MODIFY (...)}，支持类型、可空性与默认值；</li>
 *   <li>系统目录查询基于 {@code user_tables / user_tab_columns}，并对名称做 UPPER 处理；</li>
 *   <li>不支持 CREATE TABLE IF NOT EXISTS，建迁移记录表前需先检查表是否存在；</li>
 *   <li>删除索引无需 ON 子句，使用 {@code DROP INDEX}；</li>
 *   <li>不支持列注释（COMMENT）与 AFTER 子句。</li>
 * </ul>
 *
 * @see AbstractDialect
 * @see Dialect
 */
public class OracleDialect extends AbstractDialect {

    /**
     * 构造 Oracle 方言实例，方言名称为 {@code "oracle"}。
     */
    public OracleDialect() {
        super("oracle");
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String tableOptions() {
        return "";
    }

    @Override
    public String renameTableSql(String from, String to) {
        return "ALTER TABLE " + quote(from) + " RENAME TO " + quote(to);
    }

    @Override
    public String hasTableSql() {
        return "SELECT COUNT(*) FROM user_tables WHERE table_name = UPPER(?)";
    }

    @Override
    public String hasColumnSql() {
        return "SELECT COUNT(*) FROM user_tab_columns WHERE table_name = UPPER(?) AND column_name = UPPER(?)";
    }

    @Override
    public boolean supportsModifyColumn() {
        return true;
    }

    @Override
    public boolean needsTableRecreationForModify() {
        return false;
    }

    @Override
    public String modifyColumnSql(String quotedTable, ColumnDefinition column) {
        String fragment = quote(column.getName()) + " "
                + mapType(column.getType(), column.getLength(), column.getPrecision(), column.getScale(),
                        column.isUnsigned())
                + (column.isNullable() ? " NULL" : " NOT NULL")
                + buildDefaultClause(column.getDefaultValue(), column.hasDefault());
        return "ALTER TABLE " + quotedTable + " MODIFY (" + fragment + ")";
    }

    @Override
    public String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned) {
        switch (logicalType) {
            case "string":
                return "VARCHAR2(" + (length != null ? length : 255) + ")";
            case "char":
                return "CHAR(" + (length != null ? length : 255) + ")";
            case "integer":
                // Oracle 无 UNSIGNED 概念，忽略；NUMBER(10) 可容纳标准 32 位整型范围
                return "NUMBER(10)";
            case "bigInteger":
                return "NUMBER(19)";
            case "tinyInteger":
                return "NUMBER(3)";
            case "smallInteger":
                return "NUMBER(5)";
            case "mediumInteger":
                return "NUMBER(7)";
            case "text":
                return "CLOB";
            case "mediumText":
                return "CLOB";
            case "longText":
                return "CLOB";
            case "boolean":
                return "NUMBER(1)";
            case "decimal":
                // Oracle 使用 NUMBER(p,s) 而非 DECIMAL
                return "NUMBER(" + (precision != null ? precision : 8) + "," + (scale != null ? scale : 2) + ")";
            case "float":
                return "BINARY_FLOAT";
            case "double":
                return "BINARY_DOUBLE";
            case "date":
                return "DATE";
            case "dateTime":
                return "TIMESTAMP";
            case "time":
                return "TIMESTAMP";
            case "timestamp":
                return "TIMESTAMP";
            case "year":
                return "NUMBER(4)";
            case "binary":
                return "BLOB";
            case "json":
                // Oracle 12c 原生支持 JSON，但 CLOB 兼容性更佳
                return "CLOB";
            default:
                return logicalType;
        }
    }

    @Override
    public String autoIncrementPrimaryKeyTypeClause(String logicalType) {
        // Oracle 12c+ 支持 GENERATED ALWAYS AS IDENTITY
        return "NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
    }

    @Override
    public String autoIncrementClause() {
        return " GENERATED ALWAYS AS IDENTITY";
    }

    @Override
    public String createRepositoryTableSql(String table) {
        // Oracle 不支持 IF NOT EXISTS，由 needsCheckTableExistsBeforeCreateRepository() 控制先检查
        return "CREATE TABLE " + quote(table) + " (" + quote("id")
                + " NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " + quote("migration")
                + " VARCHAR2(255) NOT NULL, " + quote("batch") + " NUMBER(10) NOT NULL)";
    }

    @Override
    public boolean needsCheckTableExistsBeforeCreateRepository() {
        return true;
    }

    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        // Oracle DROP INDEX 无需 ON 子句
        return "DROP INDEX " + quote(indexName);
    }

    @Override
    public boolean supportsColumnComment() {
        return false;
    }

    @Override
    public boolean supportsAfterColumn() {
        return false;
    }
}
