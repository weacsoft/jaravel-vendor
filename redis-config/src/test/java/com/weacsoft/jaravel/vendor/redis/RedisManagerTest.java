package com.weacsoft.jaravel.vendor.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisManager 连接管理逻辑测试。
 * 不测试实际 Redis 连接，仅测试默认连接选择、连接名解析、前缀获取等纯逻辑。
 */
class RedisManagerTest {

    private RedisManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void testDefaultConnectionSelectedWhenPresent() {
        RedisProperties props = buildPropertiesWithConnections("default", "cache", "session");
        manager = new RedisManager(props);
        assertEquals("default", manager.getDefaultConnection(),
                "存在 default 连接时应选择 default 作为默认连接");
    }

    @Test
    void testDefaultConnectionFallbackToFirst() {
        RedisProperties props = buildPropertiesWithConnections("cache", "session");
        manager = new RedisManager(props);
        assertEquals("cache", manager.getDefaultConnection(),
                "不存在 default 连接时应回退到第一个连接");
    }

    @Test
    void testEmptyConnectionsThrowsOnSync() {
        RedisProperties props = new RedisProperties();
        manager = new RedisManager(props);
        assertNull(manager.getDefaultConnection(), "空连接时默认连接应为 null");
        assertThrows(IllegalStateException.class, () -> manager.sync(),
                "无连接时调用 sync 应抛出 IllegalStateException");
    }

    @Test
    void testUnknownConnectionThrows() {
        RedisProperties props = buildPropertiesWithConnections("default");
        manager = new RedisManager(props);
        assertThrows(IllegalArgumentException.class, () -> manager.sync("unknown"),
                "使用未配置的连接名应抛出 IllegalArgumentException");
    }

    @Test
    void testGetPrefix() {
        RedisProperties props = buildPropertiesWithConnections("default");
        props.getOptions().setPrefix("manage_database_");
        manager = new RedisManager(props);
        assertEquals("manage_database_", manager.getPrefix(),
                "getPrefix 应返回配置的全局键前缀");
    }

    @Test
    void testConnectionNames() {
        RedisProperties props = buildPropertiesWithConnections("default", "cache", "session");
        manager = new RedisManager(props);
        assertEquals(3, manager.connectionNames().size(), "应返回所有 3 个连接名");
        assertTrue(manager.connectionNames().contains("default"));
        assertTrue(manager.connectionNames().contains("cache"));
        assertTrue(manager.connectionNames().contains("session"));
    }

    /** 构建含指定连接名的 RedisProperties */
    private RedisProperties buildPropertiesWithConnections(String... names) {
        RedisProperties props = new RedisProperties();
        Map<String, RedisProperties.ConnectionConfig> connections = new LinkedHashMap<>();
        for (String name : names) {
            connections.put(name, new RedisProperties.ConnectionConfig());
        }
        props.setConnections(connections);
        return props;
    }
}
