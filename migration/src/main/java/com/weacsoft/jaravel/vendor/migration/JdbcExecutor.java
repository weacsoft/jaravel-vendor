package com.weacsoft.jaravel.vendor.migration;

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
 * 轻量级 JDBC 执行器，替代 Spring {@code JdbcTemplate}，使 migration 模块可独立于 SpringBoot 运行。
 * <p>
 * 封装了常用的 JDBC 操作：{@code execute}（DDL）、{@code update}（DML）、
 * {@code queryForObject}（单值查询）、{@code queryForList}（列表查询）、
 * {@code queryForMapList}（多列结果集）。
 * <p>
 * 所有方法均从 {@link DataSource} 获取连接并在使用后自动关闭，无需手动管理资源。
 * 线程安全（无实例状态）。
 */
public class JdbcExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcExecutor.class);

    private final DataSource dataSource;

    /**
     * 构造 JDBC 执行器。
     *
     * @param dataSource 数据源
     */
    public JdbcExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
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
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL 更新失败: " + sql, e);
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
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
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
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
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
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
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
}
