package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RemoteServerProperties} 配置属性默认值与字段访问单元测试。
 */
class RemoteServerPropertiesTest {

    @Test
    void defaultsMatchDocumentation() {
        RemoteServerProperties props = new RemoteServerProperties();

        assertFalse(props.isEnabled(), "默认未启用 TCP 服务端");
        assertEquals(9700, props.getPort());
        assertNull(props.getAuthToken(), "默认不认证");
        assertNull(props.getNodeId(), "节点 ID 默认为 null（自动生成）");
        assertFalse(props.isRelayEnabled(), "默认不启用中继转发（向后兼容）");
        assertEquals(5, props.getMaxHops());
    }

    @Test
    void enableAndPortAreMutable() {
        RemoteServerProperties props = new RemoteServerProperties();
        props.setEnabled(true);
        props.setPort(12345);

        assertTrue(props.isEnabled());
        assertEquals(12345, props.getPort());
    }

    @Test
    void treeRelayFieldsAreMutable() {
        RemoteServerProperties props = new RemoteServerProperties();
        props.setNodeId("node-root");
        props.setRelayEnabled(true);
        props.setMaxHops(8);
        props.setAuthToken("secret-token");

        assertEquals("node-root", props.getNodeId());
        assertTrue(props.isRelayEnabled());
        assertEquals(8, props.getMaxHops());
        assertEquals("secret-token", props.getAuthToken());
    }

    @Test
    void maxHopsCanBeZeroOrNegative() {
        RemoteServerProperties props = new RemoteServerProperties();
        props.setMaxHops(0);
        assertEquals(0, props.getMaxHops());
    }
}
