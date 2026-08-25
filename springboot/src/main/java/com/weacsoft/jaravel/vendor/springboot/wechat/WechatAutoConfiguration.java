package com.weacsoft.jaravel.vendor.springboot.wechat;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.http.middleware.MiddlewareAliasRegistry;
import com.weacsoft.jaravel.vendor.http.session.SessionStoreHolder;
import com.weacsoft.jaravel.vendor.wechat.AccessTokenManager;
import com.weacsoft.jaravel.vendor.wechat.MiniProgramService;
import com.weacsoft.jaravel.vendor.wechat.OfficialAccountService;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.oauth.WeChatOAuth;
import com.weacsoft.jaravel.vendor.wechat.oauth.WeChatOAuthMiddleware;
import com.weacsoft.jaravel.vendor.springboot.wechat.registrar.WechatMiniAppRegistrar;
import com.weacsoft.jaravel.vendor.springboot.wechat.registrar.WechatOfficialAccountRegistrar;
import com.weacsoft.jaravel.vendor.wechat.transport.JacksonJsonEncoder;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 微信 SDK 自动装配，对齐 PHP {@code overtrue/laravel-wechat} 的服务提供者。
 * <p>
 * 当 {@code jaravel.wechat.enabled=true}（默认）且 classpath 存在相关类时，
 * 自动注册以下 Bean：
 * <ul>
 *   <li>{@link OkHttpClient} —— 微信 API HTTP 客户端（带超时配置）</li>
 *   <li>{@link AccessTokenManager} —— Access Token 管理器（基于 cache 模块缓存 token，
 *       模式由 {@code jaravel.wechat.token-mode} 决定 legacy/stable）</li>
 *   <li>{@link OfficialAccountService} —— 公众号服务（类型化 API）</li>
 *   <li>{@link MiniProgramService} —— 小程序服务（类型化 API）</li>
 *   <li>{@link WeChatOAuth} —— 公众号网页授权（授权 URL 组装 + code 换 openid/用户）</li>
 *   <li>{@link WeChatOAuthMiddleware} —— 网页授权自动重定向中间件（别名 {@code wechat.oauth}）</li>
 *   <li>{@link WechatOfficialAccountRegistrar} —— 扫描 {@code @RegisterWechatOfficialAccount} 声明</li>
 *   <li>{@link WechatMiniAppRegistrar} —— 扫描 {@code @RegisterWechatMiniApp} 声明</li>
 * </ul>
 *
 * <h3>装配顺序</h3>
 * 两个注册器继承 {@code AnnotationDrivenRegistrar}（SmartInitializingSingleton），
 * 在所有单例 Bean（含业务工程的 {@code @Register*} 配置类）初始化之后执行扫描，
 * 声明的配置回填到共享 {@link WechatProperties}，实现「声明 &gt; yml &gt; 兜底默认」。
 *
 * <h3>装配条件</h3>
 * <ul>
 *   <li>{@code jaravel.wechat.enabled} 不为 false（默认启用）</li>
 *   <li>classpath 存在 {@link OkHttpClient} 类</li>
 * </ul>
 *
 * @author weacsoft
 */
@AutoConfiguration
@ConditionalOnClass(OkHttpClient.class)
@ConditionalOnProperty(name = "jaravel.wechat.enabled", havingValue = "true", matchIfMissing = true)
public class WechatAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WechatAutoConfiguration.class);

    /**
     * 微信配置属性 Bean（{@code jaravel.wechat.*} 绑定）。
     * <p>
     * {@link WechatProperties} 本体是 wechat-sdk 模块的纯 POJO（零 Spring），
     * 绑定由本模块完成——对齐 model-cache / queue-database 的属性装配模式。
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.wechat")
    public WechatProperties wechatProperties() {
        return new WechatProperties();
    }

    /**
     * 微信 API HTTP 客户端 Bean。
     * <p>
     * 根据 {@link WechatProperties.HttpConfig} 配置连接与读取超时时间。
     * OkHttp 客户端线程安全，全局复用连接池。
     *
     * @param properties 微信配置属性
     * @return OkHttpClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "wechatHttpClient")
    public OkHttpClient wechatHttpClient(WechatProperties properties) {
        double timeoutSec = properties.getHttp().getTimeout();
        long timeoutMs = (long) (timeoutSec * 1000);
        logger.info("[wechat] 初始化 OkHttpClient: timeout={}ms, retry={}",
                timeoutMs, properties.getHttp().isRetry());
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(properties.getHttp().isRetry())
                .build();
    }

    /**
     * Access Token 管理器 Bean。
     * <p>
     * 注入 OkHttp 客户端、配置与 CacheManager 提供者（cache 模块提供）。
     * 缓存仓库使用 cache 模块默认 store（或 {@code jaravel.wechat.cache-store} 指定）；
     * 获取模式由 {@code jaravel.wechat.token-mode} 决定（legacy/stable）。
     *
     * @param wechatHttpClient     OkHttp 客户端
     * @param properties           微信配置属性
     * @param cacheManagerProvider 缓存管理器提供者（由 cache 模块提供）
     * @return AccessTokenManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessTokenManager accessTokenManager(OkHttpClient wechatHttpClient,
                                                  WechatProperties properties,
                                                  ObjectProvider<CacheManager> cacheManagerProvider) {
        String cacheStore = properties.getCacheStore();
        logger.info("[wechat] 初始化 AccessTokenManager, 缓存 store: {}, token-mode: {}",
                (cacheStore == null || cacheStore.isEmpty()) ? "默认 store" : cacheStore,
                properties.getTokenMode());
        return new AccessTokenManager(wechatHttpClient,
                cacheManagerProvider.getIfAvailable(), properties.getCacheStore(), properties.getTokenMode());
    }

    /**
     * 公众号服务 Bean。
     *
     * @param accessTokenManager   Access Token 管理器
     * @param properties           微信配置属性
     * @param wechatHttpClient     OkHttp 客户端
     * @param cacheManagerProvider 缓存管理器提供者（用于 JSSDK ticket 缓存）
     * @return OfficialAccountService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OfficialAccountService officialAccountService(AccessTokenManager accessTokenManager,
                                                          WechatProperties properties,
                                                          OkHttpClient wechatHttpClient,
                                                          ObjectProvider<CacheManager> cacheManagerProvider) {
        logger.info("[wechat] 初始化 OfficialAccountService");
        return new OfficialAccountService(accessTokenManager, properties, wechatHttpClient,
                new JacksonJsonEncoder(), cacheManagerProvider.getIfAvailable());
    }

    /**
     * 小程序服务 Bean。
     *
     * @param accessTokenManager   Access Token 管理器
     * @param properties           微信配置属性
     * @param wechatHttpClient     OkHttp 客户端
     * @param cacheManagerProvider 缓存管理器提供者（用于票据缓存）
     * @return MiniProgramService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MiniProgramService miniProgramService(AccessTokenManager accessTokenManager,
                                                  WechatProperties properties,
                                                  OkHttpClient wechatHttpClient,
                                                  ObjectProvider<CacheManager> cacheManagerProvider) {
        logger.info("[wechat] 初始化 MiniProgramService");
        return new MiniProgramService(accessTokenManager, properties, wechatHttpClient,
                new JacksonJsonEncoder(), cacheManagerProvider.getIfAvailable());
    }

    /**
     * 公众号网页授权（OAuth）服务 Bean。
     * <p>
     * 提供授权 URL 组装（{@code open.weixin.qq.com/connect/oauth2/authorize}）、
     * code 换 openid/用户信息（{@code sns/oauth2/access_token} + 可选 {@code sns/userinfo}）
     * 与 EasyWeChat 兼容的会话读写辅助（键 {@code easywechat.oauth_user.{account}}）。
     * <b>不注册任何 Auth guard</b>——业务侧自建 {@code wechat} guard 从会话键取 openid。
     *
     * @param properties       微信配置属性
     * @param wechatHttpClient OkHttp 客户端
     * @return WeChatOAuth 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WeChatOAuth wechatOAuth(WechatProperties properties, OkHttpClient wechatHttpClient) {
        logger.info("[wechat] 初始化 WeChatOAuth（网页授权）");
        return new WeChatOAuth(properties, wechatHttpClient);
    }

    /**
     * 公众号网页授权中间件 Bean（自动重定向），并注册路由别名 {@code wechat.oauth}。
     * <p>
     * 路由使用：{@code router.get("/weapp", handler).middleware("wechat.oauth")}
     * 或 {@code .middleware("wechat.oauth:default,snsapi_userinfo")}（参数对齐
     * PHP Laravel 的 {@code wechat.auth:default,snsapi_userinfo} 位置语义：account[,scope]）。
     * <p>
     * 会话存储：优先注入 http 模块的 {@link SessionStoreHolder}（与业务
     * {@code wechat} guard 同一存储实例；未装配时为 null，中间件侧对会话操作降级为 no-op，
     * 此时「已授权」判定退化为每请求都走 code 交换——业务侧请确保引入了 http 的
     * Session 自动装配，或自行以显式 {@code WeChatOAuthMiddleware(oauth, account, scope, store)} 构建）。
     *
     * @param wechatOAuth             网页授权服务
     * @param sessionStoreHolderProvider http 模块 Session 持有者提供者（缺失时为 null）
     * @return WeChatOAuthMiddleware 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WeChatOAuthMiddleware wechatOAuthMiddleware(WeChatOAuth wechatOAuth,
                                                        ObjectProvider<SessionStoreHolder> sessionStoreHolderProvider) {
        SessionStoreHolder holder = sessionStoreHolderProvider.getIfAvailable();
        WeChatOAuthMiddleware mw = new WeChatOAuthMiddleware(wechatOAuth, "default", null, holder);
        try {
            MiddlewareAliasRegistry.getGlobal().register("wechat.oauth", mw);
            logger.info("[wechat] 注册路由中间件别名 wechat.oauth（网页授权自动重定向）");
        } catch (RuntimeException e) {
            logger.warn("[wechat] 注册 wechat.oauth 别名失败（可继续程序式注入该 Bean）: {}", e.getMessage());
        }
        return mw;
    }

    /**
     * 公众号命名配置声明注册器：扫描 {@code @RegisterWechatOfficialAccount} 方法，
     * 将返回的 {@link WechatProperties.OfficialAccountConfig} 回填到共享配置（声明 &gt; yml）。
     * <p>
     * P3：core 纯扫描器；扫描由下方 SmartInitializingSingleton 触发（保持原时序）。
     *
     * @param properties 微信配置属性（回填目标）
     * @return 注册器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WechatOfficialAccountRegistrar wechatOfficialAccountRegistrar(WechatProperties properties) {
        return new WechatOfficialAccountRegistrar(properties);
    }

    /**
     * 公众号注册器扫描触发。
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton
    wechatOfficialAccountRegistrarScanner(WechatOfficialAccountRegistrar registrar) {
        return registrar::scan;
    }

    /**
     * 小程序命名配置声明注册器：扫描 {@code @RegisterWechatMiniApp} 方法，
     * 将返回的 {@link WechatProperties.MiniAppConfig} 回填到共享配置（声明 &gt; yml）。
     * <p>
     * P3：core 纯扫描器；扫描由下方 SmartInitializingSingleton 触发（保持原时序）。
     *
     * @param properties 微信配置属性（回填目标）
     * @return 注册器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WechatMiniAppRegistrar wechatMiniAppRegistrar(WechatProperties properties) {
        return new WechatMiniAppRegistrar(properties);
    }

    /**
     * 小程序注册器扫描触发。
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton
    wechatMiniAppRegistrarScanner(WechatMiniAppRegistrar registrar) {
        return registrar::scan;
    }
}
