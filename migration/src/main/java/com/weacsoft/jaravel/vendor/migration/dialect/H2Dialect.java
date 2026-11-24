package com.weacsoft.jaravel.vendor.migration.dialect;


import com.weacsoft.jaravel.vendor.migration.ColumnDefinition;
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
    public String upsertSql(String quotedTable, String[] quotedColumns, String quotedKeyColumn) {
        return AbstractDialect.upsertH2(quotedTable, quotedColumns, quotedKeyColumn);
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
            // H2 原生类型（不使用 UNSIGNED / MySQL 专属类型）：
            // 保证在 H2 默认模式与 MODE=MySQL 下均可建表
            case "integer":
                return "INT";
            case "bigInteger":
                return "BIGINT";
            case "tinyInteger":
                return "TINYINT";
            case "smallInteger":
                return "SMALLINT";
            case "mediumInteger":
                return "INT";
            case "text":
                return "TEXT";
            case "mediumText":
                return "CLOB";
            case "longText":
                return "CLOB";
            case "boolean":
                return "BOOLEAN";
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
                return "INT";
            case "binary":
                return "BLOB";
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
        // UNIQUE(migration, batch)：同一批次内同一条迁移只记录一次（多节点并发跑迁移时防止重复记录）；
        // 回滚后再次迁移会写入新的 batch，不受此约束影响
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
            + "`id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "`migration` VARCHAR(255) NOT NULL, "
            + "`batch` INT NOT NULL, "
            + "UNIQUE (`migration`, `batch`)"
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
