package com.weacsoft.jaravel.vendor.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级 JDBC 执行器——<b>database 模块</b>的统一 SQL 执行底座，
 * 替代 Spring {@code JdbcTemplate}，使任何模块可独立于 SpringBoot 执行参数化 SQL。
 * <p>
 * <h3>为什么放在 database 模块</h3>
 * 各数据库驱动模块（cache-database / storage-database / queue-database 等）
 * 执行 CRUD SQL 时应<b>统一经由 database 模块</b>的底座完成，
 * 而不是各自维护一套 {@code Connection/PreparedStatement/ResultSet} 工具方法
 * （历史上每个驱动都复制了一份私有四件套，方言判断也各写各的）。
 * 建表等 DDL / 方言差异则由 migration 模块的 {@code Schema}/{@code Dialect}
 * （依赖 database 模块）负责，驱动模块两者组合即可，无需重复造轮子。
 * <p>
 * <h3>封装的常用操作</h3>
 * {@code execute}（DDL）、{@code update}（DML）、{@code queryForObject}（单值查询）、
 * {@code queryForList}（列表查询）、{@code queryForMapList}（多列结果集）、
 * {@code queryMapped}（自定义行映射）、{@code insertReturningKey}（自增主键返回）。
 * <p>
 * 所有方法均从 {@link DataSource} 获取连接并在使用后自动关闭，无需手动管理资源。
 * 线程安全（无实例状态）。
 *
 * @see ConnectionManager
 * @see JaravelDataSource
 */
public class JdbcExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcExecutor.class);

    private final DataSource dataSource;

    /**
     * 构造 JDBC 执行器。
     * <p>
     * 允许 {@code null} 数据源：捕获型 Schema（如 migration 模块的 {@code CapturingSchema}）
     * 只生成/记录 SQL 而不实际执行，沿用历史宽松行为。
     *
     * @param dataSource 数据源
     */
    public JdbcExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return 本执行器绑定的数据源 */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 执行 DDL 语句（CREATE TABLE、ALTER TABLE、DROP TABLE 等）。
     *
     * @param sql SQL 语句
     */
    public void execute(String sql) {
        log.debug("[jdbc] execute: {}", sql);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + sql, e);
        }
    }

    /**
     * 执行参数化 UPDATE/INSERT/DELETE 语句。
     *
     * @param sql  带 ? 占位符的 SQL
     * @param args 参数列表
     * @return 受影响的行数
     */
    public int update(String sql, Object... args) {
        log.debug("[jdbc] update: {} | args: {}", sql, args);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL 更新失败: " + sql, e);
        }
    }

    /**
     * 执行 INSERT 并返回自增主键。
     * <p>
     * 依赖 JDBC {@code RETURN_GENERATED_KEYS}，主流数据库
     * （MySQL / PostgreSQL / SQLite / H2 / SQL Server / Oracle）均支持。
     *
     * @param sql  带 ? 占位符的 INSERT 语句
     * @param args 参数列表
     * @return 自增主键；取不到时返回 -1
     */
    public long insertReturningKey(String sql, Object... args) {
        log.debug("[jdbc] insertReturningKey: {} | args: {}", sql, args);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1L;
        } catch (SQLException e) {
            throw new RuntimeException("SQL INSERT 失败: " + sql, e);
        }
    }

    /** 行映射函数：允许抛出受检的 {@link SQLException}。 */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * 查询并逐行自定义映射（驱动模块按列名取值的标准通道）。
     *
     * @param sql    带 ? 占位符的 SQL
     * @param mapper 行映射函数
     * @param args   参数列表
     * @param <T>    映射结果类型
     * @return 结果列表
     */
    public <T> List<T> queryMapped(String sql, RowMapper<T> mapper, Object... args) {
        log.debug("[jdbc] queryMapped: {} | args: {}", sql, args);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException("SQL 查询失败: " + sql, e);
        }
    }

    /**
     * 查询单个值（如 COUNT(*)、MAX(batch) 等）。
     *
     * @param sql         带 ? 占位符的 SQL
     * @param requiredType 期望的返回类型
     * @param args        参数列表
     * @param <T>         返回类型
     * @return 查询结果，无结果时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        log.debug("[jdbc] queryForObject: {} | args: {}", sql, args);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object obj = rs.getObject(1);
                    if (obj == null) {
                        return null;
                    }
                    if (requiredType == Integer.class) {
                        return (T) Integer.valueOf(((Number) obj).intValue());
                    } else if (requiredType == Long.class) {
                        return (T) Long.valueOf(((Number) obj).longValue());
                    } else if (requiredType == String.class) {
                        return (T) String.valueOf(obj);
                    }
                    return (T) obj;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL 查询失败: " + sql, e);
        }
    }

    /**
     * 查询单列值列表。
     *
     * @param sql         带 ? 占位符的 SQL
     * @param elementType 元素类型
     * @param args        参数列表
     * @param <T>         元素类型
     * @return 结果列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
        log.debug("[jdbc] queryForList: {} | args: {}", sql, args);
        List<T> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object obj = rs.getObject(1);
                    if (obj == null) {
                        result.add(null);
                    } else if (elementType == Integer.class) {
                        result.add((T) Integer.valueOf(((Number) obj).intValue()));
                    } else if (elementType == Long.class) {
                        result.add((T) Long.valueOf(((Number) obj).longValue()));
                    } else if (elementType == String.class) {
                        result.add((T) String.valueOf(obj));
                    } else {
                        result.add((T) obj);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL 查询失败: " + sql, e);
        }
        return result;
    }

    /**
     * 查询多列结果集，每行返回为 {@code Map<String, Object>}。
     *
     * @param sql  带 ? 占位符的 SQL
     * @param args 参数列表
     * @return 结果列表，每行一个 Map
     */
    public List<Map<String, Object>> queryForMapList(String sql, Object... args) {
        log.debug("[jdbc] queryForMapList: {} | args: {}", sql, args);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnLabel(i);
                        if (columnName == null || columnName.isEmpty()) {
                            columnName = meta.getColumnName(i);
                        }
                        row.put(columnName, rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL 查询失败: " + sql, e);
        }
        return result;
    }

    /** 绑定参数占位符 */
    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    // ==================== 事务（inTransaction） ====================

    /**
     * 事务回调：在同一个连接/事务内执行一组语句。
     *
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface TransactionWork<T> {
        /**
         * @param tx 事务句柄（语句都在同一连接上执行）
         * @return 结果
         * @throws Exception 抛出即回滚
         */
        T run(Tx tx) throws Exception;
    }

    /**
     * 在<b>单个事务</b>内执行一组语句：同一连接、关闭自动提交、成功提交、异常回滚。
     * <p>
     * <b>为什么必须要有它</b>：{@link #update} / {@link #queryMapped} 等方法各自从
     * {@link DataSource} 取一次连接（连接池下每次可能是<b>不同连接</b>），因此
     * 「先查后改」在多实例并发下不是原子的，`SELECT ... FOR UPDATE` 也无法生效
     * （行锁属于连接/事务）。需要跨语句原子性的场景 —— 队列抢占、缓存 add/pull、
     * 文件元信息+分片写入 —— 必须把语句放进同一个 {@link Tx}。
     * <p>
     * 用法：
     * <pre>{@code
     * QueuedJob job = jdbc.inTransaction(tx -> {
     *     List<Long> ids = tx.queryForList("SELECT id FROM jobs WHERE ... FOR UPDATE SKIP LOCKED", Long.class);
     *     if (ids.isEmpty()) return null;
     *     tx.update("UPDATE jobs SET reserved_at = ? WHERE id = ?", now, ids.get(0));
     *     return load(ids.get(0));
     * });
     * }</pre>
     *
     * @param work 事务体
     * @param <T>  返回值类型
     * @return 事务体的返回值
     */
    public <T> T inTransaction(TransactionWork<T> work) {
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = dataSource.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            T result = work.run(new Tx(conn));
            conn.commit();
            return result;
        } catch (Exception e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("事务执行失败: " + e.getMessage(), e);
        } finally {
            closeQuietly(conn, originalAutoCommit);
        }
    }

    /** 回滚（失败不掩盖原始异常） */
    private static void rollbackQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.warn("[jdbc] 事务回滚失败: {}", e.getMessage());
        }
    }

    /** 恢复自动提交并归还连接 */
    private static void closeQuietly(Connection conn, boolean originalAutoCommit) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(originalAutoCommit);
        } catch (SQLException e) {
            log.debug("[jdbc] 恢复 autoCommit 失败: {}", e.getMessage());
        }
        try {
            conn.close();
        } catch (SQLException e) {
            log.debug("[jdbc] 关闭连接失败: {}", e.getMessage());
        }
    }

    /**
     * 事务句柄：所有语句都在同一连接上执行，<b>不自行提交或回滚</b>（由
     * {@link #inTransaction(TransactionWork)} 统一负责）。
     */
    public final class Tx {

        private final Connection connection;

        private Tx(Connection connection) {
            this.connection = connection;
        }

        /**
         * @return 底层连接（需要方言特化语句时使用，如 {@code FOR UPDATE SKIP LOCKED}）
         */
        public Connection connection() {
            return connection;
        }

        /**
         * 执行 DDL。
         *
         * @param sql SQL
         */
        public void execute(String sql) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                throw new RuntimeException("SQL 执行失败: " + sql, e);
            }
        }

        /**
         * 参数化 UPDATE/INSERT/DELETE。
         *
         * @param sql  带 ? 占位符的 SQL
         * @param args 参数
         * @return 受影响行数
         */
        public int update(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("SQL 更新失败: " + sql, e);
            }
        }

        /**
         * INSERT 并返回自增主键。
         *
         * @param sql  带 ? 占位符的 SQL
         * @param args 参数
         * @return 自增主键；取不到返回 -1
         */
        public long insertReturningKey(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, args);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQL INSERT 失败: " + sql, e);
            }
        }

        /**
         * 查询并自定义行映射。
         *
         * @param sql    带 ? 占位符的 SQL
         * @param mapper 行映射
         * @param args   参数
         * @param <T>    结果类型
         * @return 结果列表
         */
        public <T> List<T> queryMapped(String sql, RowMapper<T> mapper, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                List<T> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(mapper.map(rs));
                    }
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException("SQL 查询失败: " + sql, e);
            }
        }

        /**
         * 查询单列结果列表。
         *
         * @param sql         带 ? 占位符的 SQL
         * @param elementType 元素类型
         * @param args        参数
         * @param <T>         元素类型
         * @return 结果列表
         */
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            List<T> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add((T) rs.getObject(1));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQL 查询失败: " + sql, e);
            }
            return result;
        }

        /**
         * 查询单个值。
         *
         * @param sql          带 ? 占位符的 SQL
         * @param requiredType 期望类型
         * @param args         参数
         * @param <T>          返回类型
         * @return 值；无结果返回 null
         */
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Object value = rs.getObject(1);
                    if (value == null) {
                        return null;
                    }
                    if (requiredType == Integer.class) {
                        return (T) Integer.valueOf(((Number) value).intValue());
                    } else if (requiredType == Long.class) {
                        return (T) Long.valueOf(((Number) value).longValue());
                    } else if (requiredType == String.class) {
                        return (T) String.valueOf(value);
                    }
                    return (T) value;
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQL 查询失败: " + sql, e);
            }
        }
    }
}
