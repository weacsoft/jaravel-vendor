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

    // ==================== 树形拓扑消息类型 ====================

    /** 消息类型：子节点注册（子节点向父节点注册自己） */
    public static final int MSG_NODE_REGISTER = 10;
    /** 消息类型：节点注册确认 */
    public static final int MSG_NODE_REGISTER_ACK = 11;
    /** 消息类型：节点树状态同步（心跳时携带子树状态） */
    public static final int MSG_NODE_SYNC = 12;
    /** 消息类型：节点注销 */
    public static final int MSG_NODE_UNREGISTER = 13;
    /** 消息类型：中继执行请求（树形转发，携带路由元数据） */
    public static final int MSG_RELAY_REQUEST = 14;
    /** 消息类型：中继执行响应 */
    public static final int MSG_RELAY_RESPONSE = 15;

    /** 帧头长度（magic + msgType + bodyLen） */
    public static final int HEADER_LENGTH = 12;
}
