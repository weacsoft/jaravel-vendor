package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * wechat-sdk 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=wechat-sdk} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/WechatSdkConfig.java}，
 * 内含 {@code jaravel.wechat.*} 配置项说明。
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
        return "微信 SDK 配置（公众号、小程序多账号与 HTTP 参数）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * 微信 SDK 配置。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=wechat-sdk} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   wechat:
                 *     enabled: true                # 是否启用微信模块，默认 true
                 *     cache-store: ""              # access_token 使用的缓存 store 名，留空用默认 store
                 *     official-accounts:           # 公众号多账号配置，key 为账号别名
                 *       default:
                 *         app-id: ""
                 *         app-secret: ""
                 *     mini-apps:                   # 小程序多账号配置，key 为账号别名
                 *       default:
                 *         app-id: ""
                 *         app-secret: ""
                 *     http:                        # OkHttp 连接/读写超时等参数
                 *       connect-timeout: 10
                 *       read-timeout: 30
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的
                 *       OfficialAccountService / MiniProgramService。</li>
                 *   <li>快照中<b>不含</b> app-secret，避免密钥泄露。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class WechatSdkConfig {

                    /**
                     * 微信模块生效配置快照（不含敏感信息）。
                     *
                     * @param provider WechatProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> wechatSdkConfigMetadata(
                            ObjectProvider<WechatProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        WechatProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.wechat", "未装配（wechat 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.wechat.enabled", properties.isEnabled());
                        metadata.put("jaravel.wechat.cache-store", properties.getCacheStore());
                        metadata.put("jaravel.wechat.official-accounts",
                                properties.getOfficialAccounts().keySet());
                        metadata.put("jaravel.wechat.mini-apps", properties.getMiniApps().keySet());
                        return metadata;
                    }
                }
                """;
    }
}
