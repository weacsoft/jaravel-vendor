package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.CaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.CacheStoreCaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.MemoryCaptchaStore;
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
                                         org.springframework.beans.factory.ObjectProvider<com.weacsoft.jaravel.vendor.cache.CacheStore> cacheStoreProvider) {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties coreProps = properties.toCoreProperties();

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
        manager.register(new com.weacsoft.jaravel.vendor.captcha.NumberCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.ArithmeticCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.SliderCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.RotateCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.ClickCaptcha(coreProps));

        // 设置静态默认实例，支持 CaptchaService.generateStatic() / verifyStatic()
        CaptchaManager.setDefault(manager);

        log.info("验证码管理器已初始化：types={}, encryption={}, store={}",
                manager.getTypes(), coreProps.getEncryptionType(),
                cacheStore != null ? "CacheStore" : "Memory");
        return manager;
    }
}
