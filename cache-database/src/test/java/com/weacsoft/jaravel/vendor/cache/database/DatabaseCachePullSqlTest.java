package com.weacsoft.jaravel.vendor.cache.database;

import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code pullValue} 取走查询的<b>方言矩阵</b>单测。
 * <p>
 * 背景：一次性令牌（pull / 原子取走）依赖同一事务内「带行锁的 SELECT + DELETE」，
 * 但各方言的行锁写法不一致：MySQL / PostgreSQL / H2 / Oracle 用句尾 {@code FOR UPDATE}，
 * SQL Server 没有 {@code FOR UPDATE}（用表提示 {@code WITH (UPDLOCK)}），SQLite 无行级锁。
 * 这些差异无法在当前测试基建里跑真实 Oracle / SQL Server，
 * 因此把「生成的 SQL 形状」用纯函数单测固定下来。
 */
class DatabaseCachePullSqlTest {

    private static final String T = "`t`";
    private static final String V = "`v`";
    private static final String E = "`e`";
    private static final String K = "`k`";

    private static String pull(Dialect d) {
        return DatabaseCacheDriver.pullSelectSql(T, V, E, K, d);
    }

    @Test
    void mysqlMariaPostgresH2UseTrailingForUpdate() {
        for (String product : new String[]{"mysql", "mariadb", "postgresql", "h2"}) {
            String sql = pull(DialectFactory.create(product));
            assertTrue(sql.endsWith(" FOR UPDATE"), product + " 句尾 FOR UPDATE: " + sql);
            assertTrue(sql.contains("FROM " + T), product + " 表名: " + sql);
            assertFalse(sql.contains("WITH ("), product + " 不应带表提示: " + sql);
        }
    }

    @Test
    void oracleUsesTrailingForUpdate() {
        String sql = pull(DialectFactory.create("oracle"));
        assertTrue(sql.endsWith(" FOR UPDATE"), "Oracle 11g+ 支持 FOR UPDATE: " + sql);
    }

    @Test
    void sqlServerUsesUpdlockTableHintNotForUpdate() {
        String sql = pull(DialectFactory.create("microsoft sql server"));
        assertTrue(sql.contains(" FROM " + T + " WITH (UPDLOCK)"), "SQL Server 用 UPDLOCK 表提示: " + sql);
        assertFalse(sql.contains("FOR UPDATE"), "SQL Server 没有 FOR UPDATE: " + sql);
    }

    @Test
    void sqliteAndUnknownUseNoLock() {
        String sqlite = pull(DialectFactory.create("sqlite"));
        assertFalse(sqlite.contains("FOR UPDATE"), "SQLite 无行级锁: " + sqlite);
        assertFalse(sqlite.contains("WITH ("), "SQLite 不应带表提示: " + sqlite);
        String unknown = pull(null);
        assertFalse(unknown.contains("FOR UPDATE"), "未知方言无行级锁: " + unknown);
        assertFalse(unknown.contains("WITH ("), "未知方言不应带表提示: " + unknown);
    }

    @Test
    void selectShapeIsStable() {
        String sql = pull(DialectFactory.create("mysql"));
        assertTrue(sql.startsWith("SELECT " + V + ", " + E + " FROM "), "固定列序: " + sql);
        assertTrue(sql.contains(" WHERE " + K + " = ?"), "键等值过滤: " + sql);
    }
}
