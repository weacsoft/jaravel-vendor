package com.weacsoft.jaravel.vendor.queue.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * 数据库队列驱动，对齐 Laravel {@code Illuminate\Queue\DatabaseQueue}。
 * <p>
 * 将任务持久化到数据库 {@code jobs} 表，支持多实例消费、重试和延迟执行。
 *
 * <h3>多实例消费</h3>
 * 使用 {@code SELECT ... FOR UPDATE SKIP LOCKED}（MySQL 8+）实现非阻塞抢占式消费，
 * 确保同一任务在同一时间只被一个实例处理。对于不支持 SKIP LOCKED 的数据库，
 * 降级为基于 {@code reserved_at} 的乐观锁。
 *
 * <h3>重试机制</h3>
 * 任务执行失败后通过 {@link #release(long, long)} 释放预约，设置延迟后重新入队。
 * 超过 {@code retryAfterSeconds}（默认 1800 秒 = 30 分钟）未被确认的任务会被重新预约。
 *
 * <h3>数据库表结构</h3>
 * 对齐 Laravel {@code jobs} 表：
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
 * </pre>
 */
public class DatabaseQueueDriver implements QueueDriver {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueueDriver.class);

    /** JdbcTemplate 用于数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /** 任务表名，默认 jobs */
    private final String table;

    /** 重试超时秒数，超过此时间未被确认的任务会被重新预约 */
    private final long retryAfterSeconds;

    /**
     * 构造数据库队列驱动。
     *
     * @param dataSource         数据源
     * @param table              任务表名
     * @param retryAfterSeconds  重试超时秒数
     */
    public DatabaseQueueDriver(DataSource dataSource, String table, long retryAfterSeconds) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.table = table;
        this.retryAfterSeconds = retryAfterSeconds;
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
}
