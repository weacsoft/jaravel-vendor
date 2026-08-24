package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发送侧消息抽象基类（客服消息 + 被动回复共享）。
 * <p>
 * 对齐 EasyWeChat 5.x {@code Kernel/Messages/Message} 的核心机制：
 * <ul>
 *   <li><b>双序列化</b>：{@link #toJsonBody()} 生成客服消息 JSON 请求体
 *       （{@code {touser, msgtype, <type>: {…}}}）；{@link #toXmlArray()} 生成被动回复 XML 节点
 *       （PascalCase，由 {@link com.weacsoft.jaravel.vendor.wechat.WeChatServer} 拼接待定头）</li>
 *   <li><b>快速失败</b>：必填字段在构造时即校验（等价于 EasyWeChat 的 requiredAttributes），
 *       避免把不完整消息发给微信后才收到 47001</li>
 *   <li><b>正交包装</b>：{@code customservice}（以客服账号发送）与 {@code aimsgcontext}
 *       （AI 消息标识）是请求体顶层字段，通过 {@link #withKfAccount}/{@link #withAiMsg} 携带，
 *       不污染消息结构本身</li>
 * </ul>
 *
 * 可发送的消息类：{@code Text}、{@code Image}、{@code Voice}、{@code Video}、{@code Music}、
 * {@code News}(+{@code NewsItem})、{@code MpNews}、{@code MpNewsArticle}、
 * {@code MenuMessage}、{@code WeChatCard}、{@code MiniProgramPage}。
 *
 * @author weacsoft
 */
public abstract class Message {

    /** 接收者 openid（touser）；可空，由服务层或 {@link #toUser} 回填。 */
    protected String to;

    /** 以指定客服账号发送（customservice.kf_account），可空。 */
    protected String kfAccount;

    /** 是否 AI 消息（aimsgcontext.is_ai_msg，0/1），可空。 */
    protected Boolean aiMsg;

    /**
     * 消息 wire 类型名（msgtype / MsgType），如 {@code text}、{@code miniprogrampage}。
     *
     * @return 类型名
     */
    public abstract String getType();

    /**
     * 消息结构体（{@code <type>} 键下的内容）。
     * <p>
     * 返回的 Map 键必须为微信官方 wire 名（下划线小写）；null 值的键将被省略。
     *
     * @return 结构体
     */
    protected abstract Map<String, Object> payload();

    /**
     * 接收者 openid（fluent）。
     *
     * @param openid 用户 openid
     * @return 本对象
     */
    public Message toUser(String openid) {
        this.to = openid;
        return this;
    }

    /**
     * @return 接收者 openid，可空
     */
    public String getUser() {
        return to;
    }

    /**
     * 以指定客服账号发送（fluent）。
     *
     * @param kfAccount 客服账号（如 {@code test1@test.com}）
     * @return 本对象
     */
    public Message withKfAccount(String kfAccount) {
        this.kfAccount = kfAccount;
        return this;
    }

    /**
     * AI 消息标识（fluent）。true 时消息下方显示灰色 wording「内容由第三方 AI 生成」。
     *
     * @param aiMsg 是否为 AI 消息
     * @return 本对象
     */
    public Message withAiMsg(boolean aiMsg) {
        this.aiMsg = aiMsg;
        return this;
    }

    /**
     * 序列化为客服消息 JSON 请求体（{@code cgi-bin/message/custom/send} 的 payload）。
     * <pre>
     * {
     *   "touser": "OPENID",
     *   "msgtype": "text",
     *   "text": {"content": "你好"},
     *   "customservice": {"kf_account": "…"},     // 可空
     *   "aimsgcontext": {"is_ai_msg": 1}          // 可空
     * }
     * </pre>
     *
     * @return 请求体（LinkedHashMap，保持键序便于调试）
     * @throws IllegalArgumentException 必填字段缺失时
     */
    public Map<String, Object> toJsonBody() {
        checkRequired();
        Map<String, Object> body = new LinkedHashMap<>();
        if (to != null && !to.isEmpty()) {
            body.put("touser", to);
        }
        body.put("msgtype", getType());
        body.put(getType(), payload());
        if (kfAccount != null && !kfAccount.isEmpty()) {
            body.put("customservice", Map.of("kf_account", kfAccount));
        }
        if (aiMsg != null) {
            body.put("aimsgcontext", Map.of("is_ai_msg", aiMsg ? 1 : 0));
        }
        return body;
    }

    /**
     * 被动回复 XML 节点（PascalCase 键，不含 ToUserName/FromUserName/CreateTime 头）。
     * <p>
     * 微信被动回复仅支持 text/image/voice/video/music/news 六种；其余消息类调用本方法
     * 将收到 {@link UnsupportedOperationException}。
     *
     * @return XML 节点表（值为 String/Number/嵌套 Map/Map 内 List）
     */
    public Map<String, Object> toXmlArray() {
        throw new UnsupportedOperationException("该消息类不支持被动回复: " + getClass().getName());
    }

    /**
     * 必填校验钩子。默认空实现（多数消息类在构造函数中已做 fail-fast 校验）。
     *
     * @throws IllegalArgumentException 必填字段缺失时
     */
    protected void checkRequired() {
        // 默认无必填项：构造函数已完成快速失败校验
    }

    /**
     * 校验字符串非空，缺失时抛带属性名的异常。
     *
     * @param value 待校验值
     * @param name  属性名（用于异常信息）
     */
    protected static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("属性 \"" + name + "\" 不能为空");
        }
    }

    /**
     * 校验对象非 null，缺失时抛带属性名的异常。
     *
     * @param value 待校验值
     * @param name  属性名（用于异常信息）
     */
    protected static void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("属性 \"" + name + "\" 不能为空");
        }
    }
}
