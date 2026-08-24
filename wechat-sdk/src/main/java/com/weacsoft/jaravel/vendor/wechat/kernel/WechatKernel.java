package com.weacsoft.jaravel.vendor.wechat.kernel;

import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.crypto.WxBizMsgCrypt;
import com.weacsoft.jaravel.vendor.wechat.server.ServerMessage;
import com.weacsoft.jaravel.vendor.wechat.xml.XmlUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微信「接收消息」洋葱内核（Kernel）：组装 {@link WechatRequest} → 洋葱层流转 → 编码 {@link WechatResponse}。
 * <p>
 * 固定洋葱顺序（先于业务层）：
 * <ol>
 *   <li>{@link VerifySignatureMiddleware} —— 验签；验签请求（GET）在此短路应答</li>
 *   <li>{@link DecryptParseMiddleware} —— 解密 + 解析类型化消息</li>
 *   <li>…业务洋葱层（{@link #middleware(WechatMiddleware)} 按注册顺序）…</li>
 *   <li>默认层 —— 不回复（空串退避）</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>
 * WechatKernel kernel = new WechatKernel("default", props.getOfficialAccounts().get("default"));
 * // 或从 WeChatServer 取: oaService.server("default").kernel()
 *
 * String reply = kernel
 *     .middleware((req, next) -&gt; req.isText() ? WechatResponse.text("echo: " + req.textContent())
 *                                             : next.handle(req))
 *     .middleware((req, next) -&gt; req.isScan() ? WechatResponse.text("已关注，我是" + req.openid())
 *                                             : next.handle(req))
 *     .handlePost(query, xml);
 * </pre>
 *
 * <h3>不可变与线程安全</h3>
 * 本类不可变：{@link #middleware} 返回<b>新实例</b>（追加一层），原实例不受影响。
 * 同一实例可跨请求、跨线程复用。
 *
 * @author weacsoft
 */
public final class WechatKernel {

    /** 固定内置洋葱层（先于业务层，顺序固定） */
    private static final List<WechatMiddleware> BUILT_IN =
            List.of(VerifySignatureMiddleware.INSTANCE, DecryptParseMiddleware.INSTANCE);

    private final String configName;
    private final WechatProperties.OfficialAccountConfig account;
    private final WxBizMsgCrypt crypt;
    private final boolean safeMode;
    private final List<WechatMiddleware> handlers;

    /**
     * 构造指定公众号配置的洋葱内核。
     *
     * @param configName 公众号别名（日志/异常信息用）
     * @param account    公众号配置（token/aes-key/app-id/message-mode），不可为 null
     * @throws IllegalStateException account 为 null，或 safe 模式缺 token/aes-key
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException token/app-id 缺失时
     */
    public WechatKernel(String configName, WechatProperties.OfficialAccountConfig account) {
        if (account == null) {
            throw new IllegalStateException("公众号配置不能为 null: " + configName);
        }
        WxBizMsgCrypt c = new WxBizMsgCrypt(account.getToken(), account.getAesKey(), account.getAppId());
        boolean safe = "safe".equalsIgnoreCase(account.getMessageMode());
        if (safe && !c.isCryptEnabled()) {
            throw new IllegalStateException("公众号 " + configName + " 配置了 safe 消息模式，但缺少 token 或 aes-key");
        }
        this.configName = configName;
        this.account = account;
        this.crypt = c;
        this.safeMode = safe;
        this.handlers = Collections.emptyList();
    }

    private WechatKernel(String configName,
                         WechatProperties.OfficialAccountConfig account,
                         WxBizMsgCrypt crypt,
                         boolean safeMode,
                         List<WechatMiddleware> handlers) {
        this.configName = configName;
        this.account = account;
        this.crypt = crypt;
        this.safeMode = safeMode;
        this.handlers = handlers;
    }

    /**
     * 追加一层业务洋葱（返回新内核，不修改本实例）。
     *
     * @param mw 洋葱层
     * @return 新内核（业务层按注册顺序排布，先注册者更靠近内置层）
     */
    public WechatKernel middleware(WechatMiddleware mw) {
        List<WechatMiddleware> next = new ArrayList<>(handlers);
        next.add(mw);
        return new WechatKernel(configName, account, crypt, safeMode, List.copyOf(next));
    }

    // ===== 应答入口 =====

    /**
     * 处理验签请求（GET）。
     *
     * @param query 回调 query 参数
     * @return 解密后的 echostr（safe 模式）或原样 echostr（plain 模式）
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 验签失败
     */
    public String handleGet(Map<String, String> query) {
        WechatRequest req = WechatRequest.ofVerify(query, configName, account);
        WechatResponse resp = dispatch(req);
        if (resp.isEcho()) {
            return resp.echostr();
        }
        if (resp.isEmpty()) {
            return "";
        }
        throw new IllegalStateException("验签流程只允许 echostr/空应答，实际得到 " + resp.kind());
    }

    /**
     * 处理消息推送（POST）并编码最终应答。
     *
     * @param query  回调 query 参数
     * @param rawXml 推送 XML 原文（safe 模式含 {@code <Encrypt>}）
     * @return 应答字符串：明文 XML / 加密应答 XML / 不回复时的空串
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 验签/解密/消息类不支持被动回复
     */
    public String handlePost(Map<String, String> query, String rawXml) {
        WechatRequest req = WechatRequest.ofMessage(query, rawXml, configName, account);
        WechatResponse resp = dispatch(req);
        if (resp.isEmpty()) {
            return "";
        }
        if (resp.isRaw()) {
            return resp.rawXml();
        }
        if (resp.isEcho()) {
            return resp.echostr();
        }
        String replyXml = resp.toReplyXml(req.openid(), req.toOpenid());
        if (!safeMode) {
            return replyXml;
        }
        return buildSafeReply(replyXml);
    }

    /**
     * 只推送不回复：验签 + 解密 + 解析（等价于旧 {@code WeChatServer.parsePost}）。
     *
     * @param query  query 参数
     * @param rawXml 推送 XML 原文
     * @return 类型化消息
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 验签/解密失败
     */
    public ServerMessage parse(Map<String, String> query, String rawXml) {
        WechatRequest req = WechatRequest.ofMessage(query, rawXml, configName, account);
        dispatch(req);
        return req.message();
    }

    // ===== 配置读取 =====

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
     * @return 是否安全（加密）模式
     */
    public boolean isSafeMode() {
        return safeMode;
    }

    // ===== 内部 =====

    private WechatResponse dispatch(WechatRequest req) {
        List<WechatMiddleware> chain = new ArrayList<>(BUILT_IN);
        chain.addAll(handlers);
        // 逆序折叠：默认应答在最内层，先注册的洋葱层更靠外。
        // 每一层 composite 自带「后续链条」，最外层 call 时传入的 next 参数被忽略。
        WechatMiddleware current = (r, next) -> WechatResponse.empty();
        for (int i = chain.size() - 1; i >= 0; i--) {
            final WechatMiddleware mw = chain.get(i);
            final WechatMiddleware inner = current;
            current = (r, nx) -> mw.handle(r, rr -> inner.handle(rr, null));
        }
        return current.handle(req, null);
    }

    private String buildSafeReply(String replyXml) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        String encrypted = crypt.encrypt(replyXml);
        String msgSignature = crypt.sign(timestamp, nonce, encrypted);
        Map<String, Object> nodes = new LinkedHashMap<>();
        nodes.put("Encrypt", encrypted);
        nodes.put("MsgSignature", msgSignature);
        nodes.put("TimeStamp", timestamp);
        nodes.put("Nonce", nonce);
        return XmlUtil.toXml("xml", nodes);
    }
}
