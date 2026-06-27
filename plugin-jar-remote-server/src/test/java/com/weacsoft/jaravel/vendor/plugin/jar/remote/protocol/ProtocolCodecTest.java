package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProtocolCodec} 编解码单元测试。
 */
class ProtocolCodecTest {

    @Test
    void encodeFrameProducesCorrectBinaryLayout() {
        byte[] frame = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_EXECUTE_REQUEST, "{\"id\":1}");

        // 帧头 12 字节 + body 字节数
        assertEquals(RemoteProtocol.HEADER_LENGTH + "{\"id\":1}".length(), frame.length);

        // magic（大端序）
        assertEquals(RemoteProtocol.MAGIC,
                ((frame[0] & 0xFF) << 24) | ((frame[1] & 0xFF) << 16)
                        | ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF));
        // msgType
        assertEquals(RemoteProtocol.MSG_EXECUTE_REQUEST,
                ((frame[4] & 0xFF) << 24) | ((frame[5] & 0xFF) << 16)
                        | ((frame[6] & 0xFF) << 8) | (frame[7] & 0xFF));
        // bodyLen
        int bodyLen = ((frame[8] & 0xFF) << 24) | ((frame[9] & 0xFF) << 16)
                | ((frame[10] & 0xFF) << 8) | (frame[11] & 0xFF);
        assertEquals("{\"id\":1}".length(), bodyLen);
    }

    @Test
    void encodeFrameWithNullBodyProducesZeroLengthBody() {
        byte[] frame = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HEARTBEAT, null);
        assertEquals(RemoteProtocol.HEADER_LENGTH, frame.length);
        // bodyLen == 0
        int bodyLen = ((frame[8] & 0xFF) << 24) | ((frame[9] & 0xFF) << 16)
                | ((frame[10] & 0xFF) << 8) | (frame[11] & 0xFF);
        assertEquals(0, bodyLen);
    }

    @Test
    void readFrameDecodesBodyString() throws IOException {
        String body = "{\"hello\":\"world\"}";
        byte[] frame = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_EXECUTE_RESPONSE, body);

        try (InputStream in = new ByteArrayInputStream(frame)) {
            String decoded = ProtocolCodec.readFrame(in);
            assertEquals(body, decoded);
        }
    }

    @Test
    void readFrameWithTypeReturnsTypeAndBody() throws IOException {
        String body = "ping";
        byte[] frame = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_HEARTBEAT, body);

        try (InputStream in = new ByteArrayInputStream(frame)) {
            Object[] result = ProtocolCodec.readFrameWithType(in);
            assertEquals(RemoteProtocol.MSG_HEARTBEAT, result[0]);
            assertEquals(body, result[1]);
        }
    }

    @Test
    void readFrameRejectsIllegalMagic() throws IOException {
        // 构造一个魔数错误的帧
        byte[] bad = new byte[RemoteProtocol.HEADER_LENGTH];
        bad[0] = 0; bad[1] = 0; bad[2] = 0; bad[3] = 1; // 错误魔数
        bad[4] = 0; bad[5] = 0; bad[6] = 0; bad[7] = RemoteProtocol.MSG_HANDSHAKE;
        bad[8] = 0; bad[9] = 0; bad[10] = 0; bad[11] = 0;

        try (InputStream in = new ByteArrayInputStream(bad)) {
            IOException ex = assertThrows(IOException.class, () -> ProtocolCodec.readFrame(in));
            assertTrue(ex.getMessage().contains("魔数"));
        }
    }

    @Test
    void encodeAndDecodeAreRoundTrippableForUtf8() throws IOException {
        String body = "中文测试-emoji-🚀-表单";
        byte[] frame = ProtocolCodec.encodeFrame(RemoteProtocol.MSG_ERROR, body);

        try (InputStream in = new ByteArrayInputStream(frame)) {
            assertEquals(body, ProtocolCodec.readFrame(in));
        }
        // 确认 body 字节采用 UTF-8
        assertArrayEquals(body.getBytes(StandardCharsets.UTF_8),
                java.util.Arrays.copyOfRange(frame, RemoteProtocol.HEADER_LENGTH, frame.length));
    }
}
