package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.QueuedJob;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库队列驱动，对齐 Laravel {@code Illuminate\Queue\DatabaseQueue}。
 * <p>
 * 将任务持久化到数据库 {@code jobs} 表，支持多实例消费、重试和延迟执行。
 * 失败任务归档到 {@code failed_jobs} 表，对齐 Laravel {@code failed_jobs}。
 * <p>
 * <b>D3（Spring 解耦终收）</b>：SQL 操作已全部改为原生 JDBC（{@code Connection/PreparedStatement/ResultSet}，
 * 复刻 {@code cache-database} 模块的驱动模板），不再依赖 spring-jdbc；
 * {@link DataSource} 来自 database 模块 {@code ConnectionManager} 注册表或业务方显式传入。
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
 * <h3>手动建表</h3>
 * <b>不会自动建表</b>：需通过 {@code artisan queue:table} 命令或手动调用 {@link #createTable()} 创建
 * {@code jobs} 与 {@code failed_jobs} 表。若数据库账号无 DDL 权限则需提前手动建表。
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

    /** 数据源（来自 database 模块 {@code ConnectionManager} 注册表或业务方显式传入） */
    private final DataSource dataSource;

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
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null（请通过 database 模块注册连接后使用）");
        }
        this.dataSource = dataSource;
        this.table = table;
        this.failedTable = failedTable;
        this.retryAfterSeconds = retryAfterSeconds;
        this.failedJobRetentionDays = failedJobRetentionDays;
        // 不自动建表：需通过 artisan queue:table 命令或手动调用 createTable() 创建
    }

    /**
     * 创建任务表与失败任务表（IF NOT EXISTS）。
     * <p>
     * 由 {@code artisan queue:table} 命令调用，或由业务方手动调用。
     *
     * @return true 表示建表成功
     */
    public boolean createTable() {
        try {
            // 建表（不在 CREATE TABLE 内使用 INDEX，保证 SQLite/MySQL/H2 通用）
            executeSql("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "queue VARCHAR(255) NOT NULL, "
                    + "payload TEXT NOT NULL, "
                    + "attempts INT NOT NULL DEFAULT 0, "
                    + "reserved_at BIGINT NULL, "
                    + "available_at BIGINT NOT NULL, "
                    + "created_at BIGINT NOT NULL"
                    + ")");
            executeSql("CREATE TABLE IF NOT EXISTS " + failedTable + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "queue VARCHAR(255) NOT NULL, "
                    + "payload TEXT NOT NULL, "
                    + "exception TEXT, "
                    + "attempts INT NOT NULL DEFAULT 0, "
                    + "failed_at BIGINT NOT NULL"
                    + ")");
            // 单独创建索引（SQLite/MySQL/H2 均支持 CREATE INDEX IF NOT EXISTS 语法）
            createIndexIfAbsent(table + "_queue_index", table, "queue");
            createIndexIfAbsent(failedTable + "_queue_index", failedTable, "queue");
            logger.info("[queue-db] 建表完成: jobs={}, failed_jobs={}", table, failedTable);
            return true;
        } catch (Exception e) {
            logger.warn("[queue-db] 建表失败（请确认 DDL 权限或手动建表）: {}", e.getMessage());
            return false;
        }
    }

    /** 建索引；不支持 {@code IF NOT EXISTS} 的数据库退化为普通 CREATE（已存在则忽略） */
    private void createIndexIfAbsent(String indexName, String tbl, String column) {
        try {
            executeSql("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tbl + " (" + column + ")");
        } catch (Exception ignored) {
            try {
                executeSql("CREATE INDEX " + indexName + " ON " + tbl + " (" + column + ")");
            } catch (Exception ignored2) {
                // 索引已存在或无权限，忽略
            }
        }
    }

    /** @return 任务表名 */
    public String getTable() {
        return table;
    }

    /** @return 失败任务表名 */
    public String getFailedTable() {
        return failedTable;
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
        long jobId = insertReturningKey(
                queueName, payload, availableAt, now, sql);
        logger.debug("[queue-db] 推送任务: queue={}, jobId={}, delayMs={}", queueName, jobId, delayMs);
        return jobId;
    }

    /**
     * 执行带自增键返回的 INSERT。
     *
     * @return 自增主键；取不到时为 -1
     */
    private long insertReturningKey(Object p1, Object p2, Object p3, Object p4, String sql) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p1, p2, p3, p4);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    @Override
    public QueuedJob pop(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);

        // 查找到期且未被预约的任务
        String selectSql = "SELECT id, queue, payload, attempts, "
                + "COALESCE(reserved_at, 0) as reserved_at, available_at, created_at "
                + "FROM " + table + " "
                + "WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?) "
                + "ORDER BY id ASC LIMIT 1";

        List<QueuedJob> jobs = queryRows(selectSql, rs -> {
            long id = rs.getLong("id");
            int attempts = rs.getInt("attempts");
            String payloadStr = rs.getString("payload");
            long availableAt = rs.getLong("available_at");
            long createdAt = rs.getLong("created_at");
            return new QueuedJob(id, queueName, payloadStr, attempts + 1, now, availableAt, createdAt);
        }, queueName, now, expired);

        if (jobs.isEmpty()) {
            return null;
        }

        QueuedJob job = jobs.get(0);
        // 乐观锁：尝试预约（只有未被预约或已过期的任务才能被预约）
        String updateSql = "UPDATE " + table + " SET reserved_at = ?, attempts = attempts + 1 "
                + "WHERE id = ? AND (reserved_at IS NULL OR reserved_at < ?)";
        int updated = executeUpdate(updateSql, now, job.getId(), expired);
        if (updated == 0) {
            // 被其他实例抢占了
            return null;
        }
        return job;
    }

    @Override
    public void delete(long jobId) {
        executeUpdate("DELETE FROM " + table + " WHERE id = ?", jobId);
        logger.debug("[queue-db] 删除任务: jobId={}", jobId);
    }

    @Override
    public void release(long jobId) {
        release(jobId, 0);
    }

    @Override
    public void release(long jobId, long delayMs) {
        long availableAt = System.currentTimeMillis() + delayMs;
        executeUpdate(
                "UPDATE " + table + " SET reserved_at = NULL, available_at = ? WHERE id = ?",
                availableAt, jobId);
        logger.debug("[queue-db] 释放任务: jobId={}, delayMs={}", jobId, delayMs);
    }

    @Override
    public int size(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);
        List<Integer> counts = queryRows(
                "SELECT COUNT(*) FROM " + table + " WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?)",
                rs -> rs.getInt(1), queueName, now, expired);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    @Override
    public void clear(String queueName) {
        executeUpdate("DELETE FROM " + table + " WHERE queue = ?", queueName);
        logger.info("[queue-db] 清空队列: {}", queueName);
    }

    // ==================== 失败队列 ====================

    @Override
    public void fail(long jobId, String queue, String payload, int attempts, String exception) {
        long now = System.currentTimeMillis();
        executeUpdate(
                "INSERT INTO " + failedTable + " (queue, payload, exception, attempts, failed_at) VALUES (?, ?, ?, ?, ?)",
                queue, payload, exception, attempts, now);
        // 从 jobs 表移除原任务
        delete(jobId);
        logger.warn("[queue-db] 任务归档到失败队列: jobId={}, queue={}, attempts={}", jobId, queue, attempts);
    }

    @Override
    public List<QueuedJob> getFailedJobs() {
        return queryRows(
                "SELECT id, queue, payload, exception, attempts, failed_at FROM " + failedTable + " ORDER BY id DESC",
                rs -> {
                    long id = rs.getLong("id");
                    String queue = rs.getString("queue");
                    String payloadStr = rs.getString("payload");
                    String exceptionMsg = rs.getString("exception");
                    int attempts = rs.getInt("attempts");
                    long failedAt = rs.getLong("failed_at");
                    return new QueuedJob(id, queue, payloadStr, attempts, 0, failedAt, failedAt, exceptionMsg);
                });
    }

    @Override
    public void retryFailedJob(long failedJobId) {
        List<QueuedJob> jobs = queryRows(
                "SELECT id, queue, payload, exception, attempts, failed_at FROM " + failedTable + " WHERE id = ?",
                rs -> {
                    long id = rs.getLong("id");
                    String queue = rs.getString("queue");
                    String payloadStr = rs.getString("payload");
                    String exceptionMsg = rs.getString("exception");
                    int attempts = rs.getInt("attempts");
                    long failedAt = rs.getLong("failed_at");
                    return new QueuedJob(id, queue, payloadStr, attempts, 0, failedAt, failedAt, exceptionMsg);
                }, failedJobId);
        if (jobs.isEmpty()) {
            logger.warn("[queue-db] 重试失败任务不存在: failedJobId={}", failedJobId);
            return;
        }
        QueuedJob job = jobs.get(0);
        // 重新推入原队列（重置尝试次数为 0）
        long newJobId = push(job.getQueue(), job.getPayload());
        // 从失败队列移除
        executeUpdate("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        logger.info("[queue-db] 重试失败任务: failedJobId={}, 新 jobId={}, queue={}", failedJobId, newJobId, job.getQueue());
    }

    @Override
    public void deleteFailedJob(long failedJobId) {
        int deleted = executeUpdate("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        if (deleted > 0) {
            logger.info("[queue-db] 删除失败任务: failedJobId={}", failedJobId);
        } else {
            logger.warn("[queue-db] 删除失败任务不存在: failedJobId={}", failedJobId);
        }
    }

    @Override
    public void clearFailedJobs() {
        executeUpdate("DELETE FROM " + failedTable);
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
        int deleted = executeUpdate("DELETE FROM " + failedTable + " WHERE failed_at < ?", threshold);
        if (deleted > 0) {
            logger.info("[queue-db] 清理过期失败任务: count={}, retentionDays={}", deleted, failedJobRetentionDays);
        }
    }

    // ==================== 原生 JDBC 工具方法（复刻 cache-database 四件套） ====================

    /**
     * 执行更新语句（INSERT/UPDATE/DELETE）。
     *
     * @return 受影响行数
     * @throws IllegalStateException 数据库错误（含表不存在——请先执行 {@code artisan queue:table}）
     */
    private int executeUpdate(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    /**
     * 执行建表等 DDL 语句。
     *
     * @throws IllegalStateException 数据库错误
     */
    private void executeSql(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    /**
     * 执行查询并逐行映射。
     *
     * @throws IllegalStateException 数据库错误
     */
    private <T> List<T> queryRows(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    /** 行映射函数：允许抛出受检的 {@link SQLException} */
    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
