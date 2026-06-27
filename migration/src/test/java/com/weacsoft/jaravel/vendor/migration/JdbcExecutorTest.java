package com.weacsoft.jaravel.vendor.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JdbcExecutor} 单元测试。
 * <p>
 * 使用 H2 内存数据库（MySQL 兼容模式）验证 JdbcExecutor 替代 Spring JdbcTemplate 后
 * 所有常用 JDBC 操作的正确性，确保 migration 模块可独立于 SpringBoot 运行。
 * <p>
 * 每个测试方法使用独立的表名，避免共享内存库状态污染。
 */
class JdbcExecutorTest {

    private DataSource dataSource;
    private JdbcExecutor jdbcExecutor;

    @BeforeEach
    void setUp() {
        // H2 内存数据库，MySQL 兼容模式；DB_CLOSE_DELAY=-1 保证连接关闭后库仍存活
        dataSource = new org.h2.jdbcx.JdbcDataSource();
        ((org.h2.jdbcx.JdbcDataSource) dataSource).setUrl("jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcExecutor = new JdbcExecutor(dataSource);
    }

    // ==================== testExecute ====================

    /**
     * 验证 execute 执行 DDL（CREATE TABLE）。
     * 创建表后通过写入并查询数据确认表已成功创建。
     */
    @Test
    void testExecute() {
        jdbcExecutor.execute("CREATE TABLE test_execute (id INT PRIMARY KEY, name VARCHAR(100))");

        // 表创建成功后应能正常写入与查询
        int rows = jdbcExecutor.update("INSERT INTO test_execute (id, name) VALUES (?, ?)", 1, "hello");
        assertEquals(1, rows);

        Integer count = jdbcExecutor.queryForObject("SELECT COUNT(*) FROM test_execute", Integer.class);
        assertEquals(1, count);
    }

    // ==================== testUpdate ====================

    /**
     * 验证 update 执行 INSERT/UPDATE/DELETE 并返回受影响行数。
     */
    @Test
    void testUpdate() {
        jdbcExecutor.execute("CREATE TABLE test_update (id INT PRIMARY KEY, name VARCHAR(100))");

        // INSERT
        int inserted = jdbcExecutor.update("INSERT INTO test_update (id, name) VALUES (?, ?)", 1, "Alice");
        assertEquals(1, inserted);

        int inserted2 = jdbcExecutor.update("INSERT INTO test_update (id, name) VALUES (?, ?)", 2, "Bob");
        assertEquals(1, inserted2);

        // UPDATE
        int updated = jdbcExecutor.update("UPDATE test_update SET name = ? WHERE id = ?", "Alice2", 1);
        assertEquals(1, updated);

        // 验证更新生效
        String name = jdbcExecutor.queryForObject("SELECT name FROM test_update WHERE id = ?", String.class, 1);
        assertEquals("Alice2", name);

        // DELETE
        int deleted = jdbcExecutor.update("DELETE FROM test_update WHERE id = ?", 2);
        assertEquals(1, deleted);

        Integer remaining = jdbcExecutor.queryForObject("SELECT COUNT(*) FROM test_update", Integer.class);
        assertEquals(1, remaining);
    }

    // ==================== testQueryForObject ====================

    /**
     * 验证 queryForObject 查询单值（COUNT(*)）。
     */
    @Test
    void testQueryForObject() {
        jdbcExecutor.execute("CREATE TABLE test_qfo (id INT PRIMARY KEY, name VARCHAR(100))");
        jdbcExecutor.update("INSERT INTO test_qfo (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcExecutor.update("INSERT INTO test_qfo (id, name) VALUES (?, ?)", 2, "Bob");
        jdbcExecutor.update("INSERT INTO test_qfo (id, name) VALUES (?, ?)", 3, "Charlie");

        Integer count = jdbcExecutor.queryForObject("SELECT COUNT(*) FROM test_qfo", Integer.class);
        assertNotNull(count);
        assertEquals(3, count);

        // 带参数的单值查询
        String name = jdbcExecutor.queryForObject("SELECT name FROM test_qfo WHERE id = ?", String.class, 2);
        assertEquals("Bob", name);
    }

    // ==================== testQueryForList ====================

    /**
     * 验证 queryForList 查询单列值列表。
     */
    @Test
    void testQueryForList() {
        jdbcExecutor.execute("CREATE TABLE test_qfl (id INT PRIMARY KEY, name VARCHAR(100))");
        jdbcExecutor.update("INSERT INTO test_qfl (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcExecutor.update("INSERT INTO test_qfl (id, name) VALUES (?, ?)", 2, "Bob");
        jdbcExecutor.update("INSERT INTO test_qfl (id, name) VALUES (?, ?)", 3, "Charlie");

        List<String> names = jdbcExecutor.queryForList("SELECT name FROM test_qfl ORDER BY id", String.class);
        assertEquals(Arrays.asList("Alice", "Bob", "Charlie"), names);

        // 带参数过滤
        List<String> filtered = jdbcExecutor.queryForList(
                "SELECT name FROM test_qfl WHERE id >= ? ORDER BY id", String.class, 2);
        assertEquals(Arrays.asList("Bob", "Charlie"), filtered);
    }

    // ==================== testQueryForMapList ====================

    /**
     * 验证 queryForMapList 查询多列结果集，每行返回为 Map。
     */
    @Test
    void testQueryForMapList() {
        jdbcExecutor.execute("CREATE TABLE test_qfml (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
        jdbcExecutor.update("INSERT INTO test_qfml (id, name, age) VALUES (?, ?, ?)", 1, "Alice", 20);
        jdbcExecutor.update("INSERT INTO test_qfml (id, name, age) VALUES (?, ?, ?)", 2, "Bob", 30);

        List<Map<String, Object>> rows = jdbcExecutor.queryForMapList("SELECT id, name, age FROM test_qfml ORDER BY id");
        assertEquals(2, rows.size());

        // H2 默认以大写返回列标签，使用大小写不敏感的方式取值以增强健壮性
        Map<String, Object> firstRow = rows.get(0);
        assertEquals(1, ((Number) getIgnoreCase(firstRow, "id")).intValue());
        assertEquals("Alice", getIgnoreCase(firstRow, "name"));
        assertEquals(20, ((Number) getIgnoreCase(firstRow, "age")).intValue());

        Map<String, Object> secondRow = rows.get(1);
        assertEquals(2, ((Number) getIgnoreCase(secondRow, "id")).intValue());
        assertEquals("Bob", getIgnoreCase(secondRow, "name"));
        assertEquals(30, ((Number) getIgnoreCase(secondRow, "age")).intValue());

        // 带参数查询
        List<Map<String, Object>> filtered = jdbcExecutor.queryForMapList(
                "SELECT id, name FROM test_qfml WHERE id = ?", 1);
        assertEquals(1, filtered.size());
        assertEquals("Alice", getIgnoreCase(filtered.get(0), "name"));
    }

    // ==================== testQueryForObjectNull ====================

    /**
     * 验证 queryForObject 在无结果时返回 null。
     */
    @Test
    void testQueryForObjectNull() {
        jdbcExecutor.execute("CREATE TABLE test_null (id INT PRIMARY KEY, name VARCHAR(100))");
        // 不插入任何数据，查询不存在的记录应返回 null
        String name = jdbcExecutor.queryForObject(
                "SELECT name FROM test_null WHERE id = ?", String.class, 999);
        assertNull(name);

        // 查询空表 COUNT 之外的列同样无结果
        Integer age = jdbcExecutor.queryForObject(
                "SELECT id FROM test_null WHERE id = ?", Integer.class, 1);
        assertNull(age);
    }

    // ==================== 辅助方法 ====================

    /**
     * 大小写不敏感地从 Map 中取值（H2 列标签大小写可能与 SQL 中书写的不一致）。
     */
    private static Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        throw new AssertionError("Map 中不存在键: " + key + "，实际键: " + map.keySet());
    }
}
