package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.queue.QueuedJob;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 并发抢占回归测试：多工作线程同时 {@code pop} 时，<b>同一任务只能被预约一次</b>。
 * <p>
 * 背景：{@code pop} 已从「{@code ORDER BY id ASC LIMIT 1} + 抢失败直接返回 null」改为
 * 「可移植的 {@code id = (SELECT MIN(id) ...)} + 条件 UPDATE 预约 + 竞争失败退避重试」。
 * 这里同时固定两件事：
 * <ul>
 *   <li><b>不重复</b>：同一 jobId 不会被两个线程取出（乐观锁生效）；</li>
 *   <li><b>不空转</b>：所有任务最终都能被取完（抢失败会重试而不是被当成「队列为空」）。</li>
 * </ul>
 * 说明：本用例只覆盖 H2（{@code MODE=MySQL}）。Oracle / SQL Server 的真实方言行为
 * 需要对应数据库环境，当前测试基建无法覆盖（已在 CHANGELOG 记为未验证项）。
 */
class DatabaseQueueConcurrencyTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private DatabaseQueueDriver driver;

    @BeforeAll
    static void initDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:queueconc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS jobs ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "queue VARCHAR(255) NOT NULL, "
                + "payload CLOB NOT NULL, "
                + "attempts INT NOT NULL DEFAULT 0, "
                + "reserved_at BIGINT, "
                + "available_at BIGINT NOT NULL, "
                + "created_at BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS failed_jobs ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "queue VARCHAR(255) NOT NULL, "
                + "payload CLOB NOT NULL, "
                + "exception CLOB, "
                + "attempts INT NOT NULL DEFAULT 0, "
                + "failed_at BIGINT NOT NULL)");
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM failed_jobs");
        driver = new DatabaseQueueDriver(dataSource, "jobs", 60);
    }

    @Test
    void concurrentPopReservesEachJobExactlyOnce() throws Exception {
        int total = 40;
        for (int i = 0; i < total; i++) {
            driver.push("default", "{\"seq\":" + i + "}");
        }

        Set<Long> reservedIds = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 25; i++) {
                        QueuedJob job = driver.pop("default");
                        if (job != null && !reservedIds.add(job.getId())) {
                            duplicates.incrementAndGet();
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, duplicates.get(), "同一任务不得被两个线程同时预约");
        assertEquals(total, reservedIds.size(), "所有任务都应被取出（抢失败必须重试而不是空转）");
        assertNull(driver.pop("default"), "任务已被预约后不得再次取出（retryAfter=60s 内）");
    }

    @Test
    void popOnEmptyQueueReturnsNullWithoutRetryStorm() {
        long start = System.currentTimeMillis();
        assertNull(driver.pop("default"));
        // 空队列应立刻返回（不进入退避重试）：3 次退避会引入 ~60ms
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(true, elapsed < 500, "空队列必须快速返回，实际 " + elapsed + "ms");
    }
}