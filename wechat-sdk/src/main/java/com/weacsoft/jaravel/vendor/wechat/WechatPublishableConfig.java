package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * wechat-sdk 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=wechat-sdk} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/WechatSdkConfig.java}——
 * 一份<b>声明式</b>配置（{@code @RegisterWechatOfficialAccount}/{@code @RegisterWechatMiniApp}），
 * 符合 jaravel-vendor「声明 &gt; yml &gt; 兜底默认」的三层优先级约定：
 * 本文件中的声明拥有最高优先级，yml 作为回退，运行时 setter 为兜底。
 */
public class WechatPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "wechat-sdk";
    }

    @Override
    public String className() {
        return "WechatSdkConfig";
    }

    @Override
    public String description() {
        return "微信 SDK 声明式配置（@RegisterWechatOfficialAccount / @RegisterWechatMiniApp）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.wechat.RegisterWechatMiniApp;
                import com.weacsoft.jaravel.vendor.wechat.RegisterWechatOfficialAccount;
                import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
                import org.springframework.context.annotation.Configuration;

                /**
                 * 微信 SDK 配置（声明式，最高优先级层）。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=wechat-sdk} 发布生成，可自由修改。
                 * 框架启动时（所有单例 Bean 就绪后）扫描本类上的
                 * {@code @RegisterWechatOfficialAccount} / {@code @RegisterWechatMiniApp} 方法，
                 * 把返回的配置对象回填到共享 {@code WechatProperties}——
                 * <b>声明 &gt; yml &gt; 兜底默认</b>（jaravel-vendor 三层优先级约定）。
                 *
                 * <h3>三层优先级</h3>
                 * <ol>
                 *   <li><b>本文件声明</b>（最高）：启动扫描后直接生效</li>
                 *   <li><b>yml</b>：{@code jaravel.wechat.*}（见下方配置项说明）</li>
                 *   <li><b>兜底默认</b>：内置缺省值（enabled=true、token-mode=legacy、message-mode=plain…）</li>
                 * </ol>
                 *
                 * <h3>yml 兜底配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   wechat:
                 *     enabled: true              # 是否启用微信模块，默认 true
                 *     token-mode: legacy         # access_token 获取模式：legacy(GET token) | stable(POST stable_token)
                 *     cache-store: ""            # 票据缓存 store 名，留空用 cache 模块默认 store
                 *     official-accounts:
                 *       default:
                 *         app-id: ""             # 公众号 AppID
                 *         secret: ""             # 公众号 AppSecret
                 *         token: ""              # 接收消息校验 Token（server 必填）
                 *         aes-key: ""            # 43 位 EncodingAESKey（message-mode=safe 必填）
                 *         message-mode: plain    # 接收消息模式：plain 明文 | safe 加密
                 *         oauth:
                 *           scopes: snsapi_base  # snsapi_base | snsapi_userinfo
                 *           callback: /oauth/callback
                 *           enforce-https: true
                 *     mini-apps:
                 *       default:
                 *         app-id: ""             # 小程序 AppID
                 *         secret: ""             # 小程序 AppSecret
                 *         type: 2                # 业务类型：2=客服小程序，3=管理端小程序
                 *     http:
                 *       timeout: 5.0             # OkHttp 连接/读写超时（秒）
                 *       retry: true              # 连接失败重试
                 * </pre>
                 *
                 * <h3>凭证安全建议</h3>
                 * 把常量替换为环境变量/KeyCenter 调用（如 {@code System.getenv("WECHAT_OA_APPID")}），
                 * 避免密钥进代码库。
                 */
                @Configuration
                public class WechatSdkConfig {

                    // ==================== 公众号（@RegisterWechatOfficialAccount） ====================

                    /**
                     * 默认公众号配置。
                     * <p>
                     * value 为配置名：{@code service.server()} / {@code getAccessToken(name)} 的键。
                     * alias 让同一份凭证在多个名字下可命中（如 OAuth 授权范围不同的场景）。
                     */
                    @RegisterWechatOfficialAccount(value = "default", alias = {"snsapi_base"})
                    public WechatProperties.OfficialAccountConfig defaultAccount() {
                        WechatProperties.OfficialAccountConfig config = new WechatProperties.OfficialAccountConfig();
                        // TODO: 替换为真实凭证（建议读环境变量，避免密钥入库）
                        config.setAppId("wx_replace_with_appid");
                        config.setSecret("replace_with_appsecret");
                        // 接收消息（server 验签/被动回复）必填；不开放 server 能力可留空
                        config.setToken("msg_validate_token");
                        // 43 位 EncodingAESKey；message-mode=safe 时必填
                        config.setAesKey("encoding_aes_key_43_chars");
                        // 接收消息模式：plain 明文（默认）| safe 加密
                        config.setMessageMode("plain");
                        // OAuth 授权范围
                        config.getOauth().setScopes("snsapi_base");
                        config.getOauth().setCallback("/oauth/callback");
                        config.getOauth().setEnforceHttps(true);
                        return config;
                    }

                    // 如需 snsapi_userinfo 授权范围的独立配置，取消注释并补全凭证：
                    //
                    // @RegisterWechatOfficialAccount("snsapi_userinfo")
                    // public WechatProperties.OfficialAccountConfig userinfo() {
                    //     WechatProperties.OfficialAccountConfig config = new WechatProperties.OfficialAccountConfig();
                    //     config.setAppId("wx_replace_with_appid");
                    //     config.setSecret("replace_with_appsecret");
                    //     config.getOauth().setScopes("snsapi_userinfo");
                    //     config.getOauth().setCallback("/oauth/userinfo-callback");
                    //     return config;
                    // }

                    // ==================== 小程序（@RegisterWechatMiniApp） ====================

                    /**
                     * 默认小程序配置。
                     */
                    @RegisterWechatMiniApp("default")
                    public WechatProperties.MiniAppConfig defaultMiniApp() {
                        WechatProperties.MiniAppConfig config = new WechatProperties.MiniAppConfig();
                        // TODO: 替换为真实凭证
                        config.setAppId("wx_replace_with_mini_appid");
                        config.setSecret("replace_with_mini_appsecret");
                        // 业务类型：2=客服小程序，3=管理端小程序
                        config.setType(2);
                        return config;
                    }
                }
                """;
    }
}
