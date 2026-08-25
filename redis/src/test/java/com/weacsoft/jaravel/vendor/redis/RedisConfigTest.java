package com.weacsoft.jaravel.vendor.redis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RedisConfig 纯配置对象测试（零 Spring 依赖的字段/默认值契约）。
 * 覆盖默认值、多命名连接配置、集群/哨兵模式字段。
 */
class RedisConfigTest {

    @Test
    void testDefaultValues() {
        RedisConfig props = new RedisConfig();
        assertEquals("lettuce", props.getClient(), "默认客户端应为 lettuce");
        assertNotNull(props.getOptions(), "Options 不应为 null");
        assertEquals("redis", props.getOptions().getCluster(), "默认集群模式应为 redis（单机）");
        assertEquals("", props.getOptions().getPrefix(), "默认前缀应为空字符串");
        assertNotNull(props.getConnections(), "connections 不应为 null");
        assertTrue(props.getConnections().isEmpty(), "默认 connections 应为空");
    }

    @Test
    void testConnectionConfigDefaults() {
        RedisConfig.ConnectionConfig cfg = new RedisConfig.ConnectionConfig();
        assertEquals("127.0.0.1", cfg.getHost(), "默认 host 应为 127.0.0.1");
        assertEquals(6379, cfg.getPort(), "默认 port 应为 6379");
        assertEquals("", cfg.getUsername(), "默认 username 应为空");
        assertEquals("", cfg.getPassword(), "默认 password 应为空");
        assertEquals(0, cfg.getDatabase(), "默认 database 应为 0");
        assertEquals(2000, cfg.getTimeoutMs(), "默认 timeoutMs 应为 2000");
        assertEquals("", cfg.getSentinelMaster(), "默认 sentinelMaster 应为空");
        assertEquals("", cfg.getSentinels(), "默认 sentinels 应为空");
        assertEquals("", cfg.getClusterNodes(), "默认 clusterNodes 应为空");
        assertEquals(null, cfg.getUrl(), "默认 url 应为 null");
    }

    @Test
    void testSetConnectionConfig() {
        RedisConfig.ConnectionConfig cfg = new RedisConfig.ConnectionConfig();
        cfg.setHost("192.168.1.100");
        cfg.setPort(6380);
        cfg.setUsername("admin");
        cfg.setPassword("secret");
        cfg.setDatabase(3);
        cfg.setTimeoutMs(5000);
        cfg.setUrl("redis://localhost:6379/0");

        assertEquals("192.168.1.100", cfg.getHost());
        assertEquals(6380, cfg.getPort());
        assertEquals("admin", cfg.getUsername());
        assertEquals("secret", cfg.getPassword());
        assertEquals(3, cfg.getDatabase());
        assertEquals(5000, cfg.getTimeoutMs());
        assertEquals("redis://localhost:6379/0", cfg.getUrl());
    }

    @Test
    void testClusterModeConfiguration() {
        RedisConfig props = new RedisConfig();
        props.getOptions().setCluster("cluster");
        props.getOptions().setPrefix("myapp_");

        RedisConfig.ConnectionConfig clusterCfg = new RedisConfig.ConnectionConfig();
        clusterCfg.setClusterNodes("192.168.1.1:7000,192.168.1.2:7000,192.168.1.3:7000");

        Map<String, RedisConfig.ConnectionConfig> connections = new LinkedHashMap<>();
        connections.put("default", clusterCfg);
        props.setConnections(connections);

        assertEquals("cluster", props.getOptions().getCluster());
        assertEquals("myapp_", props.getOptions().getPrefix());
        assertEquals(1, props.getConnections().size());
        assertEquals("192.168.1.1:7000,192.168.1.2:7000,192.168.1.3:7000",
                props.getConnections().get("default").getClusterNodes());
    }

    @Test
    void testSentinelModeConfiguration() {
        RedisConfig props = new RedisConfig();
        props.getOptions().setCluster("sentinel");

        RedisConfig.ConnectionConfig sentinelCfg = new RedisConfig.ConnectionConfig();
        sentinelCfg.setSentinelMaster("mymaster");
        sentinelCfg.setSentinels("127.0.0.1:26379,127.0.0.1:26380");

        Map<String, RedisConfig.ConnectionConfig> connections = new LinkedHashMap<>();
        connections.put("default", sentinelCfg);
        props.setConnections(connections);

        assertEquals("sentinel", props.getOptions().getCluster());
        assertEquals("mymaster", props.getConnections().get("default").getSentinelMaster());
        assertEquals("127.0.0.1:26379,127.0.0.1:26380",
                props.getConnections().get("default").getSentinels());
    }

    @Test
    void testMultipleNamedConnections() {
        RedisConfig props = new RedisConfig();
        Map<String, RedisConfig.ConnectionConfig> connections = new LinkedHashMap<>();

        RedisConfig.ConnectionConfig defaultCfg = new RedisConfig.ConnectionConfig();
        defaultCfg.setDatabase(0);
        connections.put("default", defaultCfg);

        RedisConfig.ConnectionConfig cacheCfg = new RedisConfig.ConnectionConfig();
        cacheCfg.setDatabase(1);
        connections.put("cache", cacheCfg);

        RedisConfig.ConnectionConfig sessionCfg = new RedisConfig.ConnectionConfig();
        sessionCfg.setDatabase(2);
        connections.put("session", sessionCfg);

        props.setConnections(connections);

        assertEquals(3, props.getConnections().size());
        assertEquals(0, props.getConnections().get("default").getDatabase());
        assertEquals(1, props.getConnections().get("cache").getDatabase());
        assertEquals(2, props.getConnections().get("session").getDatabase());
    }
}
