package com.weacsoft.jaravel.vendor.database;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JdbcExecutor#inTransaction} 语义测试。
 * <p>
 * 这个原语是队列抢占（{@code FOR UPDATE SKIP LOCKED}）、缓存原子 add/pull、文件元信息+分片
 * 事务化的共同基础：{@code update}/{@code queryMapped} 各自取连接（连接池下可能是不同连接），
 * 只有把它们放进同一个 {@link JdbcExecutor.Tx} 才有跨语句原子性。
 */
class JdbcExecutorTransactionTest {

    private static DataSource dataSource;
    private JdbcExecutor jdbc;

    @BeforeAll
    static void initSchema() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:txdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        new JdbcExecutor(ds).execute("CREATE TABLE IF NOT EXISTS tx_demo ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, v INT NOT NULL)");
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcExecutor(dataSource);
        jdbc.update("DELETE FROM tx_demo");
    }

    private long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM tx_demo", Long.class);
        return n == null ? 0L : n;
    }

    @Test
    void commitsOnSuccess() {
        int inserted = jdbc.inTransaction(tx -> {
            tx.update("INSERT INTO tx_demo (v) VALUES (?)", 1);
            tx.update("INSERT INTO tx_demo (v) VALUES (?)", 2);
            return 2;
        });

        assertEquals(2, inserted);
        assertEquals(2L, count(), "事务成功提交后，外部应能看到两条记录");
    }

    @Test
    void rollsBackOnException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> jdbc.inTransaction(tx -> {
            tx.update("INSERT INTO tx_demo (v) VALUES (?)", 10);
            throw new IllegalStateException("boom");
        }));

        assertEquals("boom", ex.getMessage(), "原始运行时异常应原样抛出（不被包装掩盖）");
        assertEquals(0L, count(), "事务回滚后不得留下任何记录");
    }

    @Test
    void seesItsOwnUncommittedWrites() {
        String seen = jdbc.inTransaction(tx -> {
            tx.update("INSERT INTO tx_demo (v) VALUES (?)", 5);
            // 同一事务内必须能读到自己的未提交写入（否则「先查后改」无从实现）
            Long inTx = tx.queryForObject("SELECT COUNT(*) FROM tx_demo", Long.class);
            return String.valueOf(inTx);
        });

        assertEquals("1", seen);
        assertEquals(1L, count());
    }

    @Test
    void returnsValueFromTransaction() {
        Integer value = jdbc.inTransaction(tx -> 7);
        assertEquals(7, value);
    }

    @Test
    void supportsConditionalUpdatePatternUsedByQueueClaim() {
        jdbc.update("INSERT INTO tx_demo (v) VALUES (?)", 100);

        // 模拟队列抢占：同一事务内「选中候选 + 条件更新」，更新失败则回滚
        Boolean claimed = jdbc.inTransaction(tx -> {
            List<Long> ids = tx.queryForList("SELECT id FROM tx_demo WHERE v = ?", Long.class, 100);
            if (ids.isEmpty()) {
                return false;
            }
            int updated = tx.update("UPDATE tx_demo SET v = ? WHERE id = ? AND v = ?", 200, ids.get(0), 100);
            if (updated == 0) {
                return false;
            }
            return true;
        });

        assertEquals(Boolean.TRUE, claimed);
        assertEquals(200, jdbc.queryForObject("SELECT v FROM tx_demo", Integer.class));
    }

    @Test
    void exposesRawConnectionForDialectSpecificSql() {
        Connection conn = jdbc.inTransaction(JdbcExecutor.Tx::connection);
        assertNotNull(conn, "应能拿到事务内的底层连接（供 FOR UPDATE SKIP LOCKED 等方言语句使用）");
    }

    @Test
    void executorStillUsableAfterTransaction() {
        jdbc.inTransaction(tx -> {
            tx.update("INSERT INTO tx_demo (v) VALUES (?)", 1);
            return null;
        });

        // 事务结束后连接应已归还/恢复：普通方法照常可用，且能再开一个事务
        assertEquals(1L, count());
        assertNull(jdbc.queryForObject("SELECT v FROM tx_demo WHERE v = ?", Integer.class, 999));
        jdbc.inTransaction(tx -> {
            tx.update("DELETE FROM tx_demo");
            return null;
        });
        assertEquals(0L, count());
    }
}