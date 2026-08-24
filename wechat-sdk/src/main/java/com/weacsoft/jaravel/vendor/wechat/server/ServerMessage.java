package com.weacsoft.jaravel.vendor.wechat.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信推送（接收）消息基类，对齐官方推送 XML 的公共字段：
 *
 * <pre>
 * &lt;xml&gt;
 *   &lt;ToUserName&gt;开发者微信号&lt;/ToUserName&gt;
 *   &lt;FromUserName&gt;openid&lt;/FromUserName&gt;
 *   &lt;CreateTime&gt;秒级时间戳&lt;/CreateTime&gt;
 *   &lt;MsgType&gt;消息类型&lt;/MsgType&gt;
 *   &lt;MsgId&gt;消息 id（64 位整型，建议用于排重）&lt;/MsgId&gt;
 *   &lt;MsgDataId&gt;仅消息来自文章时存在&lt;/MsgDataId&gt;
 *   &lt;Idx&gt;多图文时第几篇，从 1 开始&lt;/Idx&gt;
 * &lt;/xml&gt;
 * </pre>
 *
 * 注意：
 * <ul>
 *   <li>推送是<b>微信 → 开发者</b>方向：{@link #getToUserName()} 是<b>你</b>的公众号，
 *       {@link #getFromUserName()} 是<b>用户</b>的 openid（与发送侧语义相反）</li>
 *   <li>微信服务器 5 秒内收不到响应会断连并重试（共 3 次），
 *       使用 {@link #getMsgId()} 做幂等排重（官方推荐）</li>
 * </ul>
 *
 * 具体消息类：{@code TextMessage}、{@code ImageMessage}、{@code VoiceMessage}、
 * {@code VideoMessage}、{@code ShortVideoMessage}、{@code LocationMessage}、
 * {@code LinkMessage}、{@code EventMessage}、{@code SubscribeMsgSentEvent}、
 * {@code SubscribeMsgChangeEvent}。
 *
 * @author weacsoft
 */
public abstract class ServerMessage {

    /** 开发者微信号（推送方向上的"接收方"） */
    protected String toUserName;

    /** 发送方 openid（推送方向上的"发送方"，通常是普通用户） */
    protected String fromUserName;

    /** 消息创建时间（秒级时间戳） */
    protected long createTime;

    /** 消息类型（text/image/voice/video/shortvideo/location/link/event） */
    protected String msgType;

    /** 消息 id（64 位整型字符串；用于排重） */
    protected long msgId;

    /** 消息数据 id（仅消息来自文章时存在） */
    protected String msgDataId;

    /** 多图文时第几篇文章，从 1 开始 */
    protected int idx;

    /**
     * 从公共字段填充基类属性（由具体消息类的解析逻辑调用）。
     *
     * @param map 已解析的 XML 节点表
     */
    protected void fillCommon(Map<String, Object> map) {
        this.toUserName = text(map.get("ToUserName"));
        this.fromUserName = text(map.get("FromUserName"));
        this.createTime = longValue(map.get("CreateTime"), 0L);
        this.msgType = text(map.get("MsgType"));
        this.msgId = longValue(map.get("MsgId"), 0L);
        this.msgDataId = text(map.get("MsgDataId"));
        this.idx = intValue(map.get("Idx"), 1);
    }

    /**
     * 从节点表提取字符串值。
     *
     * @param value 原值
     * @return 字符串；null 时返回 null
     */
    protected static String text(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 从节点表提取 long 值。
     */
    protected static long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return defaultValue;
    }

    /**
     * 从节点表提取 int 值。
     */
    protected static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return defaultValue;
    }

    /**
     * 从节点表提取 double 值。
     */
    protected static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return defaultValue;
    }

    /**
     * 从节点表提取子节点（Map）。
     */
    protected static Map<String, Object> child(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) raw;
            return typed;
        }
        return new LinkedHashMap<>();
    }

    public String getToUserName() {
        return toUserName;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    /**
     * @return 秒级时间戳
     */
    public long getCreateTime() {
        return createTime;
    }

    public String getMsgType() {
        return msgType;
    }

    /**
     * @return 消息 id（用于排重；0 表示推送不含该字段）
     */
    public long getMsgId() {
        return msgId;
    }

    public String getMsgDataId() {
        return msgDataId;
    }

    /**
     * @return 多图文序号（从 1 开始；1 表示非多图文消息）
     */
    public int getIdx() {
        return idx;
    }
}
