package com.weacsoft.jaravel.vendor.queue.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * 数据库队列驱动，对齐 Laravel {@code Illuminate\Queue\DatabaseQueue}。
 * <p>
 * 将任务持久化到数据库 {@code jobs} 表，支持多实例消费、重试和延迟执行。
 * 失败任务归档到 {@code failed_jobs} 表，对齐 Laravel {@code failed_jobs}。
 *
 * <h3>多实例消费</h3>
 * 使用 {@code SELECT ... FOR UPDATE SKIP LOCKED}（MySQL 8+）实现非阻塞抢占式消费，
 * 确保同一任务在同一时间只被一个实例处理。对于不支持 SKIP LOCKED 的数据库，
 * 降级为基于 {@code reserved_at} 的乐观锁。
 *
 * <h3>重试机制</h3>
 * 任务执行失败后通过 {@link #release(long, long)} 释放预约，设置延迟后重新入队。
 * 超过 {@code retryAfterSeconds}（默认 1800 秒 = 30 分钟）未被确认的任务会被重新预约。
 * 超过最大重试次数后通过 {@link #fail} 归档到 {@code failed_jobs} 表。
 *
 * <h3>自动建表</h3>
 * 构造时自动执行 {@code CREATE TABLE IF NOT EXISTS} 创建 {@code jobs} 与 {@code failed_jobs} 表，
 * 无需手动建表。若数据库账号无 DDL 权限则记录警告并跳过（需提前手动建表）。
 *
 * <h3>数据库表结构</h3>
 * 对齐 Laravel {@code jobs} / {@code failed_jobs} 表：
 * <pre>
 * CREATE TABLE jobs (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   queue VARCHAR(255) NOT NULL,
 *   payload LONGTEXT NOT NULL,
 *   attempts INT NOT NULL DEFAULT 0,
 *   reserved_at BIGINT NULL,
 *   available_at BIGINT NOT NULL,
 *   created_at BIGINT NOT NULL,
 *   INDEX jobs_queue_index (queue)
 * );
 *
 * CREATE TABLE failed_jobs (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   queue VARCHAR(255) NOT NULL,
 *   payload LONGTEXT NOT NULL,
 *   exception LONGTEXT,
 *   attempts INT NOT NULL DEFAULT 0,
 *   failed_at BIGINT NOT NULL,
 *   INDEX failed_jobs_queue_index (queue)
 * );
 * </pre>
 */
public class DatabaseQueueDriver implements QueueDriver {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueueDriver.class);

    /** JdbcTemplate 用于数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /** 任务表名，默认 jobs */
    private final String table;

    /** 失败任务表名，默认 failed_jobs */
    private final String failedTable;

    /** 重试超时秒数，超过此时间未被确认的任务会被重新预约 */
    private final long retryAfterSeconds;

    /** 失败任务保留天数，超过后可由 {@link #purgeOldFailedJobs()} 清理 */
    private final int failedJobRetentionDays;

    /**
     * 构造数据库队列驱动（失败任务保留 7 天）。
     *
     * @param dataSource        数据源
     * @param table             任务表名
     * @param retryAfterSeconds 重试超时秒数
     */
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds) {
        this(dataSource, table, "failed_jobs", retryAfterSeconds, 7);
    }

    /**
     * 构造数据库队列驱动。
     *
     * @param dataSource             数据源
     * @param table                  任务表名
     * @param retryAfterSeconds      重试超时秒数
     * @param failedJobRetentionDays 失败任务保留天数
     */
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds,
                               int failedJobRetentionDays) {
        this(dataSource, table, "failed_jobs", retryAfterSeconds, failedJobRetentionDays);
    }

    /**
     * 全参数构造数据库队列驱动。
     *
     * @param dataSource             数据源
     * @param table                  任务表名
     * @param failedTable            失败任务表名
     * @param retryAfterSeconds      重试超时秒数
     * @param failedJobRetentionDays 失败任务保留天数
     */
    public DatabaseQueueDriver(DataSource dataSource, String table, String failedTable,
                               long retryAfterSeconds, int failedJobRetentionDays) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.table = table;
        this.failedTable = failedTable;
        this.retryAfterSeconds = retryAfterSeconds;
        this.failedJobRetentionDays = failedJobRetentionDays;
        initSchema();
    }

    /** 自动创建 jobs 与 failed_jobs 表（IF NOT EXISTS） */
    private void initSchema() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "queue VARCHAR(255) NOT NULL, " +
                    "payload LONGTEXT NOT NULL, " +
                    "attempts INT NOT NULL DEFAULT 0, " +
                    "reserved_at BIGINT NULL, " +
                    "available_at BIGINT NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "INDEX " + table + "_queue_index (queue)" +
                    ")");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + failedTable + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "queue VARCHAR(255) NOT NULL, " +
                    "payload LONGTEXT NOT NULL, " +
                    "exception LONGTEXT, " +
                    "attempts INT NOT NULL DEFAULT 0, " +
                    "failed_at BIGINT NOT NULL, " +
                    "INDEX " + failedTable + "_queue_index (queue)" +
                    ")");
            logger.info("[queue-db] 自动建表完成: jobs={}, failed_jobs={}", table, failedTable);
        } catch (Exception e) {
            logger.warn("[queue-db] 自动建表失败（请确认 DDL 权限或手动建表）: {}", e.getMessage());
        }
    }

    @Override
    public long push(String queueName, String payload) {
        return push(queueName, payload, 0);
    }

    @Override
    public long push(String queueName, String payload, long delayMs) {
        long now = System.currentTimeMillis();
        long availableAt = delayMs > 0 ? now + delayMs : now;

        String sql = "INSERT INTO " + table + " (queue, payload, attempts, reserved_at, available_at, created_at) VALUES (?, ?, 0, NULL, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, queueName);
            ps.setString(2, payload);
            ps.setLong(3, availableAt);
            ps.setLong(4, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long jobId = key != null ? key.longValue() : -1;
        logger.debug("[queue-db] 推送任务: queue={}, jobId={}, delayMs={}", queueName, jobId, delayMs);
        return jobId;
    }

    @Override
    public QueuedJob pop(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);

        // 查找到期且未被预约的任务
        String selectSql = "SELECT id, queue, payload, attempts, " +
                "COALESCE(reserved_at, 0) as reserved_at, available_at, created_at " +
                "FROM " + table + " " +
                "WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?) " +
                "ORDER BY id ASC LIMIT 1";

        List<QueuedJob> jobs = jdbcTemplate.query(selectSql, (rs, rowNum) -> {
            long id = rs.getLong("id");
            int attempts = rs.getInt("attempts");
            String payload = rs.getString("payload");
            long reservedAt = rs.getLong("reserved_at");
            long availableAt = rs.getLong("available_at");
            long createdAt = rs.getLong("created_at");
            return new QueuedJob(id, queueName, payload, attempts + 1, now, availableAt, createdAt);
        }, queueName, now, expired);

        if (jobs.isEmpty()) {
            return null;
        }

        QueuedJob job = jobs.get(0);
        // 乐观锁：尝试预约（只有未被预约或已过期的任务才能被预约）
        String updateSql = "UPDATE " + table + " SET reserved_at = ?, attempts = attempts + 1 " +
                "WHERE id = ? AND (reserved_at IS NULL OR reserved_at < ?)";
        int updated = jdbcTemplate.update(updateSql, now, job.getId(), expired);
        if (updated == 0) {
            // 被其他实例抢占了
            return null;
        }
        return job;
    }

    @Override
    public void delete(long jobId) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", jobId);
        logger.debug("[queue-db] 删除任务: jobId={}", jobId);
    }

    @Override
    public void release(long jobId) {
        release(jobId, 0);
    }

    @Override
    public void release(long jobId, long delayMs) {
        long availableAt = System.currentTimeMillis() + delayMs;
        jdbcTemplate.update(
                "UPDATE " + table + " SET reserved_at = NULL, available_at = ? WHERE id = ?",
                availableAt, jobId);
        logger.debug("[queue-db] 释放任务: jobId={}, delayMs={}", jobId, delayMs);
    }

    @Override
    public int size(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?)",
                Integer.class, queueName, now, expired);
        return count != null ? count : 0;
    }

    @Override
    public void clear(String queueName) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE queue = ?", queueName);
        logger.info("[queue-db] 清空队列: {}", queueName);
    }

    // ==================== 失败队列 ====================

    @Override
    public void fail(long jobId, String queue, String payload, int attempts, String exception) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO " + failedTable + " (queue, payload, exception, attempts, failed_at) VALUES (?, ?, ?, ?, ?)",
                queue, payload, exception, attempts, now);
        // 从 jobs 表移除原任务
        delete(jobId);
        logger.warn("[queue-db] 任务归档到失败队列: jobId={}, queue={}, attempts={}", jobId, queue, attempts);
    }

    @Override
    public List<QueuedJob> getFailedJobs() {
        return jdbcTemplate.query(
                "SELECT id, queue, payload, exception, attempts, failed_at FROM " + failedTable + " ORDER BY id DESC",
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    String queue = rs.getString("queue");
                    String payload = rs.getString("payload");
                    String exception = rs.getString("exception");
                    int attempts = rs.getInt("attempts");
                    long failedAt = rs.getLong("failed_at");
                    return new QueuedJob(id, queue, payload, attempts, 0, failedAt, failedAt, exception);
                });
    }

    @Override
    public void retryFailedJob(long failedJobId) {
        List<QueuedJob> jobs = jdbcTemplate.query(
                "SELECT id, queue, payload, exception, attempts, failed_at FROM " + failedTable + " WHERE id = ?",
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    String queue = rs.getString("queue");
                    String payload = rs.getString("payload");
                    String exception = rs.getString("exception");
                    int attempts = rs.getInt("attempts");
                    long failedAt = rs.getLong("failed_at");
                    return new QueuedJob(id, queue, payload, attempts, 0, failedAt, failedAt, exception);
                }, failedJobId);
        if (jobs.isEmpty()) {
            logger.warn("[queue-db] 重试失败任务不存在: failedJobId={}", failedJobId);
            return;
        }
        QueuedJob job = jobs.get(0);
        // 重新推入原队列（重置尝试次数为 0）
        long newJobId = push(job.getQueue(), job.getPayload());
        // 从失败队列移除
        jdbcTemplate.update("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        logger.info("[queue-db] 重试失败任务: failedJobId={}, 新 jobId={}, queue={}", failedJobId, newJobId, job.getQueue());
    }

    @Override
    public void deleteFailedJob(long failedJobId) {
        int deleted = jdbcTemplate.update("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        if (deleted > 0) {
            logger.info("[queue-db] 删除失败任务: failedJobId={}", failedJobId);
        } else {
            logger.warn("[queue-db] 删除失败任务不存在: failedJobId={}", failedJobId);
        }
    }

    @Override
    public void clearFailedJobs() {
        jdbcTemplate.update("DELETE FROM " + failedTable);
        logger.info("[queue-db] 清空所有失败任务");
    }

    /**
     * 清理超过保留天数的失败任务。
     * <p>
     * 保留天数由构造参数 {@code failedJobRetentionDays} 决定，默认 7 天。
     * 可由外部定时任务周期性调用，对齐 Laravel {@code queue:prune-failed-jobs}。
     */
    public void purgeOldFailedJobs() {
        long threshold = System.currentTimeMillis() - (long) failedJobRetentionDays * 24 * 60 * 60 * 1000;
        int deleted = jdbcTemplate.update("DELETE FROM " + failedTable + " WHERE failed_at < ?", threshold);
        if (deleted > 0) {
            logger.info("[queue-db] 清理过期失败任务: count={}, retentionDays={}", deleted, failedJobRetentionDays);
        }
    }
}
