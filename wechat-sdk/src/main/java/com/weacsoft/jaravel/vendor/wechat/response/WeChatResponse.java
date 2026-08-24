package com.weacsoft.jaravel.vendor.wechat.response;

import com.weacsoft.jaravel.vendor.json.Json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信 API 标准响应封装。
 * <p>
 * 微信几乎所有 API 的响应都遵循以下结构（成功/失败同形）：
 * <pre>
 * { "errcode": 0, "errmsg": "ok", "msgid": "200024537", ...业务字段... }
 * </pre>
 * 其中：
 * <ul>
 *   <li>{@code errcode}/{@code errmsg} —— 业务状态。HTTP 200 不代表成功，必须看 errcode；
 *       部分查询类响应（如 {@code material/batchget}）成功时<b>不带</b> errcode 字段，
 *       此时本类将 errcode 标记为 {@link #NO_ERRCODE}（-1），仍视为成功</li>
 *   <li>{@code msgid} —— 发送类接口（客服消息/模板消息/订阅消息）的消息回执 id</li>
 * </ul>
 *
 * <h3>使用模式</h3>
 * <pre>
 * WeChatResponse resp = service.sendCustomerMessage(new Text("你好").toUser(openid));
 * if (resp.isSuccess()) {
 *     String msgId = resp.getMsgId();      // 消息回执
 * } else {
 *     log.warn("发送失败: {}", resp);       // 含 errcode/errmsg
 * }
 *
 * // 严格模式：失败即抛 {@link WechatApiException}
 * String msgId = service.sendCustomerMessage(msg).requireSuccess("sendCustomerMessage").getMsgId();
 * </pre>
 *
 * 本类为不可变对象，可安全跨线程共享。
 *
 * @author weacsoft
 */
public final class WeChatResponse {

    /** errcode 缺省标记：响应体不含 errcode 字段（部分查询接口成功时的合法形态）。 */
    public static final int NO_ERRCODE = -1;

    private final int errcode;
    private final String errmsg;
    private final String msgid;
    private final Map<String, Object> raw;

    private WeChatResponse(int errcode, String errmsg, String msgid, Map<String, Object> raw) {
        this.errcode = errcode;
        this.errmsg = errmsg;
        this.msgid = msgid;
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    /**
     * 从原始响应 Map 构建（自动提取 errcode/errmsg/msgid）。
     *
     * @param raw 原始响应体（键值可含嵌套对象），可为 null（按空响应处理）
     * @return 封装后的响应
     */
    @SuppressWarnings("unchecked")
    public static WeChatResponse of(Map<String, Object> raw) {
        if (raw == null) {
            raw = Map.of();
        }
        int errcode = NO_ERRCODE;
        Object errcodeRaw = raw.get("errcode");
        if (errcodeRaw instanceof Number number) {
            errcode = number.intValue();
        }
        String errmsg = raw.get("errmsg") != null ? String.valueOf(raw.get("errmsg")) : null;
        String msgid = raw.get("msgid") != null ? String.valueOf(raw.get("msgid")) : null;
        return new WeChatResponse(errcode, errmsg, msgid, raw);
    }

    /**
     * 判断业务是否成功：errcode 缺省（-1）或 0 均视为成功。
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return errcode == NO_ERRCODE || errcode == 0;
    }

    /**
     * @return 微信错误码，响应体不含该字段时为 {@link #NO_ERRCODE}
     */
    public int getErrcode() {
        return errcode;
    }

    /**
     * @return 微信错误描述，可空
     */
    public String getErrmsg() {
        return errmsg;
    }

    /**
     * @return 消息回执 id（发送类接口独有），可空
     */
    public String getMsgId() {
        return msgid;
    }

    /**
     * @return 原始响应体（只读视图）
     */
    public Map<String, Object> raw() {
        return raw;
    }

    /**
     * 读取业务字段（便捷入口）。
     *
     * @param key 字段名
     * @return 字段值，不存在返回 null
     */
    public Object get(String key) {
        return raw.get(key);
    }

    /**
     * 读取字符串业务字段。
     *
     * @param key 字段名
     * @return 字符串值，不存在返回 null
     */
    public String getString(String key) {
        Object value = raw.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 读取整型业务字段。
     *
     * @param key 字段名
     * @return 整型值；字段不存在或非数值时抛 {@link IllegalStateException}
     */
    public int getInt(String key) {
        Object value = raw.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("响应字段 \"" + key + "\" 不存在或非整型: " + value);
        }
        return number.intValue();
    }

    /**
     * 将响应体转换为指定类型。
     * <p>
     * 转换前剔除 {@code errcode}/{@code errmsg} 状态字段，业务字段（含嵌套对象/数组）
     * 按目标类型的属性名自动映射（依赖 json 模块的 convert 语义）。
     *
     * @param type 目标类型（需有 public 无参构造 + setter，或字段可被 Jackson 映射）
     * @param <T>  目标类型
     * @return 转换结果
     */
    public <T> T as(Class<T> type) {
        Map<String, Object> body = new LinkedHashMap<>(raw);
        body.remove("errcode");
        body.remove("errmsg");
        return Json.convert(body, type);
    }

    /**
     * 严格校验：业务失败时抛 {@link WechatApiException}，成功返回自身（便于链式取值）。
     *
     * @param operation 操作名（写入异常信息，如 "getUser"）
     * @return 本对象
     * @throws WechatApiException errcode != 0 时抛出
     */
    public WeChatResponse requireSuccess(String operation) {
        if (!isSuccess()) {
            throw new WechatApiException(operation, errcode, errmsg);
        }
        return this;
    }

    @Override
    public String toString() {
        return "WeChatResponse{errcode=" + errcode
                + (errmsg != null ? ", errmsg=" + errmsg : "")
                + (msgid != null ? ", msgid=" + msgid : "")
                + (raw.isEmpty() ? "" : ", raw=" + raw) + "}";
    }
}
