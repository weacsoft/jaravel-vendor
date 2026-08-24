package com.weacsoft.jaravel.vendor.cache.database;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatabaseCacheDriver} 原生 JDBC 缓存读写单元测试（基于 H2 内存库）。
 * <p>
 * 覆盖：createTable 建表、put/get 往返、upsert 覆盖、TTL 过期、exists/remove/removeAll/allKeys、
 * 工厂别名解析与缺失数据源报错。全程不依赖 spring-jdbc——验证数据库驱动直连 database/原生 JDBC 的路径。
 */
class DatabaseCacheDriverTest {

    private static DataSource dataSource;
    private DatabaseCacheDriver driver;

    @BeforeAll
    static void initDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cachetests;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @BeforeEach
    void setUp() {
        driver = new DatabaseCacheDriver(dataSource);
        // 清表后重建，保证每个用例从空表开始
        try {
            driver.removeAll();
        } catch (Exception ignored) {
            // 表尚不存在
        }
        assertTrue(driver.createTable(), "createTable 应在 H2 上成功");
    }

    @Test
    void testPutGetRoundTrip() {
        assertTrue(driver.put("greeting", "hello", 600));
        Object v = driver.get("greeting");
        assertEquals("hello", v);
    }

    @Test
    void testPutGetMapValue() {
        Map<String, Object> value = Map.of("name", "jaravel", "count", 3);
        assertTrue(driver.put("profile", value, 600));
        Object v = driver.get("profile");
        // JSON 反序列化还原为基础集合类型，内容一致
        assertEquals(value, v);
    }

    @Test
    void testUpsertOverwritesSameKey() {
        assertTrue(driver.put("k", "v1", 600));
        assertTrue(driver.put("k", "v2", 600));
        assertEquals("v2", driver.get("k"));
        // upsert 不应产生重复行
        Collection<String> keys = driver.allKeys();
        assertEquals(1, keys.size());
        assertTrue(keys.contains("k"));
    }

    @Test
    void testZeroTtlMeansNeverExpires() {
        assertTrue(driver.put("permanent", "forever", 0));
        assertEquals("forever", driver.get("permanent"));
        assertTrue(driver.exists("permanent"));
    }

    @Test
    void testExpiredEntryReadsAsMiss() throws InterruptedException {
        assertTrue(driver.put("short", "gone", 1));
        assertEquals("gone", driver.get("short"));
        // 等待 TTL（1 秒）过期
        Thread.sleep(1200);
        assertNull(driver.get("short"));
        assertFalse(driver.exists("short"));
    }

    @Test
    void testExistsMiss() {
        assertFalse(driver.exists("absent"));
        assertNull(driver.get("absent"));
    }

    @Test
    void testRemove() {
        assertTrue(driver.put("r", "x", 600));
        assertTrue(driver.remove("r"));
        assertFalse(driver.exists("r"));
        // 再次删除不存在的键返回 false
        assertFalse(driver.remove("r"));
    }

    @Test
    void testAllKeysOnlyReturnsLiveEntries() throws InterruptedException {
        assertTrue(driver.put("live1", "a", 600));
        assertTrue(driver.put("live2", "b", 600));
        assertTrue(driver.put("dead", "c", 1));
        Thread.sleep(1200);

        Collection<String> keys = driver.allKeys();
        assertTrue(keys.contains("live1"));
        assertTrue(keys.contains("live2"));
        assertFalse(keys.contains("dead"));
        assertEquals(2, keys.size());
    }

    @Test
    void testRemoveAll() {
        assertTrue(driver.put("a", "1", 600));
        assertTrue(driver.put("b", "2", 600));
        driver.removeAll();
        assertTrue(driver.allKeys().isEmpty());
    }

    @Test
    void testCustomTable() {
        DatabaseCacheDriver custom = new DatabaseCacheDriver(dataSource, "my_cache_table");
        assertEquals("my_cache_table", custom.getTable());
        assertTrue(custom.createTable());
        assertTrue(custom.put("ct", "val", 600));
        assertEquals("val", custom.get("ct"));
    }

    // ==================== 工厂 ====================

    @Test
    void testFactorySupportsDatabaseDriver() {
        DatabaseCacheDriverFactory factory = new DatabaseCacheDriverFactory(dataSource);
        assertTrue(factory.support("database"));
        assertTrue(factory.support("Database"));
        assertFalse(factory.support("redis"));
    }

    @Test
    void testFactoryCreateUsesConfiguredTable() {
        DatabaseCacheDriverFactory factory = new DatabaseCacheDriverFactory(dataSource);
        Object driver = factory.create(Map.of("table", "factory_table"));
        assertTrue(driver instanceof DatabaseCacheDriver);
        assertEquals("factory_table", ((DatabaseCacheDriver) driver).getTable());
    }

    @Test
    void testFactoryCreateDefaultTable() {
        DatabaseCacheDriverFactory factory = new DatabaseCacheDriverFactory(dataSource);
        Object driver = factory.create(Map.of());
        assertEquals("jaravel_cache", ((DatabaseCacheDriver) driver).getTable());
    }

    @Test
    void testFactoryMissingDataSourceFailsWithActionableMessage() {
        DatabaseCacheDriverFactory factory = new DatabaseCacheDriverFactory(() -> null);
        try {
            factory.create(Map.of());
            throw new AssertionError("应当抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("数据库"), "错误信息应提示数据库连接缺失: " + e.getMessage());
        }
    }
}
