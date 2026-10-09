package com.weacsoft.jaravel.vendor.cache.database;

import com.weacsoft.jaravel.vendor.database.JdbcExecutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatabaseCacheDriver} 的<b>原子原语</b>回归测试（依赖 {@code JdbcExecutor.inTransaction}）。
 * <p>
 * 这些语义在改动前都是「两步实现」，并发下会静默失效：
 * <ul>
 *   <li>{@code add} 是 has→put：并发可双双成功（互斥门闩失效）；</li>
 *   <li>{@code pull} 是 get→forget：一次性令牌可被两个线程同时取到；</li>
 *   <li>{@code increment} 是读改写：丢失更新；</li>
 * </ul>
 * 现在数据库驱动通过「同一事务 + 主键唯一约束 / {@code SELECT ... FOR UPDATE}」提供真原子语义。
 */
class DatabaseCacheAtomicityTest {

    private static DataSource dataSource;
    private static JdbcExecutor jdbc;
    private DatabaseCacheDriver driver;

    @BeforeAll
    static void initDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cacheatomic;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        jdbc = new JdbcExecutor(ds);
    }

    @BeforeEach
    void setUp() {
        driver = new DatabaseCacheDriver(dataSource);
        driver.createTable();
        driver.removeAll();
    }

    private static void runConcurrently(int threads, java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        task.accept(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "并发任务应在超时前完成");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void addIfAbsentDoesNotOverwriteExisting() {
        assertTrue(driver.addIfAbsent("k", "v1", 60), "首次写入应成功");
        assertFalse(driver.addIfAbsent("k", "v2", 60), "键已存在时不得写入");
        assertEquals("v1", driver.get("k"), "原值不得被覆盖");
    }

    @Test
    void concurrentAddIfAbsentHasExactlyOneWinner() throws Exception {
        AtomicInteger winners = new AtomicInteger();
        runConcurrently(8, i -> {
            if (driver.addIfAbsent("once", "v" + i, 60)) {
                winners.incrementAndGet();
            }
        });
        assertEquals(1, winners.get(), "并发 add 只能有一个成功（靠主键唯一约束 + 事务兜底）");
    }

    @Test
    void addIfAbsentTreatsExpiredEntryAsAbsent() throws InterruptedException {
        assertTrue(driver.addIfAbsent("short", "v1", 1));
        Thread.sleep(1100);
        assertTrue(driver.addIfAbsent("short", "v2", 60), "已过期条目应视为不存在");
        assertEquals("v2", driver.get("short"));
    }

    @Test
    void concurrentPullHandsValueToExactlyOneThread() throws Exception {
        driver.put("token", "secret", 60);
        AtomicInteger receivers = new AtomicInteger();
        runConcurrently(8, i -> {
            if (driver.pullValue("token") != null) {
                receivers.incrementAndGet();
            }
        });
        assertEquals(1, receivers.get(), "一次性令牌只能被一个线程取到（FOR UPDATE 行锁）");
        assertEquals(null, driver.get("token"), "取走后键应已删除");
    }

    @Test
    void concurrentIncrementDoesNotLoseUpdates() throws Exception {
        int perThread = 25;
        runConcurrently(8, i -> {
            for (int n = 0; n < perThread; n++) {
                driver.incrementAndGet("counter", 1L, 0L);
            }
        });
        Object value = driver.get("counter");
        assertNotNull(value);
        assertEquals(8 * perThread, ((Number) value).intValue(), "并发自增不得丢失更新");
    }

    @Test
    void incrementPreservesExistingTtl() {
        driver.put("rate", 1L, 120);
        Long before = jdbc.queryForObject(
                "SELECT expires_at FROM jaravel_cache WHERE `cache_key` = ?", Long.class, "rate");

        driver.incrementAndGet("rate", 1L, 0L);

        Long after = jdbc.queryForObject(
                "SELECT expires_at FROM jaravel_cache WHERE `cache_key` = ?", Long.class, "rate");
        assertEquals(before, after, "原子自增必须保留原 expires_at（不得变成永不过期）");
    }

    @Test
    void incrementSeedsTtlForNewKeyWhenRequested() {
        driver.incrementAndGet("fresh", 5L, 60);
        Long expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM jaravel_cache WHERE `cache_key` = ?", Long.class, "fresh");
        assertNotNull(expiresAt);
        assertTrue(expiresAt > System.currentTimeMillis(), "新键应带上请求的 TTL");
    }
}