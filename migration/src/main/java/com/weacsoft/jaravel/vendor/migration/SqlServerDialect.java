package com.weacsoft.jaravel.vendor.migration;

/**
 * SQL Server 方言实现。
 * <p>
 * 封装 Microsoft SQL Server 与其他数据库在 DDL 生成上的差异，主要包括：
 * <ul>
 *   <li>标识符引用使用方括号 {@code [name]}；</li>
 *   <li>类型映射（如 {@code VARCHAR(MAX)}、{@code BIT}、{@code DATETIME2}、{@code NVARCHAR(MAX)} 等），
 *       忽略 UNSIGNED，无 MEDIUMINT/YEAR 等类型时用近似类型替代；</li>
 *   <li>自增主键使用 {@code IDENTITY(1,1)}；</li>
 *   <li>表是否存在查询走 {@code sys.tables}，列是否存在走 {@code sys.columns} 关联 {@code sys.tables}；</li>
 *   <li>重命名表使用 {@code sp_rename} 存储过程；</li>
 *   <li>{@code ALTER TABLE ALTER COLUMN} 仅支持类型与可空性，不含 IDENTITY / PRIMARY KEY / DEFAULT / COMMENT；</li>
 *   <li>不支持 {@code CREATE TABLE IF NOT EXISTS}，创建迁移记录表前需先检查；</li>
 *   <li>不支持列注释（COMMENT）与 AFTER 子句。</li>
 * </ul>
 * 内部使用，上层通过 {@link DialectFactory} 自动检测并返回实例。
 *
 * @see Dialect
 * @see AbstractDialect
 */
public class SqlServerDialect extends AbstractDialect {

    /**
     * 构造 SQL Server 方言实例，方言名称为 {@code "microsoft sql server"}，
     * 对应 {@code Connection.getMetaData().getDatabaseProductName()} 的小写形式。
     */
    public SqlServerDialect() {
        super("microsoft sql server");
    }

    /**
     * 使用方括号对标识符加引号，如 {@code name} -> {@code [name]}。
     *
     * @param identifier 标识符（表名、列名、索引名等）
     * @return 加方括号后的标识符
     */
    @Override
    public String quote(String identifier) {
        return "[" + identifier + "]";
    }

    /**
     * SQL Server 不支持 CREATE TABLE 的附加选项（如 MySQL 的 ENGINE/CHARSET），返回空字符串。
     *
     * @return 空字符串
     */
    @Override
    public String tableOptions() {
        return "";
    }

    /**
     * 生成重命名表的 SQL，使用 {@code sp_rename} 存储过程。
     * <p>
     * 形如：{@code sp_rename 'old_table', 'new_table'}
     *
     * @param from 原表名（未加引号）
     * @param to   新表名（未加引号）
     * @return 完整的 RENAME SQL
     */
    @Override
    public String renameTableSql(String from, String to) {
        return "sp_rename '" + from + "', '" + to + "'";
    }

    /**
     * 返回判断表是否存在的 SQL 模板（含 {@code ?} 占位符用于表名），查询 {@code sys.tables}，
     * 结果为 COUNT(*)。
     * <p>
     * 形如：{@code SELECT COUNT(*) FROM sys.tables WHERE name = ?}
     *
     * @return SQL 模板
     */
    @Override
    public String hasTableSql() {
        return "SELECT COUNT(*) FROM sys.tables WHERE name = ?";
    }

    /**
     * 返回判断列是否存在的 SQL 模板（含两个 {@code ?} 占位符：表名、列名），
     * 通过关联 {@code sys.columns} 与 {@code sys.tables} 查询，结果为 COUNT(*)。
     * <p>
     * 形如：{@code SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id WHERE t.name = ? AND c.name = ?}
     *
     * @return SQL 模板
     */
    @Override
    public String hasColumnSql() {
        return "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id WHERE t.name = ? AND c.name = ?";
    }

    /**
     * SQL Server 支持 {@code ALTER TABLE ALTER COLUMN} 语法直接修改字段。
     *
     * @return true
     */
    @Override
    public boolean supportsModifyColumn() {
        return true;
    }

    /**
     * SQL Server 无需通过重建表来修改字段。
     *
     * @return false
     */
    @Override
    public boolean needsTableRecreationForModify() {
        return false;
    }

    /**
     * 生成修改字段的完整 ALTER TABLE SQL。
     * <p>
     * SQL Server 的 {@code ALTER COLUMN} 仅支持修改类型与可空性，不能包含 IDENTITY、
     * PRIMARY KEY、DEFAULT、COMMENT 等子句，故片段格式为：
     * {@code [name] <type> [NULL|NOT NULL]}。
     * <p>
     * 完整 SQL 形如：{@code ALTER TABLE [table] ALTER COLUMN [name] VARCHAR(255) NOT NULL}
     *
     * @param quotedTable 已加引号的表名
     * @param column      列定义（isChange() = true）
     * @return 完整的 ALTER TABLE SQL
     */
    @Override
    public String modifyColumnSql(String quotedTable, ColumnDefinition column) {
        String fragment = quote(column.getName()) + " "
                + mapType(column.getType(), column.getLength(), column.getPrecision(),
                        column.getScale(), column.isUnsigned())
                + (column.isNullable() ? " NULL" : " NOT NULL");
        return "ALTER TABLE " + quotedTable + " ALTER COLUMN " + fragment;
    }

    /**
     * 将逻辑类型映射为 SQL Server 特定的类型字符串（不含自增和主键）。
     * <p>
     * 映射规则：
     * <ul>
     *   <li>{@code string} -> {@code VARCHAR(length 或 255)}</li>
     *   <li>{@code char} -> {@code CHAR(length 或 255)}</li>
     *   <li>{@code integer} -> {@code INT}（忽略 unsigned）</li>
     *   <li>{@code bigInteger} -> {@code BIGINT}</li>
     *   <li>{@code tinyInteger} -> {@code TINYINT}</li>
     *   <li>{@code smallInteger} -> {@code SMALLINT}</li>
     *   <li>{@code mediumInteger} -> {@code INT}（SQL Server 无 MEDIUMINT）</li>
     *   <li>{@code text}/{@code mediumText}/{@code longText} -> {@code VARCHAR(MAX)}</li>
     *   <li>{@code boolean} -> {@code BIT}</li>
     *   <li>{@code decimal} -> {@code DECIMAL(precision 或 8, scale 或 2)}</li>
     *   <li>{@code float} -> {@code FLOAT(24)}</li>
     *   <li>{@code double} -> {@code FLOAT(53)}（SQL Server 无 DOUBLE）</li>
     *   <li>{@code date} -> {@code DATE}</li>
     *   <li>{@code dateTime} -> {@code DATETIME2}</li>
     *   <li>{@code time} -> {@code TIME}</li>
     *   <li>{@code timestamp} -> {@code DATETIME2}（SQL Server 的 TIMESTAMP 实为 rowversion，语义不同）</li>
     *   <li>{@code year} -> {@code SMALLINT}（SQL Server 无 YEAR 类型）</li>
     *   <li>{@code binary} -> {@code VARBINARY(MAX)}</li>
     *   <li>{@code json} -> {@code NVARCHAR(MAX)}（SQL Server 无原生 JSON 类型）</li>
     *   <li>其他 -> 原样返回 logicalType</li>
     * </ul>
     *
     * @param logicalType 逻辑类型名
     * @param length      长度（string/char 类型用）
     * @param precision   精度（decimal 类型用）
     * @param scale       标度（decimal 类型用）
     * @param unsigned    是否无符号（SQL Server 不支持，忽略）
     * @return SQL Server 特定的类型字符串
     */
    @Override
    public String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned) {
        switch (logicalType) {
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
                return logicalType;
        }
    }

    /**
     * 返回自增主键的类型+约束子句。
     * <p>
     * SQL Server 使用标准格式 {@code TYPE IDENTITY(1,1) PRIMARY KEY}，由
     * {@link ColumnDefinition#toSql()} 走标准拼接逻辑，故返回 {@code null}。
     *
     * @param logicalType 逻辑类型名（如 {@code "bigInteger"}、{@code "integer"}）
     * @return {@code null} 表示使用标准格式
     */
    @Override
    public String autoIncrementPrimaryKeyTypeClause(String logicalType) {
        return null;
    }

    /**
     * 返回 SQL Server 自增关键字（含前导空格）：{@code IDENTITY(1,1)}。
     * <p>
     * 用于非主键自增或标准拼接场景。
     *
     * @return {@code " IDENTITY(1,1)"}
     */
    @Override
    public String autoIncrementClause() {
        return " IDENTITY(1,1)";
    }

    /**
     * 生成创建迁移记录表的 SQL。
     * <p>
     * SQL Server 不支持 {@code CREATE TABLE IF NOT EXISTS}，故直接生成 CREATE TABLE 语句，
     * 由调用方在创建前通过 {@link #needsCheckTableExistsBeforeCreateRepository()} 先检查表是否存在。
     * <p>
     * 形如：{@code CREATE TABLE [table] ([id] INT IDENTITY(1,1) PRIMARY KEY, [migration] VARCHAR(255) NOT NULL, [batch] INT NOT NULL)}
     *
     * @param table 迁移记录表名
     * @return CREATE TABLE SQL
     */
    @Override
    public String createRepositoryTableSql(String table) {
        return "CREATE TABLE [" + table + "] ([id] INT IDENTITY(1,1) PRIMARY KEY, [migration] VARCHAR(255) NOT NULL, [batch] INT NOT NULL)";
    }

    /**
     * SQL Server 不支持 {@code CREATE TABLE IF NOT EXISTS}，创建迁移记录表前需先检查表是否存在。
     *
     * @return true
     */
    @Override
    public boolean needsCheckTableExistsBeforeCreateRepository() {
        return true;
    }

    /**
     * 生成删除索引的 SQL。
     * <p>
     * SQL Server 的 DROP INDEX 语法需指定表名：{@code DROP INDEX [indexName] ON [table]}。
     *
     * @param indexName   索引名（未加引号）
     * @param quotedTable 已加引号的表名
     * @return DROP INDEX SQL
     */
    @Override
    public String dropIndexSql(String indexName, String quotedTable) {
        return "DROP INDEX [" + indexName + "] ON " + quotedTable;
    }

    /**
     * SQL Server 不支持列注释（COMMENT '...'）。
     *
     * @return false
     */
    @Override
    public boolean supportsColumnComment() {
        return false;
    }

    /**
     * SQL Server 不支持 AFTER 子句（ALTER TABLE 时将字段置于指定字段之后）。
     *
     * @return false
     */
    @Override
    public boolean supportsAfterColumn() {
        return false;
    }
}
