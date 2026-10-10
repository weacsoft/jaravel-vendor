package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.QueuedJob;
import com.weacsoft.jaravel.vendor.database.JdbcExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

/**
 * 数据库队列驱动，对齐 Laravel {@code Illuminate\Queue\DatabaseQueue}。
 * <p>
 * 将任务持久化到数据库 {@code jobs} 表，支持多实例消费、重试和延迟执行。
 * 失败任务归档到 {@code failed_jobs} 表，对齐 Laravel {@code failed_jobs}。
 * <p>
 * <b>架构对齐（D3 收口 + 0.1.3）</b>：SQL 操作统一经由 database 模块的
 * {@link JdbcExecutor} 执行（连接来自 {@code ConnectionManager} 注册表或业务方显式传入），
 * 不再依赖 spring-jdbc，也不再自带一套私有 JDBC 工具；建表统一走 migration 模块
 * 迁移能力（{@code vendor:publish --tag=migrations} 发布内置迁移 + {@code artisan migrate}，
 * 或 {@code artisan queue:table} 生成迁移文件）。
 *
 * <h3>多实例消费</h3>
 * 使用<b>乐观锁预约</b>实现抢占式消费：先按可移植的 {@code id = (SELECT MIN(id) ...)} 选出候选任务，
 * 再用条件 {@code UPDATE ... WHERE id = ? AND (reserved_at IS NULL OR reserved_at < ?)} 原子预约，
 * 确保同一任务在同一时间只被一个实例处理。
 * <p>
 * <b>为什么不走 {@code FOR UPDATE SKIP LOCKED}</b>：那一版实现要求 SELECT 与 UPDATE 处于同一事务，
 * 而 {@link JdbcExecutor} 目前不提供事务 API（{@code inTransaction} 为待办）；此外
 * {@code LIMIT} 在 Oracle / SQL Server 上不合法。候选抢占失败时本实现会<b>短暂退避重试</b>，
 * 而不是把「抢失败」当成「队列为空」。
 * <p>
 * 因此投递保证为<b>至少一次</b>：崩溃或超过 {@code retryAfterSeconds} 后任务会被重新取出，
 * 消费方必须<u>幂等</u>。
 *
 * <h3>重试机制</h3>
 * 任务执行失败后通过 {@link #release(long, long)} 释放预约，设置延迟后重新入队。
 * 超过 {@code retryAfterSeconds}（默认 1800 秒 = 30 分钟）未被确认的任务会被重新预约。
 * 超过最大重试次数后通过 {@link #fail} 归档到 {@code failed_jobs} 表。
 *
 * <h3>建表（统一走迁移能力）</h3>
 * <b>不会自动建表</b>：推荐 {@code artisan vendor:publish --tag=migrations} 发布本模块内置迁移
 * （默认 {@code jobs} / {@code failed_jobs} 表）后执行 {@code artisan migrate}；
 * 或 {@code artisan queue:table} 生成迁移文件后 migrate；也可手动调用 {@link #createTable()}
 *（内部即经由 migration 模块 {@code Schema.createIfAbsent} 建表）。
 * 若数据库账号无 DDL 权限则需提前手动建表。
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

    /** 抢占失败后的最大重试次数（把「抢失败」与「队列为空」区分开） */
    private static final int POP_MAX_ATTEMPTS = 3;

    /** 抢占重试的基础退避毫秒（第 n 次退避 = n × 本值） */
    private static final long POP_RETRY_BACKOFF_MS = 10L;

    /** 数据库产品名（懒探测并缓存；用于选择 SKIP LOCKED 方言，探测失败按不支持处理） */
    private volatile String productName;

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

    /** database 模块 SQL 执行底座（驱动不再自带私有 JDBC 四件套） */
    private final JdbcExecutor jdbc;

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
        this.jdbc = new JdbcExecutor(dataSource);
        // 不自动建表：建表统一走迁移能力（vendor:publish --tag=migrations / queue:table + migrate）
    }

    /**
     * 声明任务表结构——与模块内置迁移文件（jobs：自增主键 + queue/payload/attempts/
     * reserved_at/available_at/created_at + queue 索引）共用的一致定义。
     *
     * @param builder migration 模块 Blueprint 构建器
     */
    public void defineJobsTable(com.weacsoft.jaravel.vendor.migration.Blueprint builder) {
        builder.id();
        builder.string("queue", 255);
        builder.text("payload");
        builder.integer("attempts").defaultValue(0);
        builder.bigInteger("reserved_at").nullable();
        builder.bigInteger("available_at");
        builder.bigInteger("created_at");
        builder.index("queue");
    }

    /**
     * 声明失败任务表结构——与模块内置迁移文件共用的一致定义。
     *
     * @param builder migration 模块 Blueprint 构建器
     */
    public void defineFailedJobsTable(com.weacsoft.jaravel.vendor.migration.Blueprint builder) {
        builder.id();
        builder.string("queue", 255);
        builder.text("payload");
        builder.text("exception").nullable();
        builder.integer("attempts").defaultValue(0);
        builder.bigInteger("failed_at");
        builder.index("queue");
    }

    /**
     * 创建任务表与失败任务表（若不存在）——统一经由 migration 模块
     * {@link com.weacsoft.jaravel.vendor.migration.Schema#createIfAbsent} 完成：
     * 方言感知的存在性检查 + DDL 生成（自增主键 / 索引均由方言负责，
     * 不再手拼 {@code CREATE TABLE IF NOT EXISTS}——SQL Server 不支持该语法，
     * 且旧内置 DDL 硬编码 {@code AUTO_INCREMENT}，SQLite / Oracle 上直接失败）。
     * <p>
     * 建表同样推荐走迁移能力：{@code vendor:publish --tag=migrations} 发布内置迁移 +
     * {@code artisan migrate}，或 {@code artisan queue:table} 生成迁移文件后 migrate。
     *
     * @return true 表示建表成功或表已存在
     */
    public boolean createTable() {
        try {
            com.weacsoft.jaravel.vendor.migration.Schema schema =
                    new com.weacsoft.jaravel.vendor.migration.Schema(dataSource);
            boolean jobs = schema.createIfAbsent(table, this::defineJobsTable);
            boolean failed = schema.createIfAbsent(failedTable, this::defineFailedJobsTable);
            logger.info("[queue-db] 建表完成: jobs={} (created={}), failed_jobs={} (created={})",
                    table, jobs, failedTable, failed);
            return true;
        } catch (Exception e) {
            logger.warn("[queue-db] 建表失败（请确认 DDL 权限，或先执行内置迁移 / artisan queue:table）: {}", e.getMessage());
            return false;
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

        long jobId = jdbc.insertReturningKey(
                "INSERT INTO " + table + " (queue, payload, attempts, reserved_at, available_at, created_at) VALUES (?, ?, 0, NULL, ?, ?)",
                queueName, payload, availableAt, now);
        logger.debug("[queue-db] 推送任务: queue={}, jobId={}, delayMs={}", queueName, jobId, delayMs);
        return jobId;
    }

    @Override
    public QueuedJob pop(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);

        // 优先走「方言感知的 SKIP LOCKED 抢占」：SELECT ... FOR UPDATE SKIP LOCKED 与 UPDATE
        // 处于同一事务，候选行被锁住，其他实例直接跳过 —— 无竞争重试、无空转。
        String claimSql = claimSqlFor(productName(), table);
        if (claimSql != null) {
            QueuedJob claimed = claimBySkipLocked(claimSql, queueName, now, expired);
            if (claimed != null) {
                return claimed;
            }
            // SKIP LOCKED 下返回 null 就是「确实没有可执行任务」，无需退避重试
            return null;
        }
        return popOptimistic(queueName, now, expired);
    }

    /**
     * SKIP LOCKED 抢占：同一事务内「锁定候选行 + 预约」。
     *
     * @param claimSql  方言特化的候选查询（含 FOR UPDATE SKIP LOCKED）
     * @param queueName 队列名
     * @param now       当前时间戳
     * @param expired   预约过期阈值（早于此值的预约视为可抢占）
     * @return 抢占到的任务；无候选返回 null
     */
    private QueuedJob claimBySkipLocked(String claimSql, String queueName, long now, long expired) {
        try {
            return jdbc.inTransaction(tx -> {
                List<QueuedJob> jobs = tx.queryMapped(claimSql, rs -> {
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
                // 行已在事务内被锁定 → 这条 UPDATE 不会再与其它实例竞争
                tx.update("UPDATE " + table + " SET reserved_at = ?, attempts = attempts + 1 WHERE id = ?",
                        now, job.getId());
                return job;
            });
        } catch (RuntimeException e) {
            // 方言判断失误（例如实际不支持 SKIP LOCKED）时降级到乐观锁，避免整个消费停摆
            logger.warn("[queue-db] SKIP LOCKED 抢占失败，降级为乐观锁路径: {}", e.getMessage());
            return popOptimistic(queueName, now, expired);
        }
    }

    /**
     * 乐观锁抢占（降级路径）：可移植的 {@code id = (SELECT MIN(id) ...)} + 条件 UPDATE + 竞争退避重试。
     * <p>
     * 该路径在<b>任何</b>支持标准 SQL 的库上都正确（架构评审结论）；SKIP LOCKED 不可用时走这里。
     *
     * @param queueName 队列名
     * @param now       当前时间戳
     * @param expired   预约过期阈值
     * @return 抢占到的任务；无候选或多次竞争失败返回 null
     */
    private QueuedJob popOptimistic(String queueName, long now, long expired) {
        // 可移植写法：用「id = (SELECT MIN(id) ...)」代替 LIMIT 1（Oracle / SQL Server 不支持 LIMIT）
        String selectSql = "SELECT id, queue, payload, attempts, "
                + "COALESCE(reserved_at, 0) as reserved_at, available_at, created_at "
                + "FROM " + table + " "
                + "WHERE id = (SELECT MIN(id) FROM " + table + " "
                + "  WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?))";

        String updateSql = "UPDATE " + table + " SET reserved_at = ?, attempts = attempts + 1 "
                + "WHERE id = ? AND (reserved_at IS NULL OR reserved_at < ?)";

        for (int attempt = 0; attempt < POP_MAX_ATTEMPTS; attempt++) {
            List<QueuedJob> jobs = jdbc.queryMapped(selectSql, rs -> {
                long id = rs.getLong("id");
                int attempts = rs.getInt("attempts");
                String payloadStr = rs.getString("payload");
                long availableAt = rs.getLong("available_at");
                long createdAt = rs.getLong("created_at");
                return new QueuedJob(id, queueName, payloadStr, attempts + 1, now, availableAt, createdAt);
            }, queueName, now, expired);

            if (jobs.isEmpty()) {
                return null;   // 确实没有可执行任务
            }

            QueuedJob job = jobs.get(0);
            // 乐观锁：尝试预约（只有未被预约或已过期的任务才能被预约）
            int updated = jdbc.update(updateSql, now, job.getId(), expired);
            if (updated > 0) {
                return job;
            }
            // 被其他实例抢占：短暂退避后重试。不能直接 return null —— 上层 workLoop 把 null
            // 当成「队列为空」去 sleep，高并发下会表现为空转与任务延迟。
            try {
                Thread.sleep(POP_RETRY_BACKOFF_MS * (attempt + 1L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 按数据库产品名生成 SKIP LOCKED 抢占 SQL；<b>不支持的方言返回 {@code null}</b>（调用方降级）。
     * <p>
     * 各方言差异：
     * <ul>
     *   <li>{@code MySQL} / {@code MariaDB} / {@code PostgreSQL} / {@code H2}：
     *       {@code ORDER BY id ASC LIMIT 1 FOR UPDATE SKIP LOCKED}</li>
     *   <li>{@code Oracle}：{@code FETCH FIRST 1 ROWS ONLY FOR UPDATE SKIP LOCKED}（无 LIMIT）</li>
     *   <li>{@code SQL Server}（含 Microsoft SQL Server）：用
     *       {@code WITH (UPDLOCK, READPAST)} + {@code TOP 1}（它没有 SKIP LOCKED 关键字）</li>
     *   <li>{@code SQLite}：无行级锁，返回 {@code null} 走乐观锁</li>
     * </ul>
     * 该方法是纯函数，便于用单测固定各方言输出（不需真实 Oracle/SQL Server 环境）。
     *
     * @param productName JDBC 数据库产品名（可空）
     * @return 抢占 SQL；不支持返回 null
     */
    static String claimSqlFor(String productName) {
        return claimSqlFor(productName, "jobs");
    }

    /**
     * 按数据库产品名 + 表名生成抢占 SQL。
     *
     * @param productName JDBC 数据库产品名（可空）
     * @param table       任务表名
     * @return 抢占 SQL；不支持的方言返回 null
     */
    static String claimSqlFor(String productName, String table) {
        String name = productName == null ? "" : productName.toLowerCase();
        // 只有「确定支持」的方言才走 SKIP LOCKED；其余（SQLite/未知/第三方库）一律返回 null
        // 交给乐观锁路径 —— 乐观锁在任何标准 SQL 库上都正确，宁可少一层优化也不要不可预期的报错。
        boolean skipLockedCapable = name.contains("mysql") || name.contains("mariadb")
                || name.contains("postgres") || name.contains("h2");
        boolean oracle = name.contains("oracle");
        boolean sqlServer = name.contains("sql server") || name.contains("microsoft");
        if (!skipLockedCapable && !oracle && !sqlServer) {
            return null;
        }
        String columns = "id, queue, payload, attempts, "
                + "COALESCE(reserved_at, 0) as reserved_at, available_at, created_at";
        String where = " WHERE queue = ? AND available_at <= ? "
                + "AND (reserved_at IS NULL OR reserved_at < ?)";
        if (sqlServer) {
            // SQL Server 没有 SKIP LOCKED 关键字：UPDLOCK 取更新锁 + READPAST 跳过已锁行
            return "SELECT TOP 1 " + columns + " FROM " + table
                    + " WITH (UPDLOCK, READPAST)" + where + " ORDER BY id ASC";
        }
        String limit = oracle ? " FETCH FIRST 1 ROWS ONLY" : " LIMIT 1";
        return "SELECT " + columns + " FROM " + table + where
                + " ORDER BY id ASC" + limit + " FOR UPDATE SKIP LOCKED";
    }

    /** 懒探测数据库产品名（仅用于选择抢占方言；探测失败按不支持处理） */
    private String productName() {
        String cached = productName;
        if (cached != null) {
            return cached;
        }
        try (java.sql.Connection conn = dataSource.getConnection()) {
            cached = conn.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            logger.debug("[queue-db] 探测数据库产品名失败，使用乐观锁路径: {}", e.getMessage());
            cached = "";
        }
        productName = cached;
        return cached;
    }

    @Override
    public void delete(long jobId) {
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", jobId);
        logger.debug("[queue-db] 删除任务: jobId={}", jobId);
    }

    @Override
    public void release(long jobId) {
        release(jobId, 0);
    }

    @Override
    public void release(long jobId, long delayMs) {
        long availableAt = System.currentTimeMillis() + delayMs;
        jdbc.update(
                "UPDATE " + table + " SET reserved_at = NULL, available_at = ? WHERE id = ?",
                availableAt, jobId);
        logger.debug("[queue-db] 释放任务: jobId={}, delayMs={}", jobId, delayMs);
    }

    @Override
    public int size(String queueName) {
        long now = System.currentTimeMillis();
        long expired = now - (retryAfterSeconds * 1000);
        List<Integer> counts = jdbc.queryMapped(
                "SELECT COUNT(*) FROM " + table + " WHERE queue = ? AND available_at <= ? AND (reserved_at IS NULL OR reserved_at < ?)",
                rs -> rs.getInt(1), queueName, now, expired);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    @Override
    public void clear(String queueName) {
        jdbc.update("DELETE FROM " + table + " WHERE queue = ?", queueName);
        logger.info("[queue-db] 清空队列: {}", queueName);
    }

    // ==================== 失败队列 ====================

    @Override
    public void fail(long jobId, String queue, String payload, int attempts, String exception) {
        long now = System.currentTimeMillis();
        jdbc.update(
                "INSERT INTO " + failedTable + " (queue, payload, exception, attempts, failed_at) VALUES (?, ?, ?, ?, ?)",
                queue, payload, exception, attempts, now);
        // 从 jobs 表移除原任务
        delete(jobId);
        logger.warn("[queue-db] 任务归档到失败队列: jobId={}, queue={}, attempts={}", jobId, queue, attempts);
    }

    @Override
    public List<QueuedJob> getFailedJobs() {
        return jdbc.queryMapped(
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
        List<QueuedJob> jobs = jdbc.queryMapped(
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
        jdbc.update("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        logger.info("[queue-db] 重试失败任务: failedJobId={}, 新 jobId={}, queue={}", failedJobId, newJobId, job.getQueue());
    }

    @Override
    public void deleteFailedJob(long failedJobId) {
        int deleted = jdbc.update("DELETE FROM " + failedTable + " WHERE id = ?", failedJobId);
        if (deleted > 0) {
            logger.info("[queue-db] 删除失败任务: failedJobId={}", failedJobId);
        } else {
            logger.warn("[queue-db] 删除失败任务不存在: failedJobId={}", failedJobId);
        }
    }

    @Override
    public void clearFailedJobs() {
        jdbc.update("DELETE FROM " + failedTable);
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
        int deleted = jdbc.update("DELETE FROM " + failedTable + " WHERE failed_at < ?", threshold);
        if (deleted > 0) {
            logger.info("[queue-db] 清理过期失败任务: count={}, retentionDays={}", deleted, failedJobRetentionDays);
        }
    }

}
