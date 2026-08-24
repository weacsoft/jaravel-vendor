package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.store.CacheStoreCaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.store.MemoryCaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.generator.ArithmeticCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.ClickCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.RotateCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.SliderCaptcha;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 验证码 SpringBoot 自动装配。
 * <p>
 * 当 {@code jaravel.captcha.enabled=true}（默认）时自动创建 {@link CaptchaManager} Bean，
 * 注册五种验证码类型（数字、算术、滑动、旋转、文字点选）。
 * <p>
 * <b>无状态设计</b>：captchaKey 自包含加密的答案信息，服务端无需存储答案。
 * <b>防复用</b>：验证成功后 nonce 被写入 {@link CaptchaStore} 标记为已消费。
 * <p>
 * <h3>存储层级（自动选择）</h3>
 * <ol>
 *     <li>若项目中存在 jaravel {@code CacheStore} Bean → 使用 {@link CacheStoreCaptchaStore}
 *         （支持 Redis / 数据库等，跨进程防复用）</li>
 *     <li>否则 → 使用 {@link MemoryCaptchaStore}（内存 ConcurrentHashMap + TTL，单机防复用）</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(CaptchaManager.class)
@ConditionalOnProperty(prefix = "jaravel.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CaptchaAutoConfiguration.class);

    /**
     * 创建验证码管理器 Bean，注册五种验证码类型。
     * <p>
     * 自动检测项目中是否有 jaravel {@code CacheStore}：有则用其做防复用存储，
     * 无则回退到内存 {@link MemoryCaptchaStore}。
     *
     * @param properties      SpringBoot 配置
     * @param cacheStore      jaravel CacheStore（可选，无则使用内存存储）
     * @return 验证码管理器
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaManager.class)
    public CaptchaManager captchaManager(CaptchaProperties properties,
                                         org.springframework.beans.factory.ObjectProvider<com.weacsoft.jaravel.vendor.cache.CacheStore> cacheStoreProvider,
                                         org.springframework.beans.factory.ObjectProvider<com.weacsoft.jaravel.vendor.core.crypto.AppKey> appKeyProvider) {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties coreProps =
                resolveCoreProperties(properties, appKeyProvider);

        // 选择防复用存储：优先使用 CacheStore，无则回退到 MemoryCaptchaStore
        CaptchaStore store;
        com.weacsoft.jaravel.vendor.cache.CacheStore cacheStore = cacheStoreProvider.getIfAvailable();
        if (cacheStore != null) {
            store = new CacheStoreCaptchaStore(cacheStore);
            log.info("验证码防复用存储：CacheStoreCaptchaStore（jaravel cache 模块）");
        } else {
            store = new MemoryCaptchaStore();
            log.info("验证码防复用存储：MemoryCaptchaStore（内存模式）");
        }

        CaptchaManager manager = new CaptchaManager(store, coreProps);
        manager.register(new NumberCaptcha(coreProps));
        manager.register(new ArithmeticCaptcha(coreProps));
        manager.register(new SliderCaptcha(coreProps));
        manager.register(new RotateCaptcha(coreProps));
        manager.register(new ClickCaptcha(coreProps));

        // 设置静态默认实例，支持 CaptchaService.generateStatic() / verifyStatic()
        CaptchaManager.setDefault(manager);

        log.info("验证码管理器已初始化：types={}, encryption={}, store={}",
                manager.getTypes(), coreProps.getEncryptionType(),
                cacheStore != null ? "CacheStore" : "Memory");
        return manager;
    }

    /**
     * 创建验证码场景注册表 Bean（前端可选场景白名单）。
     * <p>
     * 前端只能通过 {@code scene=<name>} 选择后端预声明的场景，
     * 不能再通过查询参数直接指定 {@code tolerance / clickTargetCount / length} 等安全参数。
     * 未配置任何场景时该 Bean 仍会创建，此时任何 scene 都不会命中，一律使用全局配置。
     *
     * <b>注意</b>：这里必须使用与 {@link #captchaManager} 完全一致的「已解析」核心配置
     * （即经过 {@code AppKey} 全局密钥兜底处理后的配置）。场景配置由
     * {@code globalProperties.copy()} 派生，若基准配置未经解析，场景副本就会带上
     * 模块出厂默认密钥，与管理器实际使用的全局密钥不一致。
     *
     * @param properties      SpringBoot 配置（含 scenes 定义）
     * @param appKeyProvider  全局应用密钥（可选）
     * @return 场景注册表
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaSceneRegistry.class)
    public CaptchaSceneRegistry captchaSceneRegistry(CaptchaProperties properties,
                                                     org.springframework.beans.factory.ObjectProvider<com.weacsoft.jaravel.vendor.core.crypto.AppKey> appKeyProvider) {
        return new CaptchaSceneRegistry(resolveCoreProperties(properties, appKeyProvider),
                properties.getScenes());
    }

    /**
     * 将 SpringBoot 配置转换为核心层配置，并完成全局应用密钥兜底。
     * <p>
     * 应用密钥兜底：模块自身未显式配置 {@code encryption-key}（等于出厂默认值）时，
     * 回退到全局 {@code jaravel.key}，避免每个模块各自维护弱默认密钥。
     * <p>
     * 该方法被 {@link #captchaManager} 与 {@link #captchaSceneRegistry} 共用，
     * 保证「生成」与「校验」两侧看到的加解密参数完全一致。
     *
     * @param properties     SpringBoot 配置
     * @param appKeyProvider 全局应用密钥（可选）
     * @return 已解析的核心层配置
     */
    private static com.weacsoft.jaravel.vendor.captcha.CaptchaProperties resolveCoreProperties(
            CaptchaProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.weacsoft.jaravel.vendor.core.crypto.AppKey> appKeyProvider) {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties coreProps = properties.toCoreProperties();
        com.weacsoft.jaravel.vendor.core.crypto.AppKey appKey =
                (appKeyProvider != null) ? appKeyProvider.getIfAvailable() : null;
        if (appKey != null) {
            String effective = appKey.resolve(
                    coreProps.getEncryptionKey(),
                    com.weacsoft.jaravel.vendor.captcha.CaptchaProperties.DEFAULT_ENCRYPTION_KEY);
            coreProps.setEncryptionKey(effective);
        }
        return coreProps;
    }

    static {
        PublishableRegistry.register(new CaptchaPublishableConfig());
        PublishableRegistry.register(new CaptchaStaticPublishable());
    }
}
