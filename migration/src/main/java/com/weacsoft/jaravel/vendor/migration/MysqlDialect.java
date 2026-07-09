package com.weacsoft.jaravel.vendor.migration;

/**
 * MySQL 方言实现。
 * <p>
 * 标识符用反引号，自增用 AUTO_INCREMENT，建表追加 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4，
 * 修改字段用 ALTER TABLE MODIFY，支持 COMMENT 和 AFTER。
 */
public class MysqlDialect extends AbstractDialect {

    public MysqlDialect() {
        super("mysql");
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String tableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public String renameTableSql(String from, String to) {
        return "RENAME TABLE " + quote(from) + " TO " + quote(to);
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
    public String modifyColumnSql(String quotedTable, ColumnDefinition column) {
        return "ALTER TABLE " + quotedTable + " MODIFY " + column.toSql();
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
        // MySQL 使用标准格式：TYPE AUTO_INCREMENT PRIMARY KEY
        return null;
    }

    @Override
    public String autoIncrementClause() {
        return " AUTO_INCREMENT";
    }

    @Override
    public String createRepositoryTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
            + "`id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "`migration` VARCHAR(255) NOT NULL, "
            + "`batch` INT NOT NULL"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        return "DROP INDEX `" + indexName + "` ON " + quotedTable;
    }

    @Override
    public boolean supportsColumnComment() {
        return true;
    }

    @Override
    public boolean supportsAfterColumn() {
        return true;
    }
}
