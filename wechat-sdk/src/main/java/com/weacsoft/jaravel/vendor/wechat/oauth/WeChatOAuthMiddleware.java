package com.weacsoft.jaravel.vendor.wechat.oauth;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.http.middleware.Middleware;
import com.weacsoft.jaravel.vendor.http.session.SessionStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 公众号网页授权中间件（<b>自动重定向</b>），对齐 overtrue {@code OAuthAuthenticate}：
 * <ol>
 *   <li>会话已有 {@code easywechat.oauth_user.{account}} → 放行（{@code next}），业务侧 guard 可直接取 openid</li>
 *   <li>微信回调（带 {@code ?code=}；state 已记录时核对，防 CSRF）→ 换 openid/用户、
 *       写入会话、302 回原地址（剔除 {@code code}/{@code state}，保留其余 query）</li>
 *   <li>否则 → 清除该账号的旧会话值、生成 state 存会话、302 微信授权页</li>
 * </ol>
 *
 * <h3>路由使用</h3>
 * <pre>
 * // default 账号（alias 由 WechatAutoConfiguration 注册）
 * router.get("/weapp", handler).middleware("wechat.oauth");
 *
 * // 指定账号 + scope（对齐 PHP Laravel: wechat.auth:default,snsapi_userinfo）
 * router.get("/sns", handler).middleware("wechat.oauth:default,snsapi_userinfo");
 *
 * // 或程序式
 * router.get("/weapp", handler).middleware(new WeChatOAuthMiddleware(oauthService, "default", null, sessionStoreHolder));
 * </pre>
 *
 * <h3>业务侧衔接（用户自建的 wechat guard）</h3>
 * 放行后的 handler/guard 从会话取 openid：
 * <pre>
 * WeChatOAuthUser user = WeChatOAuth.userInSession(store, "default");
 * String openid = user.getId();   // 映射业务用户 → Auth.guard("web").login(...)
 * </pre>
 * 会话键与 overtrue/EasyWeChat 完全一致（{@code easywechat.oauth_user.{account}}），
 * PHP 侧 {@code session('easywechat.oauth_user')} 的同构键直接兼容。
 *
 * <h3>参数解析（别名表达式）</h3>
 * 优先取 {@code params}（来自别名表达式 {@code wechat.oauth:account[,scope]}），
 * 其次取构造器指定值，都无则 {@code account="default"}、scope 取配置
 * {@code oauth.scopes}（默认 {@code snsapi_base}）。
 *
 * @author weacsoft
 */
public class WeChatOAuthMiddleware implements Middleware {

    private final WeChatOAuth oauth;
    private final SessionStore sessionStore;
    private final String account;
    private final String scope;

    /**
     * @param oauth 网页授权服务（取 URL 组装 + code 交换，需配置好公众号）
     */
    public WeChatOAuthMiddleware(WeChatOAuth oauth) {
        this(oauth, null, null, null);
    }

    /**
     * @param oauth   网页授权服务
     * @param account 默认公众号别名（可被路由参数覆盖）
     */
    public WeChatOAuthMiddleware(WeChatOAuth oauth, String account) {
        this(oauth, account, null, null);
    }

    /**
     * @param oauth   网页授权服务
     * @param account 默认公众号别名
     * @param scope   默认 scope（可 null → 配置默认）
     */
    public WeChatOAuthMiddleware(WeChatOAuth oauth, String account, String scope) {
        this(oauth, account, scope, null);
    }

    /**
     * @param oauth        网页授权服务
     * @param account      默认公众号别名（可 null）
     * @param scope        默认 scope（可 null → 配置默认）
     * @param sessionStore 会话存储（可 null → 惰性回退 http 模块默认；
     *                     建议传 {@code SessionStoreHolder}，与业务 guard 同一存储实例）
     */
    public WeChatOAuthMiddleware(WeChatOAuth oauth, String account, String scope, SessionStore sessionStore) {
        if (oauth == null) {
            throw new IllegalArgumentException("oauth 服务不能为 null");
        }
        this.oauth = oauth;
        this.account = account;
        this.scope = scope;
        this.sessionStore = sessionStore;
    }

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        String account = (params != null && params.length > 0 && !params[0].isEmpty())
                ? params[0]
                : (this.account != null ? this.account : "default");
        String scope = (params != null && params.length > 1 && !params[1].isEmpty())
                ? params[1]
                : this.scope;
        SessionStore store = this.sessionStore;
        List<String> scopes = oauth.resolveScopes(account, scope);

        // 1) 已授权 → 放行（业务 guard 从会话取 openid）
        if (WeChatOAuth.userInSession(store, account) != null) {
            return next.apply(request);
        }

        boolean enforceHttps = oauth.enforceHttps(account);

        // 2) 微信回调（带 code）→ 换用户、存会话、回原地址
        String code = request.query("code");
        if (code != null && !code.isEmpty()) {
            String expectedState = (store != null) ? asString(store.get(WeChatOAuth.stateKey(account))) : null;
            String givenState = request.query("state");
            if (expectedState != null && !expectedState.equals(givenState)) {
                if (store != null) {
                    store.remove(WeChatOAuth.stateKey(account));
                }
                return ResponseBuilder.forbidden("OAuth state 校验失败（疑似 CSRF，请重新发起授权）");
            }
            WeChatOAuthUser user = oauth.userFromCode(account, code, scopes);
            WeChatOAuth.saveToSession(store, account, user);
            if (store != null) {
                store.remove(WeChatOAuth.stateKey(account));
            }
            return ResponseBuilder.redirect(intendedUrl(request, enforceHttps));
        }

        // 3) 发起授权：清旧值 → 记 state → 302 微信授权页
        WeChatOAuth.clearFromSession(store, account);
        String state = WeChatOAuth.randomState();
        if (store != null) {
            store.put(WeChatOAuth.stateKey(account), state);
        }
        String redirectUri = request.fullUrl();
        if (enforceHttps && redirectUri.startsWith("http://")) {
            redirectUri = "https://" + redirectUri.substring("http://".length());
        }
        return ResponseBuilder.redirect(oauth.authorizeUrl(account, redirectUri, scopes, state));
    }

    /**
     * 目标回跳地址：当前完整 URL 剔除 {@code code}/{@code state}（其余 query 保留），
     * 对齐 overtrue 的 {@code getIntendUrl}（必要时升级 https）。
     *
     * @param request      Web 请求
     * @param https        是否强制 HTTPS
     * @return 回跳 URL
     */
    public static String intendedUrl(Request request, boolean https) {
        String full = request.fullUrl();
        String base = full;
        int q = full.indexOf('?');
        if (q >= 0) {
            base = full.substring(0, q);
            List<String> kept = new ArrayList<>();
            for (String p : full.substring(q + 1).split("&")) {
                if (p.isEmpty()) {
                    continue;
                }
                int eq = p.indexOf('=');
                String key = eq >= 0 ? p.substring(0, eq) : p;
                if (!"code".equals(key) && !"state".equals(key)) {
                    kept.add(p);
                }
            }
            if (!kept.isEmpty()) {
                base = base + "?" + String.join("&", kept);
            }
        }
        if (https && base.startsWith("http://")) {
            base = "https://" + base.substring("http://".length());
        }
        return base;
    }

    private static String asString(Object v) {
        return (v != null) ? String.valueOf(v) : null;
    }
}
