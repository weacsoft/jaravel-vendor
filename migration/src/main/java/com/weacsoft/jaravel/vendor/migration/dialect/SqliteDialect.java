package com.weacsoft.jaravel.vendor.migration.dialect;


import com.weacsoft.jaravel.vendor.migration.ColumnDefinition;
/**
 * SQLite 方言实现。
 * <p>
 * 标识符用反引号，自增主键使用 {@code INTEGER PRIMARY KEY AUTOINCREMENT} 特殊格式，
 * 列信息通过 {@code PRAGMA table_info} 读取，修改字段需通过重建表实现（不支持 ALTER COLUMN MODIFY）。
 * 建表无附加选项，不支持列注释和 AFTER 子句。
 */
public class SqliteDialect extends AbstractDialect {

    public SqliteDialect() {
        super("sqlite");
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
        return "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
    }

    @Override
    public String hasColumnSql() {
        // SQLite 使用 PRAGMA table_info 读取列信息，无需 SQL 模板
        return null;
    }

    @Override
    public boolean usesPragmaForColumnInfo() {
        return true;
    }

    @Override
    public String pragmaTableInfoSql(String quotedTable) {
        return "PRAGMA table_info(" + quotedTable + ")";
    }

    @Override
    public boolean supportsModifyColumn() {
        return false;
    }

    @Override
    public boolean needsTableRecreationForModify() {
        return true;
    }

    @Override
    public String modifyColumnSql(String quotedTable, ColumnDefinition column) {
        throw new UnsupportedOperationException("SQLite uses table recreation for modify");
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
        // SQLite 自增主键必须使用 INTEGER PRIMARY KEY AUTOINCREMENT，类型固定为 INTEGER
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    public String autoIncrementClause() {
        return " AUTOINCREMENT";
    }

    @Override
    public String createRepositoryTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "`migration` VARCHAR(255) NOT NULL, "
            + "`batch` INT NOT NULL"
            + ")";
    }

    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        return "DROP INDEX IF EXISTS `" + indexName + "`";
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
