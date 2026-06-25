package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 协议帧读写工具。
 * <p>
 * 负责将消息编码为二进制帧和从二进制流解码消息。
 */
public final class ProtocolCodec {

    private ProtocolCodec() {
    }

    /**
     * 读取一帧消息。
     *
     * @param in 输入流
     * @return 消息体 JSON 字符串，若流结束返回 null
     * @throws IOException 读取失败
     */
    public static String readFrame(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int magic = dis.readInt();
        if (magic != RemoteProtocol.MAGIC) {
            throw new IOException("非法魔数: 0x" + Integer.toHexString(magic));
        }
        int msgType = dis.readInt();
        int bodyLen = dis.readInt();
        if (bodyLen < 0 || bodyLen > 50 * 1024 * 1024) {
            throw new IOException("非法 body 长度: " + bodyLen);
        }
        byte[] body = new byte[bodyLen];
        dis.readFully(body);
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 读取一帧消息并返回消息类型。
     *
     * @param in 输入流
     * @return 包含消息类型和 body 的数组，[0]=msgType, [1]=body JSON
     * @throws IOException 读取失败
     */
    public static Object[] readFrameWithType(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int magic = dis.readInt();
        if (magic != RemoteProtocol.MAGIC) {
            throw new IOException("非法魔数: 0x" + Integer.toHexString(magic));
        }
        int msgType = dis.readInt();
        int bodyLen = dis.readInt();
        if (bodyLen < 0 || bodyLen > 50 * 1024 * 1024) {
            throw new IOException("非法 body 长度: " + bodyLen);
        }
        byte[] body = new byte[bodyLen];
        dis.readFully(body);
        return new Object[]{msgType, new String(body, StandardCharsets.UTF_8)};
    }

    /**
     * 编码一帧消息。
     *
     * @param msgType 消息类型
     * @param body    消息体 JSON 字符串
     * @return 编码后的字节数组
     */
    public static byte[] encodeFrame(int msgType, String body) {
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(RemoteProtocol.HEADER_LENGTH + bodyBytes.length);
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(RemoteProtocol.MAGIC);
            dos.writeInt(msgType);
            dos.writeInt(bodyBytes.length);
            dos.write(bodyBytes);
        } catch (IOException e) {
            throw new RuntimeException("编码帧失败", e);
        }
        return baos.toByteArray();
    }
}
