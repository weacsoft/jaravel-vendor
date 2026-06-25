package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

/**
 * 远程插件执行协议常量。
 * <p>
 * 帧格式（二进制，大端序）：
 * <pre>
 * +----------+----------+----------+----------+
 * | magic    | msgType  | bodyLen  | body     |
 * | 4 bytes  | 4 bytes  | 4 bytes  | N bytes  |
 * +----------+----------+----------+----------+
 * </pre>
 * <ul>
 *   <li>magic: 固定 0x4A52504D ("JRPM" = Jaravel Remote Plugin Protocol)</li>
 *   <li>msgType: 消息类型，见 {@link MessageType}</li>
 *   <li>bodyLen: body 字节数（JSON 编码的 UTF-8 字节）</li>
 *   <li>body: JSON 消息体</li>
 * </ul>
 */
public final class RemoteProtocol {

    private RemoteProtocol() {
    }

    /** 魔数 */
    public static final int MAGIC = 0x4A52504D;

    /** 消息类型：握手 */
    public static final int MSG_HANDSHAKE = 1;
    /** 消息类型：握手确认 */
    public static final int MSG_HANDSHAKE_ACK = 2;
    /** 消息类型：心跳 */
    public static final int MSG_HEARTBEAT = 3;
    /** 消息类型：执行请求 */
    public static final int MSG_EXECUTE_REQUEST = 4;
    /** 消息类型：执行响应 */
    public static final int MSG_EXECUTE_RESPONSE = 5;
    /** 消息类型：错误 */
    public static final int MSG_ERROR = 6;

    /** 帧头长度（magic + msgType + bodyLen） */
    public static final int HEADER_LENGTH = 12;
}
