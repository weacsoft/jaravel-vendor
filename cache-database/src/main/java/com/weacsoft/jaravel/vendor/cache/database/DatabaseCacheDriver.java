package com.weacsoft.jaravel.vendor.cache.database;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.database.JdbcExecutor;
import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.migration.Schema;
import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于关系型数据库的缓存驱动，对齐 Laravel {@code "database"} 缓存驱动。
 * <p>
 * <b>架构对齐（0.1.3）</b>：驱动不再自带一套「JDBC 四件套 + 方言判断 + 建表 DDL」，
 * 数据库操作统一收敛到框架模块：
 * <ul>
 *   <li><b>连接与 SQL 执行</b> → database 模块（{@link JdbcExecutor}，
 *       数据源来自 {@code ConnectionManager} 注册表或业务方显式传入）；</li>
 *   <li><b>方言差异（引号 / upsert / 建表）</b> → migration 模块
 *       （{@link Dialect} + {@link DialectFactory} + {@link Schema}）。</li>
 * </ul>
 * 历史版本中驱动内置的 {@code isMysql()/quote()/upsertSql()/textType()/createTable() DDL}
 * 已移除——方言知识只存在于 migration 模块一方。
 * <p>
 * 缓存值以 JSON 字符串存储。<b>不会自动建表</b>：建表统一走迁移能力——
 * <ul>
 *   <li>{@code artisan vendor:publish --tag=migrations} 发布本模块内置迁移文件，
 *       再执行 {@code artisan migrate}（推荐，对齐 Laravel）；</li>
 *   <li>或执行 {@code artisan cache:table} 生成一份迁移文件再 {@code artisan migrate}；</li>
 *   <li>或手动调用 {@link #createTable()}（内部即经由 {@link Schema#createIfAbsent} 建表）。</li>
 * </ul>
 * 表结构如下：
 * <pre>
 * CREATE TABLE jaravel_cache (
 *   cache_key   VARCHAR(255) NOT NULL PRIMARY KEY,   -- 缓存键
 *   cache_value TEXT,                                -- 缓存值（JSON 字符串）
 *   expires_at  BIGINT NOT NULL DEFAULT 0            -- 过期时间戳（毫秒），0=永不过期
 * );
 * </pre>
 * <p>
 * <b>TTL 单位为秒</b>（对齐 Laravel）：{@code expires_at = System.currentTimeMillis() + ttlSeconds * 1000}，
 * {@code ttlSeconds <= 0} 时 {@code expires_at = 0}（永不过期）。
 * <p>
 * 读取 / 存在性判断时会检查过期：命中已过期记录时返回未命中，并通过后台守护线程异步删除该过期记录，
 * 避免阻塞读路径。{@link DataSource} 本身线程安全，本驱动可作为单例在多线程环境共享。
 * <p>
 * 注意：由于 {@code cache_value} 以 JSON 存储，{@code Object} 反序列化时复杂对象会还原为
 * {@code LinkedHashMap} / {@code ArrayList} 等基础类型，这是 JSON 缓存的固有特性。
 */
public class DatabaseCacheDriver implements CacheDriver {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseCacheDriver.class);

    /** 默认缓存表名 */
    private static final String DEFAULT_TABLE = "jaravel_cache";

    /** 缓存键列 */
    private static final String COL_KEY = "cache_key";
    /** 缓存值列 */
    private static final String COL_VALUE = "cache_value";
    /** 过期时间列 */
    private static final String COL_EXPIRES = "expires_at";

    /** 数据源（来自 database 模块连接注册表或业务方显式传入） */
    private final DataSource dataSource;

    /** database 模块 SQL 执行底座 */
    private final JdbcExecutor jdbc;

    /** 缓存表名 */
    private final String table;

    /**
     * migration 模块方言（惰性求值并缓存）。
     * <p>
     * 驱动可能在 {@code @RegisterConnection} 扫描完成之前就被创建，
     * 此刻检测方言会失败并错误地回退到 MySQL。因此推迟到第一次真正用到方言时才探测；
     * 探测失败<b>不缓存</b>，下次调用重试，临时回退 MySQL 方言。
     */
    private volatile Dialect dialect;

    /** 用于异步删除过期记录的后台执行器（守护线程，不阻塞 JVM 退出） */
    private final ExecutorService expireCleaner;

    /**
     * @return 缓存表名
     */
    public String getTable() {
        return table;
    }

    /**
     * 构造数据库缓存驱动，使用默认表名 {@code jaravel_cache}。
     *
     * @param dataSource 数据源（database 模块连接或任意 JDBC 数据源）
     */
    public DatabaseCacheDriver(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    /**
     * 构造数据库缓存驱动。
     *
     * @param dataSource 数据源（database 模块连接或任意 JDBC 数据源）
     * @param table      缓存表名，{@code null} 或空串使用默认 {@code jaravel_cache}
     */
    public DatabaseCacheDriver(DataSource dataSource, String table) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null（请通过 database 模块注册连接后使用）");
        }
        this.dataSource = dataSource;
        this.table = (table == null || table.isEmpty()) ? DEFAULT_TABLE : table;
        this.jdbc = new JdbcExecutor(dataSource);
        this.expireCleaner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jaravel-cache-db-expire-cleaner");
            t.setDaemon(true);
            return t;
        });
        // 方言不在构造期探测（连接可能尚未就绪）；也不自动建表：
        // 建表走迁移能力（vendor:publish --tag=migrations / cache:table / Schema）
    }

    // ==================== 表结构定义与建表（建表统一走 migration 能力） ====================

    /**
     * 声明缓存表结构——{@link #createTable()} 与模块内置迁移文件共用的一致定义
     * （cache_key 主键 / cache_value 文本 / expires_at 默认 0 = 永不过期）。
     *
     * @param builder migration 模块 Blueprint 构建器
     */
    public void defineTable(com.weacsoft.jaravel.vendor.migration.Blueprint builder) {
        builder.string(COL_KEY, 255).primary();
        builder.text(COL_VALUE).nullable();
        builder.bigInteger(COL_EXPIRES).defaultValue(0L);
    }

    /**
     * 创建缓存表（若不存在）——统一经由 migration 模块 {@link Schema#createIfAbsent}
     * 完成（方言感知的存在性检查 + DDL 生成），不再手拼 {@code CREATE TABLE IF NOT EXISTS}
     * （SQL Server / Oracle 不支持该语法，驱动内置 DDL 在这些库上会直接失败）。
     *
     * @return true 表示建表成功或表已存在
     */
    public boolean createTable() {
        try {
            boolean created = new Schema(dataSource).createIfAbsent(table, this::defineTable);
            if (created) {
                logger.info("[cache-db] 缓存表已创建: {}", table);
            }
            return true;
        } catch (Exception e) {
            logger.warn("[cache-db] 创建缓存表失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== CacheDriver 实现 ====================

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        // TTL 统一为秒，expires_at 使用毫秒时间戳
        long expiresAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        String json;
        try {
            json = Json.stringify(value);
        } catch (Exception e) {
            logger.warn("[cache-db] 序列化缓存值失败: key={}, err={}", key, e.getMessage());
            return false;
        }
        try {
            jdbc.update(upsertSql(), key, json, expiresAt);
            return true;
        } catch (Exception e) {
            logger.warn("[cache-db] 写入缓存失败: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public Object get(String key) {
        List<Row> rows = jdbc.queryMapped(
                "SELECT " + q(COL_VALUE) + ", " + q(COL_EXPIRES)
                        + " FROM " + q(table)
                        + " WHERE " + q(COL_KEY) + " = ?",
                rs -> new Row(rs.getString(COL_VALUE), rs.getLong(COL_EXPIRES)),
                key);
        if (rows.isEmpty()) {
            return null;
        }
        Row row = rows.get(0);
        if (isExpired(row.expiresAt())) {
            // 命中已过期记录：返回未命中，并异步删除该过期记录
            deleteAsync(key);
            return null;
        }
        return deserialize(row.cacheValue());
    }

    @Override
    public boolean exists(String key) {
        List<Long> expires = jdbc.queryMapped(
                "SELECT " + q(COL_EXPIRES)
                        + " FROM " + q(table)
                        + " WHERE " + q(COL_KEY) + " = ?",
                rs -> rs.getLong(COL_EXPIRES),
                key);
        if (expires.isEmpty()) {
            return false;
        }
        long expiresAt = expires.get(0);
        if (isExpired(expiresAt)) {
            // 命中已过期记录：返回 false，并异步删除该过期记录
            deleteAsync(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(String key) {
        return jdbc.update("DELETE FROM " + q(table) + " WHERE " + q(COL_KEY) + " = ?", key) > 0;
    }

    @Override
    public void removeAll() {
        jdbc.update("DELETE FROM " + q(table));
    }

    @Override
    public Collection<String> allKeys() {
        long now = System.currentTimeMillis();
        // 顺带清理已过期记录，仅返回未过期键
        try {
            jdbc.update(
                    "DELETE FROM " + q(table)
                            + " WHERE " + q(COL_EXPIRES) + " > 0 AND " + q(COL_EXPIRES) + " <= ?",
                    now);
        } catch (Exception e) {
            logger.debug("[cache-db] 清理过期记录失败（忽略）: {}", e.getMessage());
        }
        return jdbc.queryMapped(
                "SELECT " + q(COL_KEY)
                        + " FROM " + q(table)
                        + " WHERE " + q(COL_EXPIRES) + " = 0 OR " + q(COL_EXPIRES) + " > ?",
                rs -> rs.getString(COL_KEY),
                now);
    }

    // ==================== 内部工具 ====================

    /** 解析方言（惰性 + 缓存，失败临时回退 MySQL） */
    private Dialect dialect() {
        Dialect cached = dialect;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dialect != null) {
                return dialect;
            }
            try {
                Dialect detected = DialectFactory.detect(dataSource);
                logger.debug("[cache-db] 使用 migration 模块方言: {}", detected.getName());
                dialect = detected;
                return detected;
            } catch (Exception e) {
                // 不缓存失败结果，留待下次重试
                logger.debug("[cache-db] 暂时无法识别数据库方言，临时使用 MySQL 方言: {}", e.getMessage());
                return DialectFactory.create("mysql");
            }
        }
    }

    /** 按方言对标识符加引号（委托 migration 模块 Dialect） */
    private String q(String identifier) {
        return dialect().quote(identifier);
    }

    /** upsert SQL 由 migration 模块方言统一生成（驱动不再内置方言判断） */
    private String upsertSql() {
        String[] columns = { q(COL_KEY), q(COL_VALUE), q(COL_EXPIRES) };
        return dialect().upsertSql(q(table), columns, q(COL_KEY));
    }

    /** 是否已过期：{@code expiresAt > 0} 且当前时间已达到 / 超过过期时间 */
    private static boolean isExpired(long expiresAt) {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    /** 反序列化 JSON 字符串为 {@link Object}，失败返回 {@code null} */
    private Object deserialize(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Json.parse(json, Object.class);
        } catch (Exception e) {
            logger.warn("[cache-db] 反序列化缓存值失败: {}", e.getMessage());
            return null;
        }
    }

    /** 异步删除一条过期记录，避免阻塞读路径 */
    private void deleteAsync(String key) {
        expireCleaner.submit(() -> {
            try {
                jdbc.update("DELETE FROM " + q(table) + " WHERE " + q(COL_KEY) + " = ?", key);
            } catch (Exception e) {
                logger.debug("[cache-db] 异步删除过期记录失败: key={}, err={}", key, e.getMessage());
            }
        });
    }

    /** 缓存行：{@code cacheValue} + {@code expiresAt} */
    private record Row(String cacheValue, long expiresAt) {
    }
}
