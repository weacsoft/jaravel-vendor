package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于关系型数据库的缓存驱动，对齐 Laravel {@code "database"} 缓存驱动。
 * <p>
 * 使用 Spring {@link JdbcTemplate} 将缓存条目持久化到 {@code jaravel_cache} 表
 * （表名可通过 {@code CacheProperties#getDatabaseTable()} 配置），缓存值以 JSON 字符串存储。
 * 构造时会自动建表（若不存在），表结构如下：
 * <pre>
 * CREATE TABLE jaravel_cache (
 *   cache_key   VARCHAR(255) NOT NULL PRIMARY KEY,   -- 缓存键
 *   cache_value TEXT,                                -- 缓存值（JSON 字符串）
 *   expires_at  BIGINT NOT NULL DEFAULT 0            -- 过期时间戳（毫秒），0=永不过期
 * );
 * </pre>
 * 自动适配 MySQL / PostgreSQL / SQLite / H2 / SQL Server 方言（建表与 upsert 语义）。
 * <p>
 * <b>TTL 单位为秒</b>（对齐 Laravel）：{@code expires_at = System.currentTimeMillis() + ttlSeconds * 1000}，
 * {@code ttlSeconds <= 0} 时 {@code expires_at = 0}（永不过期）。
 * <p>
 * 读取 / 存在性判断时会检查过期：命中已过期记录时返回未命中，并通过后台守护线程异步删除该过期记录，
 * 避免阻塞读路径。{@link JdbcTemplate} 本身线程安全，本驱动可作为单例在多线程环境共享。
 * <p>
 * 注意：由于 {@code cache_value} 以 JSON 存储，{@code Object} 反序列化时复杂对象会还原为
 * {@code LinkedHashMap} / {@code ArrayList} 等基础类型，这是 JSON 缓存的固有特性。
 */
public class DatabaseCacheDriver implements CacheDriver {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseCacheDriver.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认缓存表名 */
    private static final String DEFAULT_TABLE = "jaravel_cache";

    /** JdbcTemplate 用于数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /** 缓存表名 */
    private final String table;

    /** 数据库产品名（小写），用于方言适配 */
    private final String databaseProductName;

    /** 用于异步删除过期记录的后台执行器（守护线程，不阻塞 JVM 退出） */
    private final ExecutorService expireCleaner;

    /**
     * 构造数据库缓存驱动，使用默认表名 {@code jaravel_cache}。
     *
     * @param dataSource 数据源
     */
    public DatabaseCacheDriver(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    /**
     * 构造数据库缓存驱动。
     *
     * @param dataSource 数据源
     * @param table      缓存表名，{@code null} 或空串使用默认 {@code jaravel_cache}
     */
    public DatabaseCacheDriver(DataSource dataSource, String table) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.table = (table == null || table.isEmpty()) ? DEFAULT_TABLE : table;
        this.databaseProductName = detectProductName(dataSource);
        this.expireCleaner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jaravel-cache-db-expire-cleaner");
            t.setDaemon(true);
            return t;
        });
        createTableIfNotExists();
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        // TTL 统一为秒，expires_at 使用毫秒时间戳
        long expiresAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        String json;
        try {
            json = MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("[cache-db] 序列化缓存值失败: key={}, err={}", key, e.getMessage());
            return false;
        }
        try {
            jdbcTemplate.update(upsertSql(), key, json, expiresAt);
            return true;
        } catch (Exception e) {
            logger.warn("[cache-db] 写入缓存失败: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public Object get(String key) {
        List<Row> rows = jdbcTemplate.query(
                "SELECT " + quote("cache_value") + ", " + quote("expires_at")
                        + " FROM " + quote(table)
                        + " WHERE " + quote("cache_key") + " = ?",
                (rs, rowNum) -> new Row(rs.getString("cache_value"), rs.getLong("expires_at")),
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
        List<Long> expires = jdbcTemplate.query(
                "SELECT " + quote("expires_at")
                        + " FROM " + quote(table)
                        + " WHERE " + quote("cache_key") + " = ?",
                (rs, rowNum) -> rs.getLong("expires_at"),
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
        int affected = jdbcTemplate.update(
                "DELETE FROM " + quote(table) + " WHERE " + quote("cache_key") + " = ?", key);
        return affected > 0;
    }

    @Override
    public void removeAll() {
        jdbcTemplate.update("DELETE FROM " + quote(table));
    }

    @Override
    public Collection<String> allKeys() {
        long now = System.currentTimeMillis();
        // 顺带清理已过期记录，仅返回未过期键
        try {
            jdbcTemplate.update(
                    "DELETE FROM " + quote(table)
                            + " WHERE " + quote("expires_at") + " > 0 AND " + quote("expires_at") + " <= ?",
                    now);
        } catch (Exception e) {
            logger.debug("[cache-db] 清理过期记录失败（忽略）: {}", e.getMessage());
        }
        return jdbcTemplate.queryForList(
                "SELECT " + quote("cache_key")
                        + " FROM " + quote(table)
                        + " WHERE " + quote("expires_at") + " = 0 OR " + quote("expires_at") + " > ?",
                String.class, now);
    }

    // ==================== 内部工具方法 ====================

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
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            logger.warn("[cache-db] 反序列化缓存值失败: {}", e.getMessage());
            return null;
        }
    }

    /** 异步删除一条过期记录，避免阻塞读路径 */
    private void deleteAsync(String key) {
        expireCleaner.submit(() -> {
            try {
                jdbcTemplate.update(
                        "DELETE FROM " + quote(table) + " WHERE " + quote("cache_key") + " = ?", key);
            } catch (Exception e) {
                logger.debug("[cache-db] 异步删除过期记录失败: key={}, err={}", key, e.getMessage());
            }
        });
    }

    /** 识别数据库产品名（小写），失败回退 MySQL 方言 */
    private static String detectProductName(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            logger.warn("[cache-db] 无法识别数据库产品，使用默认 MySQL 方言: {}", e.getMessage());
            return "mysql";
        }
    }

    private boolean isMysql() {
        return databaseProductName.contains("mysql");
    }

    private boolean isPostgres() {
        return databaseProductName.contains("postgresql") || databaseProductName.contains("postgres");
    }

    private boolean isSqlite() {
        return databaseProductName.contains("sqlite");
    }

    private boolean isH2() {
        return databaseProductName.contains("h2");
    }

    private boolean isSqlServer() {
        return databaseProductName.contains("sql server") || databaseProductName.contains("microsoft");
    }

    /** 按方言对标识符加引号 */
    private String quote(String identifier) {
        if (isSqlServer()) {
            return "[" + identifier + "]";
        }
        if (isPostgres()) {
            return "\"" + identifier + "\"";
        }
        return "`" + identifier + "`";
    }

    /** 按方言返回大文本类型 */
    private String textType() {
        if (isSqlServer()) {
            return "NVARCHAR(MAX)";
        }
        if (isH2()) {
            return "CLOB";
        }
        return "TEXT";
    }

    /** 是否支持 {@code CREATE TABLE IF NOT EXISTS} 语法（SQL Server 旧版本不支持，已通过 sys.tables 预检） */
    private boolean supportsIfNotExists() {
        return !isSqlServer();
    }

    /** 自动建表（若不存在） */
    private void createTableIfNotExists() {
        try {
            if (isSqlServer()) {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys.tables WHERE name = ?", Integer.class, table);
                if (cnt != null && cnt > 0) {
                    logger.info("[cache-db] 缓存表已存在: {}", table);
                    return;
                }
            }
            String sql = "CREATE TABLE " + (supportsIfNotExists() ? "IF NOT EXISTS " : "")
                    + quote(table) + " ("
                    + quote("cache_key") + " VARCHAR(255) NOT NULL PRIMARY KEY, "
                    + quote("cache_value") + " " + textType() + ", "
                    + quote("expires_at") + " BIGINT NOT NULL DEFAULT 0"
                    + (isMysql() ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ")");
            logger.info("[cache-db] 创建缓存表: {}", sql);
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            // 并发建表或表已存在时可能抛异常，忽略
            logger.warn("[cache-db] 创建缓存表失败（可能已存在，忽略）: {}", e.getMessage());
        }
    }

    /**
     * 按方言生成 upsert SQL（MERGE / REPLACE 语义）。
     * <p>
     * 参数顺序固定为：{@code cache_key}, {@code cache_value}, {@code expires_at}。
     */
    private String upsertSql() {
        String tbl = quote(table);
        String k = quote("cache_key");
        String v = quote("cache_value");
        String e = quote("expires_at");
        if (isMysql()) {
            // MySQL: INSERT ... ON DUPLICATE KEY UPDATE
            return "INSERT INTO " + tbl + " (" + k + ", " + v + ", " + e + ") VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE " + v + " = VALUES(" + v + "), " + e + " = VALUES(" + e + ")";
        }
        if (isPostgres() || isSqlite()) {
            // PostgreSQL / SQLite: INSERT ... ON CONFLICT DO UPDATE
            return "INSERT INTO " + tbl + " (" + k + ", " + v + ", " + e + ") VALUES (?, ?, ?) "
                    + "ON CONFLICT (" + k + ") DO UPDATE SET "
                    + v + " = EXCLUDED." + v + ", " + e + " = EXCLUDED." + e;
        }
        if (isH2()) {
            // H2: MERGE ... KEY(...) VALUES (...)
            return "MERGE INTO " + tbl + " (" + k + ", " + v + ", " + e + ") KEY (" + k + ") VALUES (?, ?, ?)";
        }
        if (isSqlServer()) {
            // SQL Server: MERGE ... USING ...
            return "MERGE " + tbl + " AS t "
                    + "USING (SELECT ? AS " + k + ", ? AS " + v + ", ? AS " + e + ") AS s "
                    + "ON (t." + k + " = s." + k + ") "
                    + "WHEN MATCHED THEN UPDATE SET t." + v + " = s." + v + ", t." + e + " = s." + e + " "
                    + "WHEN NOT MATCHED THEN INSERT (" + k + ", " + v + ", " + e + ") "
                    + "VALUES (s." + k + ", s." + v + ", s." + e + ");";
        }
        // 默认按 MySQL 方言处理
        return "INSERT INTO " + tbl + " (" + k + ", " + v + ", " + e + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE " + v + " = VALUES(" + v + "), " + e + " = VALUES(" + e + ")";
    }

    /** 缓存行：{@code cacheValue} + {@code expiresAt} */
    private record Row(String cacheValue, long expiresAt) {
    }
}
