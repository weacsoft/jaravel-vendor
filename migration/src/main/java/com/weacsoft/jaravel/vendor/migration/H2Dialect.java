package com.weacsoft.jaravel.vendor.migration;

/**
 * H2 数据库方言实现。
 * <p>
 * 标识符用反引号，自增用 AUTO_INCREMENT（标准格式），建表无附加选项，
 * 通过 INFORMATION_SCHEMA 查询表和列是否存在，修改字段用 ALTER TABLE ALTER COLUMN。
 * 不支持列注释和 AFTER 子句。
 */
public class H2Dialect extends AbstractDialect {

    public H2Dialect() {
        super("h2");
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier + "`";
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
        return "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = UPPER(?)";
    }

    @Override
    public String hasColumnSql() {
        return "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = UPPER(?) AND COLUMN_NAME = UPPER(?)";
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
        return "ALTER TABLE " + quotedTable + " ALTER COLUMN " + column.toSql();
    }

    @Override
    public String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned) {
        switch (logicalType) {
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
                return logicalType;
        }
    }

    @Override
    public String autoIncrementPrimaryKeyTypeClause(String logicalType) {
        // H2 使用标准格式：TYPE AUTO_INCREMENT PRIMARY KEY
        return null;
    }

    @Override
    public String autoIncrementClause() {
        return " AUTO_INCREMENT";
    }

    @Override
    public String createRepositoryTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
            + "`id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "`migration` VARCHAR(255) NOT NULL, "
            + "`batch` INT NOT NULL"
            + ")";
    }

    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        return "DROP INDEX `" + indexName + "` ON " + quotedTable;
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
