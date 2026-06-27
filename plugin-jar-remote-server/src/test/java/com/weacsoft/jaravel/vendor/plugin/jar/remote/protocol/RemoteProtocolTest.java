package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RemoteProtocol} 协议常量单元测试。
 */
class RemoteProtocolTest {

    @Test
    void magicConstantIsJrpm() {
        // "JRPM" 的十六进制即 0x4A52504D
        assertEquals(0x4A52504D, RemoteProtocol.MAGIC);
        assertEquals("JRPM", new String(new byte[]{
                (byte) ((RemoteProtocol.MAGIC >> 24) & 0xFF),
                (byte) ((RemoteProtocol.MAGIC >> 16) & 0xFF),
                (byte) ((RemoteProtocol.MAGIC >> 8) & 0xFF),
                (byte) (RemoteProtocol.MAGIC & 0xFF)
        }));
    }

    @Test
    void messageTypeConstantsAreDistinctAndOrdered() {
        // 基础消息类型
        assertEquals(1, RemoteProtocol.MSG_HANDSHAKE);
        assertEquals(2, RemoteProtocol.MSG_HANDSHAKE_ACK);
        assertEquals(3, RemoteProtocol.MSG_HEARTBEAT);
        assertEquals(4, RemoteProtocol.MSG_EXECUTE_REQUEST);
        assertEquals(5, RemoteProtocol.MSG_EXECUTE_RESPONSE);
        assertEquals(6, RemoteProtocol.MSG_ERROR);

        // 树形拓扑消息类型从 10 开始，递增且互不相同
        assertTrue(RemoteProtocol.MSG_NODE_REGISTER == 10);
        assertTrue(RemoteProtocol.MSG_NODE_REGISTER_ACK > RemoteProtocol.MSG_NODE_REGISTER);
        assertTrue(RemoteProtocol.MSG_NODE_SYNC > RemoteProtocol.MSG_NODE_REGISTER_ACK);
        assertTrue(RemoteProtocol.MSG_NODE_UNREGISTER > RemoteProtocol.MSG_NODE_SYNC);
        assertTrue(RemoteProtocol.MSG_RELAY_REQUEST > RemoteProtocol.MSG_NODE_UNREGISTER);
        assertTrue(RemoteProtocol.MSG_RELAY_RESPONSE > RemoteProtocol.MSG_RELAY_REQUEST);
    }

    @Test
    void headerLengthIsTwelveBytes() {
        // magic(4) + msgType(4) + bodyLen(4)
        assertEquals(12, RemoteProtocol.HEADER_LENGTH);
    }
}
