package com.weacsoft.jaravel.vendor.queue.database;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.h2.jdbcx.JdbcDataSource;

/**
 * {@link DatabaseQueueDriver} 数据库队列 push/pop/失败归档逻辑单元测试（基于 H2 内存库，MySQL 兼容模式）。
 */
class DatabaseQueueDriverTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private DatabaseQueueDriver driver;

    @BeforeAll
    static void initDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:queuedb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);

        // 预先以 H2 兼容 DDL 建表，保证即便驱动自动建表语法不被支持也能正常工作
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
        // retryAfterSeconds=60，失败保留 7 天
        driver = new DatabaseQueueDriver(dataSource, "jobs", 60);
    }

    @Test
    void createTableCreatesJobsAndFailedJobsTables() {
        // 使用独立的数据源验证 createTable() 方法
        JdbcDataSource ds2 = new JdbcDataSource();
        ds2.setURL("jdbc:h2:mem:queue_create_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds2.setUser("sa");
        ds2.setPassword("");

        DatabaseQueueDriver d = new DatabaseQueueDriver(ds2, "test_jobs", "test_failed_jobs", 60, 7);
        boolean success = d.createTable();
        assertTrue(success, "createTable() 应返回 true");

        // 验证表已创建
        JdbcTemplate jdbc2 = new JdbcTemplate(ds2);
        Integer jobCount = jdbc2.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TEST_JOBS'", Integer.class);
        assertEquals(1, jobCount, "test_jobs 表应已创建");

        Integer failedCount = jdbc2.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TEST_FAILED_JOBS'", Integer.class);
        assertEquals(1, failedCount, "test_failed_jobs 表应已创建");
    }

    @Test
    void pushAndPopRoundTrip() {
        long id = driver.push("default", "{\"job\":\"hello\"}");
        assertTrue(id > 0);
        assertEquals(1, driver.size("default"));

        QueuedJob job = driver.pop("default");
        assertNotNull(job);
        assertEquals(id, job.getId());
        assertEquals("default", job.getQueue());
        assertEquals("{\"job\":\"hello\"}", job.getPayload());
        assertEquals(1, job.getAttempts());

        // 已被预约，再次 pop 返回 null
        assertNull(driver.pop("default"));
    }

    @Test
    void delayedPushIsNotImmediatelyAvailable() {
        long id = driver.push("default", "{\"delayed\":true}", 5000);
        assertTrue(id > 0);
        // 延迟 5 秒，当前不可消费
        assertNull(driver.pop("default"));
        assertEquals(0, driver.size("default"));
    }

    @Test
    void deleteRemovesJob() {
        long id = driver.push("default", "p");
        driver.delete(id);
        assertNull(driver.pop("default"));
        assertEquals(0, driver.size("default"));
    }

    @Test
    void releaseMakesJobAvailableAgain() {
        long id = driver.push("default", "retry-me");
        QueuedJob first = driver.pop("default");
        assertNotNull(first);
        assertEquals(1, first.getAttempts());

        // 释放（无延迟）后可再次消费，尝试次数递增
        driver.release(id);
        QueuedJob second = driver.pop("default");
        assertNotNull(second);
        assertEquals(id, second.getId());
        assertEquals(2, second.getAttempts());
    }

    @Test
    void sizeAndClear() {
        driver.push("emails", "a");
        driver.push("emails", "b");
        driver.push("orders", "c");
        assertEquals(2, driver.size("emails"));
        assertEquals(1, driver.size("orders"));

        driver.clear("emails");
        assertEquals(0, driver.size("emails"));
        assertEquals(1, driver.size("orders"));
    }

    @Test
    void failArchivesToFailedJobsAndRetryRestores() {
        long id = driver.push("default", "will-fail");
        QueuedJob job = driver.pop("default");
        assertNotNull(job);

        // 归档到失败队列
        driver.fail(id, "default", "will-fail", 1, "boom");
        assertEquals(0, driver.size("default"), "原任务应从 jobs 表移除");

        java.util.List<QueuedJob> failed = driver.getFailedJobs();
        assertEquals(1, failed.size());
        QueuedJob failedJob = failed.get(0);
        assertEquals("will-fail", failedJob.getPayload());
        assertEquals("boom", failedJob.getException());
        assertEquals(1, failedJob.getAttempts());

        // 重试：重新入队并从失败队列移除
        driver.retryFailedJob(failedJob.getId());
        assertTrue(driver.getFailedJobs().isEmpty());
        assertEquals(1, driver.size("default"));
        QueuedJob retried = driver.pop("default");
        assertNotNull(retried);
        assertEquals("will-fail", retried.getPayload());
    }

    @Test
    void purgeOldFailedJobsRespectsRetention() {
        long now = System.currentTimeMillis();
        long day = 24L * 60 * 60 * 1000;
        // 手动插入一条超过 7 天保留期的失败任务
        jdbc.update("INSERT INTO failed_jobs (queue, payload, exception, attempts, failed_at) VALUES (?, ?, ?, ?, ?)",
                "default", "old", "err", 3, now - 8 * day);
        // 一条近期失败任务
        jdbc.update("INSERT INTO failed_jobs (queue, payload, exception, attempts, failed_at) VALUES (?, ?, ?, ?, ?)",
                "default", "recent", "err", 1, now);

        assertEquals(2, driver.getFailedJobs().size());
        driver.purgeOldFailedJobs();
        assertEquals(1, driver.getFailedJobs().size());
        assertEquals("recent", driver.getFailedJobs().get(0).getPayload());
    }
}
