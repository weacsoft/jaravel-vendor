package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt;
import com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException;
import com.weacsoft.jaravel.vendor.wechat.server.EventMessage;
import com.weacsoft.jaravel.vendor.wechat.server.MessageParser;
import com.weacsoft.jaravel.vendor.wechat.server.ServerMessage;
import com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil;

import java.util.Collections;
import java.util.Map;

/**
 * 微信「接收消息」请求对象：静态组装 + 类型化提取（Laravel {@code Request} 风格）。
 * <p>
 * 表示一次微信服务器到公众号回调的推送，两种形态：
 * <ul>
 *   <li><b>验签请求</b>（GET）：query 携带 signature/msg_signature、timestamp、nonce、echostr</li>
 *   <li><b>消息推送</b>（POST）：query（验签参数）+ 请求体 XML（safe 模式时为 {@code <Encrypt>} 密文）</li>
 * </ul>
 *
 * <h3>能力面</h3>
 * <ul>
 *   <li><b>组装</b>：{@link #ofVerify}/{@link #ofMessage}（绑定公众号上下文：token/aes-key/消息模式）</li>
 *   <li><b>提取</b>：query 参数（timestamp/nonce/signature/msg_signature/echostr）、
 *       {@link #plainXml()}（safe 模式自动解密，惰性）、{@link #message()}（类型化消息，惰性解析）、
 *       {@link #openid()}/{@link #textContent()}/{@link #event()} 等快捷读法</li>
 *   <li><b>加解密原语</b>：{@link #crypt()}、{@link #extractEncrypt()}（供 {@link VerifySignatureMiddleware}
 *       与 {@link DecryptParseMiddleware} 使用；业务层一般不直接用）</li>
 * </ul>
 *
 * 本对象不可变（惰性缓存除外），单请求生命周期内使用，不做线程共享。
 *
 * @author weacsoft
 */
public final class WechatRequest {

    private final Map<String, String> query;
    private final String rawXml;
    private final String configName;
    private final WechatProperties.OfficialAccountConfig account;
    private final WxBizMsgCrypt crypt;
    private final boolean safeMode;
    private final boolean verify;

    private volatile String plainXml;
    private volatile ServerMessage message;

    private WechatRequest(Map<String, String> query, String rawXml, boolean verify,
                          String configName, WechatProperties.OfficialAccountConfig account,
                          WxBizMsgCrypt crypt, boolean safeMode) {
        this.query = (query == null) ? Map.of() : Collections.unmodifiableMap(query);
        this.rawXml = rawXml;
        this.verify = verify;
        this.configName = configName;
        this.account = account;
        this.crypt = crypt;
        this.safeMode = safeMode;
    }

    // ===== 静态组装 =====

    /**
     * 组装一次「验签请求」（GET echostr 场景）。
     *
     * @param query      回调 query 参数（signature/msg_signature、timestamp、nonce、echostr）
     * @param configName 公众号别名（仅用于日志/异常信息）
     * @param account    公众号配置（token/aes-key/message-mode）
     * @return 请求对象
     * @throws WechatCryptoException token/app-id 缺失时（验签必然需要）
     */
    public static WechatRequest ofVerify(Map<String, String> query,
                                         String configName,
                                         WechatProperties.OfficialAccountConfig account) {
        return new WechatRequest(query, null, true, configName, account,
                new WxBizMsgCrypt(account.getToken(), account.getAesKey(), account.getAppId()),
                safeModeOf(account));
    }

    /**
     * 组装一次「消息推送」（POST 场景）。
     *
     * @param query      回调 query 参数（safe 模式含 msg_signature/timestamp/nonce）
     * @param rawXml     推送 XML 原文（safe 模式为含 {@code <Encrypt>} 的外层 XML）
     * @param configName 公众号别名
     * @param account    公众号配置
     * @return 请求对象
     */
    public static WechatRequest ofMessage(Map<String, String> query, String rawXml,
                                          String configName,
                                          WechatProperties.OfficialAccountConfig account) {
        return new WechatRequest(query, rawXml, false, configName, account,
                new WxBizMsgCrypt(account.getToken(), account.getAesKey(), account.getAppId()),
                safeModeOf(account));
    }

    private static boolean safeModeOf(WechatProperties.OfficialAccountConfig account) {
        String mode = account.getMessageMode();
        return "safe".equalsIgnoreCase(mode);
    }

    // ===== 上下文提取 =====

    /**
     * @return query 参数表（只读视图）
     */
    public Map<String, String> query() {
        return query;
    }

    /**
     * @return 指定 query 参数，缺失时 null
     */
    public String queryParam(String key) {
        return query.get(key);
    }

    /**
     * @return 指定 query 参数，缺失时取默认值
     */
    public String queryParam(String key, String defaultValue) {
        String v = query.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    /**
     * @return 原始请求体 XML（消息推送）；验签请求时为 null
     */
    public String rawXml() {
        return rawXml;
    }

    /**
     * @return 是否验签请求（GET echostr）
     */
    public boolean isVerify() {
        return verify;
    }

    /**
     * @return 公众号别名
     */
    public String configName() {
        return configName;
    }

    /**
     * @return 公众号配置
     */
    public WechatProperties.OfficialAccountConfig account() {
        return account;
    }

    /**
     * @return 是否安全（加密）消息模式
     */
    public boolean safeMode() {
        return safeMode;
    }

    /**
     * @return 消息加解密器（token/aes-key/app-id 已绑定）
     */
    public WxBizMsgCrypt crypt() {
        return crypt;
    }

    // ===== query 常用字段提取 =====

    /**
     * @return timestamp 参数
     */
    public String timestamp() {
        return query.get("timestamp");
    }

    /**
     * @return nonce 参数
     */
    public String nonce() {
        return query.get("nonce");
    }

    /**
     * @return echostr 参数（验签请求）
     */
    public String echostr() {
        return query.get("echostr");
    }

    /**
     * @return 明文模式签名参数 signature
     */
    public String signature() {
        return query.get("signature");
    }

    /**
     * @return 安全模式签名参数 msg_signature
     */
    public String msgSignature() {
        return query.get("msg_signature");
    }

    // ===== 消息体提取（惰性） =====

    /**
     * 明文消息 XML：safe 模式自动从 {@code <Encrypt>} 解密，plain 模式为原文。
     *
     * @return 明文 XML
     * @throws WechatCryptoException safe 模式缺 {@code <Encrypt>} 或解密失败；验签请求无消息体
     * @throws IllegalStateException 验签请求（GET）调用本方法
     */
    public String plainXml() {
        if (verify) {
            throw new IllegalStateException("验签请求（GET）没有消息体");
        }
        String v = plainXml;
        if (v == null) {
            synchronized (this) {
                v = plainXml;
                if (v == null) {
                    v = safeMode ? crypt.decrypt(extractEncrypt()) : rawXml;
                    plainXml = v;
                }
            }
        }
        return v;
    }

    /**
     * 类型化消息（惰性解析）。
     *
     * @return 类型化消息（text/image/event/…）
     * @throws WechatCryptoException  解密失败
     * @throws IllegalStateException 验签请求（GET）调用本方法
     */
    public ServerMessage message() {
        if (verify) {
            throw new IllegalStateException("验签请求（GET）没有消息体");
        }
        ServerMessage m = message;
        if (m == null) {
            synchronized (this) {
                m = message;
                if (m == null) {
                    m = MessageParser.parse(plainXml());
                    message = m;
                }
            }
        }
        return m;
    }

    // ===== 类型化快捷读法（等价于 MessageParser 断言） =====

    /**
     * @return 发件人（用户）openid
     */
    public String openid() {
        return message().getFromUserName();
    }

    /**
     * @return 收件人（公众号）原始 id
     */
    public String toOpenid() {
        return message().getToUserName();
    }

    /**
     * @return 是否文本消息
     */
    public boolean isText() {
        return MessageParser.isText(message());
    }

    /**
     * @return 文本内容（非文本消息时抛 ClassCastException）
     */
    public String textContent() {
        return MessageParser.asText(message()).getContent();
    }

    /**
     * @return 是否事件消息
     */
    public boolean isEvent() {
        return MessageParser.isEvent(message());
    }

    /**
     * @return 事件名（subscribe/unsubscribe/SCAN/CLICK/VIEW…；非事件消息时抛 ClassCastException）
     */
    public String event() {
        return MessageParser.asEvent(message()).getEvent();
    }

    /**
     * @return 事件附加 Key（菜单 key 等；非事件消息时抛 ClassCastException）
     */
    public String eventKey() {
        return MessageParser.asEvent(message()).getEventKey();
    }

    /**
     * @return 是否 SCAN 事件（关注后扫码）
     */
    public boolean isScan() {
        return isEvent() && MessageParser.asEvent(message()).isScan();
    }

    // ===== 加解密原语（供内置中间件） =====

    /**
     * 提取推送 XML 的 {@code <Encrypt>} 密文（safe 模式）。
     *
     * @return Base64 密文
     * @throws WechatCryptoException 缺 {@code <Encrypt>} 节点时
     */
    public String extractEncrypt() {
        if (rawXml == null) {
            throw new WechatCryptoException("请求没有消息体，无法提取 Encrypt 节点");
        }
        Map<String, Object> nodes = XmlUtil.parseXml(rawXml);
        Object rootValue = nodes.get("xml") != null ? nodes.get("xml") : firstMapValue(nodes);
        Object encryptNode = rootValue instanceof Map<?, ?> root ? root.get("Encrypt") : null;
        if (encryptNode == null) {
            throw new WechatCryptoException("安全模式推送缺少 <Encrypt> 节点");
        }
        return String.valueOf(encryptNode).trim();
    }

    private static Object firstMapValue(Map<String, Object> nodes) {
        for (Object value : nodes.values()) {
            if (value instanceof Map<?, ?>) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "WechatRequest{config=" + configName
                + ", type=" + (verify ? "verify" : "message")
                + ", mode=" + (safeMode ? "safe" : "plain") + "}";
    }
}
