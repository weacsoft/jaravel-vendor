package com.weacsoft.jaravel.vendor.migration;

/**
 * 数据库方言接口，封装各数据库之间的 SQL DDL 差异。
 * <p>
 * 所有方言相关逻辑（标识符引用、类型映射、自增语法、系统目录查询、ALTER TABLE 语法等）
 * 集中在此接口的实现类中，{@link Schema}、{@link Blueprint}、{@link ColumnDefinition}、
 * {@link MigrationRepository} 通过委托 {@link Dialect} 完成方言适配，上层 API 无需感知具体方言。
 * <p>
 * 通过 {@link DialectFactory#detect(javax.sql.DataSource)} 自动检测数据库产品名并返回对应实现。
 * <p>
 * 内部使用，上层无需直接调用。
 *
 * @see DialectFactory
 * @see AbstractDialect
 */
public interface Dialect {

    // ==================== 身份与标识符 ====================

    /**
     * 方言名称（小写），对应 {@code Connection.getMetaData().getDatabaseProductName()} 的小写形式。
     * 如 {@code "mysql"}、{@code "sqlite"}、{@code "h2"}、{@code "microsoft sql server"}、
     * {@code "postgresql"}、{@code "oracle"}。
     *
     * @return 方言名称
     */
    String getName();

    /**
     * 按方言对标识符（表名、列名、索引名等）加引号。
     * <ul>
     *   <li>MySQL / SQLite / H2：反引号 {@code `name`}</li>
     *   <li>SQL Server：方括号 {@code [name]}</li>
     *   <li>PostgreSQL / Oracle：双引号 {@code "name"}</li>
     * </ul>
     *
     * @param identifier 标识符
     * @return 加引号后的标识符
     */
    String quote(String identifier);

    // ==================== 建表 ====================

    /**
     * 返回 CREATE TABLE 的附加选项（如 MySQL 的 {@code ENGINE=InnoDB DEFAULT CHARSET=utf8mb4}）。
     * 不支持附加选项的方言返回空字符串。
     *
     * @return 建表附加选项
     */
    String tableOptions();

    // ==================== 表操作 ====================

    /**
     * 生成重命名表的 SQL。
     *
     * @param from 原表名（未加引号）
     * @param to   新表名（未加引号）
     * @return 完整的 RENAME SQL
     */
    String renameTableSql(String from, String to);

    /**
     * 返回判断表是否存在的 SQL 模板（含 {@code ?} 占位符用于表名），查询结果为 COUNT(*)。
     * <p>
     * 如 MySQL：{@code SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?}
     *
     * @return SQL 模板
     */
    String hasTableSql();

    /**
     * 返回判断列是否存在的 SQL 模板（含两个 {@code ?} 占位符：表名、列名），查询结果为 COUNT(*)。
     * 若方言使用 PRAGMA 读取列信息（如 SQLite），返回 {@code null}。
     *
     * @return SQL 模板，或 {@code null} 表示使用 PRAGMA
     */
    String hasColumnSql();

    /**
     * 是否使用 PRAGMA 读取列信息（仅 SQLite 为 true）。
     *
     * @return true 表示使用 PRAGMA
     */
    boolean usesPragmaForColumnInfo();

    /**
     * 返回 PRAGMA table_info 的 SQL（仅当 {@link #usesPragmaForColumnInfo()} 为 true 时调用）。
     *
     * @param quotedTable 已加引号的表名
     * @return PRAGMA SQL
     */
    String pragmaTableInfoSql(String quotedTable);

    // ==================== 字段修改 ====================

    /**
     * 是否支持 ALTER TABLE MODIFY / ALTER COLUMN 语法直接修改字段。
     *
     * @return true 表示支持
     */
    boolean supportsModifyColumn();

    /**
     * 是否需要通过重建表来修改字段（仅 SQLite 为 true）。
     *
     * @return true 表示需要重建表
     */
    boolean needsTableRecreationForModify();

    /**
     * 生成修改字段的完整 ALTER TABLE SQL。
     *
     * @param quotedTable 已加引号的表名
     * @param column      列定义（isChange() = true）
     * @return 完整的 ALTER TABLE SQL
     */
    String modifyColumnSql(String quotedTable, ColumnDefinition column);

    // ==================== 类型映射 ====================

    /**
     * 将逻辑类型映射为数据库特定的类型字符串（不含自增和主键）。
     *
     * @param logicalType 逻辑类型名，如 {@code "string"}、{@code "integer"}、{@code "bigInteger"} 等
     * @param length      长度（string/char 类型用）
     * @param precision   精度（decimal 类型用）
     * @param scale       标度（decimal 类型用）
     * @param unsigned    是否无符号
     * @return 数据库特定的类型字符串
     */
    String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned);

    // ==================== 自增主键 ====================

    /**
     * 返回自增主键的类型+约束子句。
     * <p>
     * 对于有特殊格式的方言（如 SQLite 的 {@code INTEGER PRIMARY KEY AUTOINCREMENT}、
     * PostgreSQL 的 {@code BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY}），
     * 返回完整子句；对于使用标准格式的方言（MySQL/H2 的 AUTO_INCREMENT、SQL Server 的 IDENTITY），
     * 返回 {@code null}，由 {@link ColumnDefinition#toSql()} 走标准拼接逻辑。
     *
     * @param logicalType 逻辑类型名（如 {@code "bigInteger"}、{@code "integer"}）
     * @return 完整子句，或 {@code null} 表示使用标准格式
     */
    String autoIncrementPrimaryKeyTypeClause(String logicalType);

    /**
     * 返回自增关键字（用于非主键自增或标准拼接场景）。
     * <p>
     * 如 MySQL/H2 返回 {@code " AUTO_INCREMENT"}，SQL Server 返回 {@code " IDENTITY(1,1)"}，
     * PostgreSQL/Oracle 返回 {@code " GENERATED ALWAYS AS IDENTITY"}，
     * SQLite 返回 {@code " AUTOINCREMENT"}（仅非主键自增时使用，较少见）。
     *
     * @return 自增关键字（含前导空格）
     */
    String autoIncrementClause();

    // ==================== 迁移记录表 ====================

    /**
     * 生成创建迁移记录表的 SQL（含 IF NOT EXISTS 或先检查逻辑）。
     *
     * @param table 迁移记录表名
     * @return CREATE TABLE SQL
     */
    String createRepositoryTableSql(String table);

    /**
     * 创建迁移记录表前是否需要先检查表是否存在（SQL Server 和 Oracle 不支持 IF NOT EXISTS）。
     *
     * @return true 表示需要先检查
     */
    boolean needsCheckTableExistsBeforeCreateRepository();

    // ==================== 索引 ====================

    /**
     * 生成删除索引的 SQL。
     *
     * @param indexName    索引名（未加引号）
     * @param quotedTable  已加引号的表名
     * @return DROP INDEX SQL
     */
    String dropIndexSql(String indexName, String quotedTable);

    // ==================== 特性支持 ====================

    /**
     * 是否支持列注释（COMMENT '...'）。
     *
     * @return true 表示支持
     */
    boolean supportsColumnComment();

    /**
     * 是否支持 AFTER 子句（ALTER TABLE 时将字段置于指定字段之后，仅 MySQL）。
     *
     * @return true 表示支持
     */
    boolean supportsAfterColumn();
}
