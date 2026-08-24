package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import com.weacsoft.jaravel.vendor.wechat.message.Image;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信「接收消息」应答对象：静态组装 + 类型化提取 + 返回（Laravel {@code Response} 风格）。
 * <p>
 * 四种应答形态（{@link Kind}）：
 * <ul>
 *   <li>{@link Kind#ECHO} —— 验签请求（GET）的回包：解密后的 echostr 原文</li>
 *   <li>{@link Kind#MESSAGE} —— 被动回复：一个类型化 {@link Message}（text/image/… 六种之内）</li>
 *   <li>{@link Kind#EMPTY} —— 不回复：微信 5 秒时限下的官方推荐退避（应答空串，微信不重试）</li>
 *   <li>{@link Kind#RAW} —— 已组装好的最终 XML（如自定义应答）</li>
 * </ul>
 *
 * <h3>组装 / 提取 示例</h3>
 * <pre>
 * WechatResponse resp = req.isText() ? WechatResponse.text("echo: " + req.textContent())
 *                                      : WechatResponse.empty();
 * // ... 由 WechatKernel.encodeMessage() 完成方向互换与 safe 模式加密/签名
 * </pre>
 *
 * 本对象不可变，可安全缓存/传递。
 *
 * @author weacsoft
 */
public final class WechatResponse {

    /** 应答形态 */
    public enum Kind {
        /** 验签回包（echostr） */
        ECHO,
        /** 被动回复（类型化消息） */
        MESSAGE,
        /** 不回复（空串退避） */
        EMPTY,
        /** 预组装的最终 XML */
        RAW
    }

    private final Kind kind;
    private final String echostr;
    private final Message message;
    private final String rawXml;

    private WechatResponse(Kind kind, String echostr, Message message, String rawXml) {
        this.kind = kind;
        this.echostr = echostr;
        this.message = message;
        this.rawXml = rawXml;
    }

    // ===== 静态组装 =====

    /**
     * 组装「验签回包」应答。
     *
     * @param echostr 回包内容（safe 模式下应为解密后明文）
     * @return 应答
     */
    public static WechatResponse echostr(String echostr) {
        return new WechatResponse(Kind.ECHO, echostr, null, null);
    }

    /**
     * 组装「被动回复」应答（任意支持的类型化消息）。
     *
     * @param message 应答消息（{@code Text}/{@code Image}/…，须支持被动回复）
     * @return 应答
     * @throws IllegalArgumentException message 为 null
     */
    public static WechatResponse message(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为 null（不回复请用 empty()）");
        }
        return new WechatResponse(Kind.MESSAGE, null, message, null);
    }

    /**
     * 组装文本被动回复。
     *
     * @param content 文本内容
     * @return 应答
     */
    public static WechatResponse text(String content) {
        return message(new Text(content));
    }

    /**
     * 组装图片被动回复。
     *
     * @param mediaId 图片素材 id
     * @return 应答
     */
    public static WechatResponse image(String mediaId) {
        return message(new Image(mediaId));
    }

    /**
     * 组装「不回复」应答（空串退避，微信不会对空串重试）。
     *
     * @return 应答
     */
    public static WechatResponse empty() {
        return new WechatResponse(Kind.EMPTY, null, null, null);
    }

    /**
     * 组装「预序列化」应答（已是最终 XML，内核不再二次编码）。
     *
     * @param xml 最终 XML
     * @return 应答
     */
    public static WechatResponse rawXml(String xml) {
        return new WechatResponse(Kind.RAW, null, null, xml);
    }

    // ===== 提取 =====

    /**
     * @return 应答形态
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return 是否验签回包
     */
    public boolean isEcho() {
        return kind == Kind.ECHO;
    }

    /**
     * @return 是否被动回复
     */
    public boolean isMessage() {
        return kind == Kind.MESSAGE;
    }

    /**
     * @return 是否「不回复」
     */
    public boolean isEmpty() {
        return kind == Kind.EMPTY;
    }

    /**
     * @return 是否预序列化
     */
    public boolean isRaw() {
        return kind == Kind.RAW;
    }

    /**
     * @return 验签回包内容（ECHO 形态）
     * @throws IllegalStateException 非 ECHO 形态
     */
    public String echostr() {
        require(Kind.ECHO);
        return echostr;
    }

    /**
     * @return 应答消息（MESSAGE 形态）
     * @throws IllegalStateException 非 MESSAGE 形态
     */
    public Message message() {
        require(Kind.MESSAGE);
        return message;
    }

    /**
     * @return 预序列化 XML（RAW 形态）
     * @throws IllegalStateException 非 RAW 形态
     */
    public String rawXml() {
        require(Kind.RAW);
        return rawXml;
    }

    private void require(Kind expected) {
        if (kind != expected) {
            throw new IllegalStateException("应答形态为 " + kind + "，不是 " + expected);
        }
    }

    // ===== 返回 =====

    /**
     * 被动回复的最终明文 XML：方向互换（ToUserName=用户，FromUserName=公众号）
     * + CreateTime + MsgType + 消息负载。
     *
     * @param userOpenid 用户 openid（收到的 FromUserName）
     * @param accountName 公众号原始 id（收到的 ToUserName）
     * @return 被动回复 XML
     * @throws IllegalStateException 非 MESSAGE 形态
     * @throws WechatCryptoException 消息类不支持被动回复（微信仅支持 text/image/voice/video/music/news）
     */
    public String toReplyXml(String userOpenid, String accountName) {
        require(Kind.MESSAGE);
        Map<String, Object> nodes = new LinkedHashMap<>();
        // 被动回复方向互换：ToUserName 是用户，FromUserName 是公众号
        nodes.put("ToUserName", userOpenid);
        nodes.put("FromUserName", accountName);
        nodes.put("CreateTime", System.currentTimeMillis() / 1000);
        nodes.put("MsgType", message.getType());
        nodes.putAll(requireXmlCapable(message));
        return XmlUtil.toXml("xml", nodes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireXmlCapable(Message out) {
        try {
            return out.toXmlArray();
        } catch (UnsupportedOperationException e) {
            throw new WechatCryptoException("消息类 " + out.getClass().getSimpleName()
                    + " 不支持被动回复（微信仅支持 text/image/voice/video/music/news）");
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            case ECHO:
                return "WechatResponse{echo=" + echostr + "}";
            case MESSAGE:
                return "WechatResponse{message=" + (message != null ? message.getType() : "?") + "}";
            case RAW:
                return "WechatResponse{raw=" + (rawXml != null ? rawXml.length() + "B" : "?") + "}";
            case EMPTY:
            default:
                return "WechatResponse{empty}";
        }
    }
}
