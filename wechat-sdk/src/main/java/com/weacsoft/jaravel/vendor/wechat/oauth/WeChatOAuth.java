package com.weacsoft.jaravel.vendor.wechat.oauth;

import com.weacsoft.jaravel.vendor.http.session.SessionStore;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import com.weacsoft.jaravel.vendor.wechat.transport.JacksonJsonEncoder;
import com.weacsoft.jaravel.vendor.wechat.transport.WechatTransport;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公众号网页授权（OAuth 2.0，<b>非</b>小程序 jscode2session）：组装授权 URL +
 * code 换 openid/用户信息 + 会话读写辅助。
 * <p>
 * 对齐 EasyWeChat（overtrue）的 {@code OAuth} 用法：
 * <ul>
 *   <li>{@link #authorizeUrl} ⇔ PHP {@code $service->getOAuth()->scopes(...)->redirect($redirectUrl)}</li>
 *   <li>{@link #userFromCode} ⇔ PHP {@code $service->getOAuth()->userFromCode($code)}</li>
 * </ul>
 *
 * <h3>官方网页授权流程</h3>
 * <ol>
 *   <li>302 到 {@code https://open.weixin.qq.com/connect/oauth2/authorize?...#wechat_redirect}</li>
 *   <li>用户确认（或 {@code snsapi_base} 静默）后微信 302 回 {@code redirect_uri?code=CODE&state=STATE}</li>
 *   <li>用 code 换 {@code sns/oauth2/access_token} → {@code openid}（+unionid?）</li>
 *   <li>{@code snsapi_userinfo} 范围再取 {@code sns/userinfo}（昵称/头像/unionid）</li>
 * </ol>
 *
 * <h3>边界（与 Auth 的分工）</h3>
 * 本类只做「取 openid/用户 + 组装 URL + 会话读写」，<b>不注册任何 Auth guard</b>。
 * 业务侧注册 {@code wechat} guard 后从固定会话键读取 openid：
 * <pre>
 * String key   = WeChatOAuth.sessionKey("default");          // easywechat.oauth_user.default
 * WeChatOAuthUser user = WeChatOAuth.userInSession(store, "default");
 * String openid = user.getId();
 * </pre>
 *
 * <h3>CSRF/state</h3>
 * 发起跳转时用 {@link #randomState()} 生成 state 并写入 {@link #stateKey}；
 * 回调时核对 {@code query.state}，防止他人伪造 code 回调（{@link WeChatOAuthMiddleware} 自动执行）。
 *
 * <h3>线程安全</h3>
 * 本类无状态（OkHttp 线程安全），可跨线程共享。
 *
 * @author weacsoft
 */
public final class WeChatOAuth {

    private static final Logger logger = LoggerFactory.getLogger(WeChatOAuth.class);

    /** 微信网页授权入口 */
    public static final String AUTHORIZE_BASE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";

    /** 静默授权（仅 openid） */
    public static final String SCOPE_SNSAPI_BASE = "snsapi_base";
    /** 用户信息授权（openid + 昵称/头像等） */
    public static final String SCOPE_SNSAPI_USERINFO = "snsapi_userinfo";

    private final WechatProperties properties;
    private final WechatTransport transport;

    /**
     * @param properties 微信配置
     * @param transport  HTTP 传输层
     */
    public WeChatOAuth(WechatProperties properties, WechatTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    /**
     * 便捷构造（默认 JSON 编码器）。
     *
     * @param properties 微信配置
     * @param httpClient OkHttp 客户端
     */
    public WeChatOAuth(WechatProperties properties, OkHttpClient httpClient) {
        this(properties, new WechatTransport(httpClient, new JacksonJsonEncoder()));
    }

    // ===== 配置解析 =====

    private WechatProperties.OfficialAccountConfig resolveAccount(String configName) {
        String name = normalizeAccount(configName);
        WechatProperties.OfficialAccountConfig cfg = properties.getOfficialAccounts().get(name);
        if (cfg == null) {
            throw new IllegalStateException("未找到公众号配置: " + name
                    + "（@RegisterWechatOfficialAccount 或 yml 配置后重试；不会静默回退 default）");
        }
        if (cfg.getAppId() == null || cfg.getAppId().isEmpty()) {
            throw new IllegalStateException("公众号配置 \"" + name + "\" 缺少 appId（网页授权必需）");
        }
        return cfg;
    }

    private static String normalizeAccount(String account) {
        return (account == null || account.isEmpty()) ? "default" : account;
    }

    /**
     * 解析生效 scope 列表：参数覆盖 &gt; 配置 {@code oauth.scopes}（逗号分隔）&gt; {@code [snsapi_base]}。
     *
     * @param configName  公众号别名
     * @param override    路由参数/构造器指定的 scope（单值或逗号分隔），可 null
     * @return 非空 scope 列表
     */
    public List<String> resolveScopes(String configName, String override) {
        List<String> fromParam = splitScopes(override);
        if (!fromParam.isEmpty()) {
            return fromParam;
        }
        WechatProperties.OfficialAccountConfig cfg = resolveAccount(configName);
        return splitScopes(cfg.getOauth() != null ? cfg.getOauth().getScopes() : null);
    }

    /**
     * 解析配置 {@code oauth.enforce_https}（默认读取配置，缺失时 true 对齐 PHP config 段）。
     *
     * @param configName 公众号别名
     * @return 是否强制 HTTPS
     */
    public boolean enforceHttps(String configName) {
        WechatProperties.OfficialAccountConfig cfg = resolveAccount(configName);
        return cfg.getOauth() == null || cfg.getOauth().isEnforceHttps();
    }

    // ===== 授权 URL 组装 =====

    /**
     * 组装微信授权页 URL（scope 取配置默认，state 自动生成——
     * 非 Web 上下文使用本重载时需自行保管该 state）。
     *
     * @param configName  公众号别名
     * @param redirectUri 回调地址（微信 302 回该地址并附加 {@code code}/{@code state}）
     * @return 授权页 URL（以 {@code #wechat_redirect} 结尾）
     */
    public String authorizeUrl(String configName, String redirectUri) {
        return authorizeUrl(configName, redirectUri, resolveScopes(configName, null), randomState());
    }

    /**
     * 组装微信授权页 URL（显式 scope/state）。
     *
     * @param configName 公众号别名
     * @param redirectUri 回调地址（自动 URL 编码）
     * @param scopes     授权范围列表（首个作用域用于授权 URL，如 {@code snsapi_base}/{@code snsapi_userinfo}）
     * @param state      防 CSRF 随机串（可 null，退化为空 state；Web 流程应由 {@link WeChatOAuthMiddleware} 生成并保管）
     * @return 授权页 URL
     */
    public String authorizeUrl(String configName, String redirectUri, List<String> scopes, String state) {
        if (redirectUri == null || redirectUri.isEmpty()) {
            throw new IllegalArgumentException("redirectUri 不能为空");
        }
        WechatProperties.OfficialAccountConfig cfg = resolveAccount(configName);
        String scope = (scopes == null || scopes.isEmpty())
                ? SCOPE_SNSAPI_BASE
                : scopes.get(0);
        StringBuilder sb = new StringBuilder(AUTHORIZE_BASE_URL);
        sb.append('?')
                .append("appid=").append(urlEncode(cfg.getAppId()))
                .append("&redirect_uri=").append(urlEncode(redirectUri))
                .append("&response_type=code")
                .append("&scope=").append(urlEncode(scope))
                .append("&state=").append(urlEncode(state == null ? "" : state));
        sb.append("#wechat_redirect");
        return sb.toString();
    }

    // ===== code 换 openid / 用户 =====

    /**
     * code 换 OAuth 用户（scope 取配置默认；{@code snsapi_userinfo} 时附带用户信息）。
     *
     * @param configName 公众号别名
     * @param code       微信回调的 code
     * @return 用户对象（{@link WeChatOAuthUser#getId()} = openid）
     * @throws WechatApiException 微信返回 errcode（如 40029 code 无效、40163 code 已消费）
     */
    public WeChatOAuthUser userFromCode(String configName, String code) {
        return userFromCode(configName, code, resolveScopes(configName, null));
    }

    /**
     * code 换 OAuth 用户（显式 scope 列表）。
     * <p>
     * 流程：{@code sns/oauth2/access_token}（必做）→
     * 任一 scope 为 {@code snsapi_userinfo} 时追加 {@code sns/userinfo}
     * （失败不致命：记 warn 后仅以 openid 返回，对齐 PHP overtrue 的容错）。
     *
     * @param configName 公众号别名
     * @param code       微信回调的 code
     * @param scopes     授权范围列表
     * @return 用户对象
     * @throws IllegalArgumentException code 为空
     * @throws IllegalStateException 配置缺 secret
     * @throws WechatApiException 换 token 失败（微信 errcode）
     */
    public WeChatOAuthUser userFromCode(String configName, String code, List<String> scopes) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("userFromCode 需要非空 code");
        }
        WechatProperties.OfficialAccountConfig cfg = resolveAccount(configName);
        if (cfg.getSecret() == null || cfg.getSecret().isEmpty()) {
            throw new IllegalStateException("公众号配置 \"" + normalizeAccount(configName)
                    + "\" 缺少 secret（网页授权 code 换 token 必需）");
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appid", cfg.getAppId());
        query.put("secret", cfg.getSecret());
        query.put("code", code);
        query.put("grant_type", "authorization_code");
        WeChatResponse tokenResp = transport.get("sns/oauth2/access_token", query, "WeChatOAuth.userFromCode");
        tokenResp.requireSuccess("WeChatOAuth.userFromCode");
        Map<String, Object> token = tokenResp.raw();

        Map<String, Object> profile = null;
        boolean fetchProfile = scopes != null && scopes.contains(SCOPE_SNSAPI_USERINFO);
        if (fetchProfile) {
            String accessToken = asString(token.get("access_token"));
            String openid = asString(token.get("openid"));
            Map<String, String> pquery = new LinkedHashMap<>();
            pquery.put("access_token", accessToken == null ? "" : accessToken);
            pquery.put("openid", openid);
            pquery.put("lang", "zh_CN");
            try {
                WeChatResponse profileResp = transport.get("sns/userinfo", pquery, "WeChatOAuth.userinfo");
                if (profileResp.isSuccess()) {
                    profile = profileResp.raw();
                } else {
                    logger.warn("[wechat-oauth] sns/userinfo 获取失败（openid={}, errcode={}），降级为仅 openid",
                            openid, profileResp.getErrcode());
                }
            } catch (WechatApiException e) {
                logger.warn("[wechat-oauth] sns/userinfo 网络异常: {}，降级为仅 openid", e.getMessage());
            }
        }
        WeChatOAuthUser user = WeChatOAuthUser.from(token, profile);
        logger.debug("[wechat-oauth] code 交换成功: openid={}, scope={}", user.getOpenId(), user.getScope());
        return user;
    }

    // ===== 会话契约（业务 guard 读取点） =====

    /**
     * OAuth 用户会话键，EasyWeChat 兼容：{@code easywechat.oauth_user.{account}}。
     *
     * @param account 公众号别名（null/空 → default）
     * @return 会话键
     */
    public static String sessionKey(String account) {
        return "easywechat.oauth_user." + normalizeAccount(account);
    }

    /**
     * OAuth state 会话键（CSRF 防护）：{@code easywechat.oauth_state.{account}}。
     *
     * @param account 公众号别名
     * @return 会话键
     */
    public static String stateKey(String account) {
        return "easywechat.oauth_state." + normalizeAccount(account);
    }

    /**
     * 从会话读取已授权用户。
     *
     * @param store   会话存储（http 模块 {@code SessionStoreHolder} 或其实现）
     * @param account 公众号别名
     * @return 用户对象；未授权/存储为 null 时返回 null
     */
    public static WeChatOAuthUser userInSession(SessionStore store, String account) {
        if (store == null) {
            return null;
        }
        return WeChatOAuthUser.fromStored(store.get(sessionKey(account)));
    }

    /**
     * 把已授权用户写入会话（对齐 PHP {@code session($key => userFromCode($code))}）。
     *
     * @param store   会话存储（可 null —— 无会话场景时无操作）
     * @param account 公众号别名
     * @param user    用户对象
     */
    public static void saveToSession(SessionStore store, String account, WeChatOAuthUser user) {
        if (store != null) {
            store.put(sessionKey(account), user);
        }
    }

    /**
     * 清除已授权用户会话（对齐 PHP {@code session()->forget($key)}；store 可 null）。
     *
     * @param store   会话存储（可 null —— 无会话场景时无操作）
     * @param account 公众号别名
     */
    public static void clearFromSession(SessionStore store, String account) {
        if (store != null) {
            store.remove(sessionKey(account));
        }
    }

    /**
     * 生成 32 位十六进制随机 state（防 CSRF）。
     *
     * @return state 串
     */
    public static String randomState() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    // ===== 工具 =====

    /**
     * scope 字符串拆分（逗号分隔、去空白、忽略空段）；null/空 → {@code [snsapi_base]}。
     *
     * @param scopes 原始 scope 串
     * @return 非空列表
     */
    public static List<String> splitScopes(String scopes) {
        List<String> out = new ArrayList<>();
        if (scopes != null) {
            for (String s : scopes.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(SCOPE_SNSAPI_BASE);
        }
        return out;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String asString(Object v) {
        return (v != null) ? String.valueOf(v) : null;
    }
}
