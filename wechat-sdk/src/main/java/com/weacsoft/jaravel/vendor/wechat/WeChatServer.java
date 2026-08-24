package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.wechat.kernel.ResponderMiddleware;
import com.weacsoft.jaravel.vendor.wechat.kernel.WechatKernel;
import com.weacsoft.jaravel.vendor.wechat.kernel.WechatRequest;
import com.weacsoft.jaravel.vendor.wechat.kernel.WechatResponse;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.server.ServerMessage;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 微信公众号「接收消息」服务端（薄壳）：验签 → （可选）解密 → 解析 → 被动回复，
 * 全部逻辑由洋葱内核 {@link WechatKernel} 承载。
 * <p>
 * 两种消息模式（官方定义）：
 * <ul>
 *   <li><b>明文模式</b>（默认，{@code message-mode=plain}）：推送 XML 明文，回复明文 XML</li>
 *   <li><b>安全模式</b>（{@code message-mode=safe}）：推送/回复的 XML 整体 AES 加密于 {@code <Encrypt>}，
 *       以 {@code msg_signature} 验签（需配置 token + aes-key）</li>
 * </ul>
 *
 * <h3>两套 API</h3>
 * <ul>
 *   <li><b>单应答函数</b>（既有便捷 API）：{@link #handlePost(Map, String, BiFunction)}，
 *       responder 返回 {@link Message} 即被动回复，返回 {@code null} 按空串应答</li>
 *   <li><b>洋葱模型</b>（新）：{@link #kernel()} 拿 {@link WechatKernel}，
 *       {@code .middleware((req, next) -&gt; ...)} 逐层追加业务洋葱（鉴权/限频/路由/应答），
 *       {@link WechatRequest} 负责组装与提取（Laravel Request 风格），
 *       {@link WechatResponse} 负责组装与返回</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 * WeChatServer server = oaService.server("default");
 *
 * // GET：首次接入验签，返回解密后的 echostr
 * String echo = server.handleGet(queryParams);
 *
 * // POST（单应答函数）：
 * String reply = server.handlePost(queryParams, bodyXml, (msg, srv) -&gt; {
 *     if (msg instanceof com.weacsoft.jaravel.vendor.wechat.server.TextMessage) {
 *         return new com.weacsoft.jaravel.vendor.wechat.message.Text("你说了: ...");
 *     }
 *     return null;   // 不回复（微信不会对空串重试）
 * });
 *
 * // POST（洋葱模型，多层）：
 * String reply2 = server.kernel()
 *     .middleware((req, next) -&gt; req.isText() ? WechatResponse.text("echo: " + req.textContent()) : next.handle(req))
 *     .middleware((req, next) -&gt; req.isScan() ? WechatResponse.text("已关注") : next.handle(req))
 *     .handlePost(queryParams, bodyXml);
 * </pre>
 *
 * <h3>5 秒时限约定</h3>
 * 微信服务器 5 秒内收不到响应会断连并<b>重试 3 次</b>。业务处理超过 5 秒时
 * 应让洋葱层尽快返回 {@link WechatResponse#empty()}（本类将其应答为空串 {@code ""}，官方明确
 * "直接回复空串不会发起重试"），把耗时逻辑转入异步任务后用<b>客服消息</b>补发。
 *
 * @author weacsoft
 */
public class WeChatServer {

    /** 消息模式：明文 */
    public static final String MODE_PLAIN = "plain";
    /** 消息模式：安全（加密） */
    public static final String MODE_SAFE = "safe";

    private final String configName;
    private final WechatKernel kernel;

    /**
     * 构造指定公众号配置的接收消息服务端。
     *
     * @param properties 微信配置
     * @param configName 公众号别名（如 "default"）
     * @throws IllegalStateException    别名未配置、或 safe 模式缺 token/aes-key 时
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException token/app-id 缺失时
     */
    public WeChatServer(WechatProperties properties, String configName) {
        String name = (configName == null || configName.isEmpty()) ? "default" : configName;
        // 回调验签/加解密必须使用「确切别名」对应的 token/aes-key（不 fallback 到 default），
        // 否则会用 A 公众号的密钥去验 B 公众号的回调——安全上不可接受。
        WechatProperties.OfficialAccountConfig config = properties.getOfficialAccounts().get(name);
        if (config == null) {
            throw new IllegalStateException("未找到公众号配置: " + name
                    + "（@RegisterWechatOfficialAccount 或 yml 配置后重试；server 不会静默回退 default）");
        }
        this.configName = name;
        this.kernel = new WechatKernel(name, config);
    }

    /**
     * @return 当前消息模式（plain/safe）
     */
    public String getMode() {
        return kernel.isSafeMode() ? MODE_SAFE : MODE_PLAIN;
    }

    /**
     * @return 是否安全（加密）模式
     */
    public boolean isSafeMode() {
        return kernel.isSafeMode();
    }

    /**
     * @return 公众号别名
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * 洋葱内核（新 API 入口）：{@code server.kernel().middleware(...).handlePost(query, xml)}。
     *
     * @return 无业务层的基座内核；每次 {@code middleware(...)} 返回新内核，本实例不受影响
     */
    public WechatKernel kernel() {
        return kernel;
    }

    /**
     * 处理微信服务器 GET 请求（首次接入验签，返回 echostr）。
     *
     * @param query query 参数（signature/msg_signature、timestamp、nonce、echostr）
     * @return 解密后的 echostr（安全模式）或原样 echostr（明文模式）
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 签名校验失败时
     */
    public String handleGet(Map<String, String> query) {
        return kernel.handleGet(query);
    }

    /**
     * 解析一次推送（验签 + 解密 + 类型化），不做回复。
     *
     * @param query   query 参数
     * @param bodyXml 请求体 XML
     * @return 类型化消息
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 验签/解密失败
     */
    public ServerMessage parsePost(Map<String, String> query, String bodyXml) {
        return kernel.parse(query, bodyXml);
    }

    /**
     * 处理一次推送并生成被动回复（单应答函数字形）。
     * <p>
     * responder 返回 {@code null} 或无 responder 时按微信规范应答<b>空串</b>
     * （微信不会对空串重试，这是 5 秒时限下的官方推荐退避）。
     *
     * @param query     query 参数
     * @param bodyXml   请求体 XML
     * @param responder 回复逻辑（入参：解析出的消息、本服务端；返回：被动回复消息，null 表示不回复）
     * @return 应答字符串（明文 XML 或加密应答 XML；不回复时为空串）
     * @throws com.weacsoft.jaravel.vendor.wechat.crypto.WechatCryptoException 验签/解密失败，
     *                                                                         或消息类不支持被动回复
     */
    public String handlePost(Map<String, String> query, String bodyXml,
                             BiFunction<ServerMessage, WeChatServer, Message> responder) {
        if (responder == null) {
            return kernel.handlePost(query, bodyXml);
        }
        return kernel.middleware(new ResponderMiddleware(this, responder)).handlePost(query, bodyXml);
    }
}
