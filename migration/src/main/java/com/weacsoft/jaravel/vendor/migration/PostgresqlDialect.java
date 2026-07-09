package com.weacsoft.jaravel.vendor.migration;

/**
 * PostgreSQL 数据库方言实现。
 * <p>
 * 适配 PostgreSQL 的标识符引用、类型映射、自增列、系统目录查询等特性，供
 * {@link Schema}、{@link Blueprint}、{@link MigrationRepository} 通过 {@link Dialect} 委托调用。
 * <ul>
 *   <li>标识符使用双引号 {@code "name"} 引用；</li>
 *   <li>无 TINYINT / YEAR 类型，分别以 SMALLINT 替代；</li>
 *   <li>自增主键使用 {@code GENERATED ALWAYS AS IDENTITY}（PostgreSQL 10+）；</li>
 *   <li>修改字段使用 {@code ALTER TABLE ... ALTER COLUMN ... TYPE ...}，仅变更类型，
 *       不在同一语句中变更可空性（PostgreSQL 限制）；</li>
 *   <li>JSON 使用 JSONB，二进制使用 BYTEA；</li>
 *   <li>系统目录查询基于 {@code information_schema.tables / columns}；</li>
 *   <li>删除索引无需 ON 子句，使用 {@code DROP INDEX IF EXISTS}；</li>
 *   <li>不支持列注释（COMMENT）与 AFTER 子句。</li>
 * </ul>
 *
 * @see AbstractDialect
 * @see Dialect
 */
public class PostgresqlDialect extends AbstractDialect {

    /**
     * 构造 PostgreSQL 方言实例，方言名称为 {@code "postgresql"}。
     */
    public PostgresqlDialect() {
        super("postgresql");
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
        return "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
    }

    @Override
    public String hasColumnSql() {
        return "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?";
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
        // PostgreSQL ALTER COLUMN TYPE 仅变更类型，不可在同一语句中变更可空性
        return "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quote(column.getName()) + " TYPE "
                + mapType(column.getType(), column.getLength(), column.getPrecision(), column.getScale(),
                        column.isUnsigned());
    }

    @Override
    public String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned) {
        switch (logicalType) {
            case "string":
                return "VARCHAR(" + (length != null ? length : 255) + ")";
            case "char":
                return "CHAR(" + (length != null ? length : 255) + ")";
            case "integer":
                // PostgreSQL 无 UNSIGNED 概念，忽略
                return "INT";
            case "bigInteger":
                return "BIGINT";
            case "tinyInteger":
                // PostgreSQL 无 TINYINT，使用 SMALLINT
                return "SMALLINT";
            case "smallInteger":
                return "SMALLINT";
            case "mediumInteger":
                return "INTEGER";
            case "text":
                return "TEXT";
            case "mediumText":
                return "TEXT";
            case "longText":
                return "TEXT";
            case "boolean":
                return "BOOLEAN";
            case "decimal":
                return "DECIMAL(" + (precision != null ? precision : 8) + "," + (scale != null ? scale : 2) + ")";
            case "float":
                return "REAL";
            case "double":
                return "DOUBLE PRECISION";
            case "date":
                return "DATE";
            case "dateTime":
                return "TIMESTAMP";
            case "time":
                return "TIME";
            case "timestamp":
                return "TIMESTAMP";
            case "year":
                // PostgreSQL 无 YEAR 类型，使用 SMALLINT
                return "SMALLINT";
            case "binary":
                return "BYTEA";
            case "json":
                return "JSONB";
            default:
                return logicalType;
        }
    }

    @Override
    public String autoIncrementPrimaryKeyTypeClause(String logicalType) {
        if ("bigInteger".equals(logicalType)) {
            return "BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
        }
        if ("integer".equals(logicalType)) {
            return "INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
        }
        return mapType(logicalType, null, null, null, false) + " GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
    }

    @Override
    public String autoIncrementClause() {
        return " GENERATED ALWAYS AS IDENTITY";
    }

    @Override
    public String createRepositoryTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + quote(table) + " (" + quote("id") + " SERIAL PRIMARY KEY, "
                + quote("migration") + " VARCHAR(255) NOT NULL, " + quote("batch") + " INT NOT NULL)";
    }

    @Override
    public boolean needsCheckTableExistsBeforeCreateRepository() {
        return false;
    }

    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        // PostgreSQL DROP INDEX 无需 ON 子句
        return "DROP INDEX IF EXISTS " + quote(indexName);
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
