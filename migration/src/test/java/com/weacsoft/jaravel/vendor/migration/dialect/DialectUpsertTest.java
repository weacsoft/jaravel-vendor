package com.weacsoft.jaravel.vendor.migration.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Dialect#upsertSql(String, String[], String)} 各方言实现单元测试。
 * <p>
 * 0.1.3 架构对齐：upsert SQL 统一由 migration 模块方言生成，
 * cache-database 等驱动不再各自内置方言判断。
 */
class DialectUpsertTest {

    private static final String[] COLS = {"`key`", "`value`", "`expires`"};
    private static final String KEY = "`key`";

    @Test
    void mysqlUsesOnDuplicateKeyUpdate() {
        Dialect d = DialectFactory.create("mysql");
        String sql = d.upsertSql("`t`", COLS, KEY);
        assertEquals("INSERT INTO `t` (`key`, `value`, `expires`) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `expires` = VALUES(`expires`)",
                sql);
    }

    @Test
    void postgresqlUsesOnConflict() {
        Dialect d = DialectFactory.create("postgresql");
        String sql = d.upsertSql("\"t\"", new String[]{"\"key\"", "\"value\"", "\"expires\""}, "\"key\"");
        assertTrue(sql.startsWith("INSERT INTO \"t\" (\"key\", \"value\", \"expires\") VALUES (?, ?, ?) "), sql);
        assertTrue(sql.endsWith("ON CONFLICT (\"key\") DO UPDATE SET \"value\" = EXCLUDED.\"value\", \"expires\" = EXCLUDED.\"expires\""), sql);
    }

    @Test
    void sqliteUsesOnConflict() {
        Dialect d = DialectFactory.create("sqlite");
        String sql = d.upsertSql("`t`", COLS, KEY);
        assertTrue(sql.endsWith("ON CONFLICT (`key`) DO UPDATE SET `value` = EXCLUDED.`value`, `expires` = EXCLUDED.`expires`"), sql);
    }

    @Test
    void h2UsesMergeIntoKey() {
        Dialect d = DialectFactory.create("h2");
        String sql = d.upsertSql("`t`", COLS, KEY);
        assertEquals("MERGE INTO `t` (`key`, `value`, `expires`) KEY (`key`) VALUES (?, ?, ?)", sql);
    }

    @Test
    void sqlServerUsesMergeUsingAs() {
        Dialect d = DialectFactory.create("microsoft sql server");
        String sql = d.upsertSql("[t]", new String[]{"[key]", "[value]"}, "[key]");
        assertTrue(sql.startsWith("MERGE [t] AS t USING (SELECT ? AS [key], ? AS [value]) AS s "), sql);
        assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET t.[value] = s.[value]"), sql);
        assertTrue(sql.endsWith("WHEN NOT MATCHED THEN INSERT ([key], [value]) VALUES (s.[key], s.[value])"), sql);
    }

    @Test
    void oracleUsesMergeUsingDual() {
        Dialect d = DialectFactory.create("oracle");
        String sql = d.upsertSql("\"t\"", new String[]{"\"key\"", "\"value\""}, "\"key\"");
        assertTrue(sql.startsWith("MERGE \"t\" AS t USING (SELECT ? \"key\", ? \"value\" FROM DUAL) AS s "), sql);
        assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET t.\"value\" = s.\"value\""), sql);
    }

    @Test
    void unknownDialectFallsBackToMysqlVariantViaDefault() {
        // Dialect 接口的 default 实现（未重写 upsertSql 的新方言）走 MySQL 变体
        Dialect stub = new AbstractDialect("test-dialect") {
            public String quote(String identifier) {
                return identifier;
            }

            public String renameTableSql(String from, String to) {
                throw new UnsupportedOperationException();
            }

            public String hasTableSql() {
                throw new UnsupportedOperationException();
            }

            public String hasColumnSql() {
                throw new UnsupportedOperationException();
            }

            public String modifyColumnSql(String table, com.weacsoft.jaravel.vendor.migration.ColumnDefinition column) {
                throw new UnsupportedOperationException();
            }

            public String mapType(String logicalType, Integer length, Integer precision, Integer scale, boolean unsigned) {
                return logicalType.toUpperCase();
            }

            public String autoIncrementPrimaryKeyTypeClause(String logicalType) {
                return null;
            }

            public String autoIncrementClause() {
                return " AUTO_INCREMENT";
            }

            public String createRepositoryTableSql(String table) {
                throw new UnsupportedOperationException();
            }

            public String dropIndexSql(String indexName, String quotedTable) {
                throw new UnsupportedOperationException();
            }
        };
        String sql = stub.upsertSql("t", new String[]{"key", "value"}, "key");
        assertTrue(sql.endsWith("ON DUPLICATE KEY UPDATE value = VALUES(value)"), sql);
    }

    @Test
    void factoryFallsBackToMysqlForUnknownProduct() {
        Dialect d = DialectFactory.create("exotic-db");
        assertEquals("mysql", d.getName());
        assertTrue(d.upsertSql("`t`", COLS, KEY).contains("ON DUPLICATE KEY UPDATE"),
                "未知数据库产品名应回退 MySQL 方言的 upsert SQL");
    }
}
