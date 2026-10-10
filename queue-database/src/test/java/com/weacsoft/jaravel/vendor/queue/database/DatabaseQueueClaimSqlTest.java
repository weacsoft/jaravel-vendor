package com.weacsoft.jaravel.vendor.queue.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 队列抢占 SQL 的<b>方言矩阵</b>单测。
 * <p>
 * 背景：{@code pop} 优先走「{@code SELECT ... FOR UPDATE SKIP LOCKED} + 同一事务内 UPDATE」
 * 的无竞争抢占；但各方言写法不一致（Oracle 无 {@code LIMIT}、SQL Server 无 {@code SKIP LOCKED}
 * 关键字、SQLite 无行级锁）。这些差异无法在当前测试基建里跑真实 Oracle/SQL Server，
 * 因此把「生成的 SQL 形状」用纯函数单测固定下来（专家团验收清单明确要求）。
 * <p>
 * 策略：只有确定支持的方言才生成 SKIP LOCKED 语句，其余返回 {@code null} →
 * 调用方降级到「乐观锁 + 竞争退避重试」（该路径在任何标准 SQL 库上都正确）。
 */
class DatabaseQueueClaimSqlTest {

    @Test
    void mysqlUsesLimitWithSkipLocked() {
        String sql = DatabaseQueueDriver.claimSqlFor("MySQL", "jobs");
        assertTrue(sql.contains("LIMIT 1"), "MySQL 用 LIMIT 1: " + sql);
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"), "MySQL 支持 SKIP LOCKED: " + sql);
        assertTrue(sql.contains("ORDER BY id ASC"), "必须按 id 升序取最早任务: " + sql);
        assertTrue(sql.contains("COALESCE(reserved_at, 0)"), "保留 reserved_at 的空值兼容: " + sql);
    }

    @Test
    void postgresAndH2UseLimitWithSkipLocked() {
        for (String product : new String[]{"PostgreSQL", "H2", "MariaDB"}) {
            String sql = DatabaseQueueDriver.claimSqlFor(product, "jobs");
            assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"),
                    product + " 应使用 SKIP LOCKED: " + sql);
            assertTrue(sql.contains("LIMIT 1"), product + " 应使用 LIMIT 1: " + sql);
        }
    }

    @Test
    void oracleUsesFetchFirstInsteadOfLimit() {
        String sql = DatabaseQueueDriver.claimSqlFor("Oracle", "jobs");
        assertTrue(sql.contains("FETCH FIRST 1 ROWS ONLY"), "Oracle 用 FETCH FIRST: " + sql);
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"), "Oracle 11g+ 支持 SKIP LOCKED: " + sql);
        assertFalse(sql.contains("LIMIT"), "Oracle 不接受 LIMIT: " + sql);
    }

    @Test
    void sqlServerUsesUpdlockReadpastWithTop() {
        String sql = DatabaseQueueDriver.claimSqlFor("Microsoft SQL Server", "jobs");
        assertTrue(sql.contains("TOP 1"), "SQL Server 用 TOP 1: " + sql);
        assertTrue(sql.contains("WITH (UPDLOCK, READPAST)"), "SQL Server 用 UPDLOCK+READPAST: " + sql);
        assertFalse(sql.contains("SKIP LOCKED"), "SQL Server 没有 SKIP LOCKED 关键字: " + sql);
        assertFalse(sql.contains("LIMIT"), "SQL Server 不接受 LIMIT: " + sql);
    }

    @Test
    void sqliteAndUnknownDialectsFallBackToNull() {
        assertNull(DatabaseQueueDriver.claimSqlFor("SQLite", "jobs"), "SQLite 无行级锁 → 乐观锁");
        assertNull(DatabaseQueueDriver.claimSqlFor(null, "jobs"), "产品名缺失 → 乐观锁");
        assertNull(DatabaseQueueDriver.claimSqlFor("", "jobs"), "产品名空串 → 乐观锁");
        assertNull(DatabaseQueueDriver.claimSqlFor("SomeExoticDB", "jobs"), "未知方言 → 乐观锁");
    }

    @Test
    void customTableNameIsHonored() {
        String sql = DatabaseQueueDriver.claimSqlFor("MySQL", "my_jobs");
        assertTrue(sql.contains("FROM my_jobs"), "应使用传入的表名: " + sql);
        assertFalse(sql.contains("FROM jobs "), "不得回落到默认表名: " + sql);
    }
}