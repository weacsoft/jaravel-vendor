package com.weacsoft.jaravel.vendor.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 微信 SDK 自动装配，对齐 PHP {@code overtrue/laravel-wechat} 的服务提供者。
 * <p>
 * 当 {@code jaravel.wechat.enabled=true}（默认）且 classpath 存在相关类时，
 * 自动注册以下 Bean：
 * <ul>
 *   <li>{@link OkHttpClient} —— 微信 API HTTP 客户端（带超时配置）</li>
 *   <li>{@link AccessTokenManager} —— Access Token 管理器（基于 cache 模块缓存 token）</li>
 *   <li>{@link OfficialAccountService} —— 公众号服务</li>
 *   <li>{@link MiniProgramService} —— 小程序服务</li>
 * </ul>
 *
 * <h3>装配条件</h3>
 * <ul>
 *   <li>{@code jaravel.wechat.enabled} 不为 false（默认启用）</li>
 *   <li>classpath 存在 {@link OkHttpClient} 类</li>
 * </ul>
 *
 * <h3>PHP 对齐</h3>
 * <p>
 * 对应 PHP 项目中通过 ServiceProvider 注册 EasyWeChat Application 的过程。
 * PHP 中 {@code overtrue/laravel-wechat} 通过 {@code WechatServiceProvider} 自动注册
 * {@code EasyWeChat\Application} 实例到容器，Java 侧通过本自动装配类实现等价功能。
 *
 * @author weacsoft
 */
@AutoConfiguration
@ConditionalOnClass(OkHttpClient.class)
@ConditionalOnProperty(name = "jaravel.wechat.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WechatProperties.class)
public class WechatAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WechatAutoConfiguration.class);

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
     * 注入 OkHttpClient、ObjectMapper 以及 CacheManager 提供者（由 cache 模块提供）。
     * AccessTokenManager 通过 CacheManager 解析缓存仓库：优先 redis store，未注册时回退 array 内存缓存。
     *
     * @param wechatHttpClient     OkHttp 客户端
     * @param objectMapper         Jackson JSON 解析器
     * @param cacheManagerProvider 缓存管理器提供者（由 cache 模块提供）
     * @return AccessTokenManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessTokenManager accessTokenManager(OkHttpClient wechatHttpClient,
                                                  ObjectMapper objectMapper,
                                                  WechatProperties properties,
                                                  ObjectProvider<CacheManager> cacheManagerProvider) {
        logger.info("[wechat] 初始化 AccessTokenManager, 首选缓存 store: {}", properties.getCacheStore());
        return new AccessTokenManager(wechatHttpClient, objectMapper,
                cacheManagerProvider.getIfAvailable(), properties.getCacheStore());
    }

    /**
     * 公众号服务 Bean。
     *
     * @param accessTokenManager   Access Token 管理器
     * @param properties           微信配置属性
     * @param wechatHttpClient     OkHttp 客户端
     * @param objectMapper         Jackson JSON 解析器
     * @param cacheManagerProvider 缓存管理器提供者（用于 JSSDK ticket 缓存）
     * @return OfficialAccountService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OfficialAccountService officialAccountService(AccessTokenManager accessTokenManager,
                                                         WechatProperties properties,
                                                         OkHttpClient wechatHttpClient,
                                                         ObjectMapper objectMapper,
                                                         ObjectProvider<CacheManager> cacheManagerProvider) {
        logger.info("[wechat] 初始化 OfficialAccountService");
        return new OfficialAccountService(accessTokenManager, properties, wechatHttpClient,
                objectMapper, cacheManagerProvider.getIfAvailable());
    }

    /**
     * 小程序服务 Bean。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param wechatHttpClient   OkHttp 客户端
     * @param objectMapper       Jackson JSON 解析器
     * @return MiniProgramService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MiniProgramService miniProgramService(AccessTokenManager accessTokenManager,
                                                 WechatProperties properties,
                                                 OkHttpClient wechatHttpClient,
                                                 ObjectMapper objectMapper) {
        logger.info("[wechat] 初始化 MiniProgramService");
        return new MiniProgramService(accessTokenManager, properties, wechatHttpClient, objectMapper);
    }
}
