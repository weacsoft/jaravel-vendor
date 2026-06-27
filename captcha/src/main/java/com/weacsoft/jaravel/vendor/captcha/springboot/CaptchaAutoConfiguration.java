package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.CaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.MemoryCaptchaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证码 SpringBoot 自动装配。
 * <p>
 * 当 {@code jaravel.captcha.enabled=true}（默认）时自动创建 {@link CaptchaManager} Bean，
 * 注册四种验证码类型（数字、算术、滑动、旋转）。
 * <p>
 * <h3>存储选择</h3>
 * <ul>
 *   <li>有 jaravel cache 模块：使用 {@code CacheStoreCaptchaStore}，答案存入 Redis/数据库，支持跨进程</li>
 *   <li>无 cache 模块：使用 {@link MemoryCaptchaStore}，答案存内存，单进程有效</li>
 * </ul>
 * <p>
 * <h3>独立使用（无 SpringBoot）</h3>
 * 不引入本模块也不影响核心层使用：
 * <pre>
 * CaptchaManager manager = CaptchaManager.createDefault();
 * CaptchaResult result = manager.generate("number", "my-key");
 * boolean ok = manager.verify("number", "my-key", "ABCD");
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(CaptchaManager.class)
@ConditionalOnProperty(prefix = "jaravel.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CaptchaAutoConfiguration.class);

    /**
     * 无 cache 模块时的默认存储：内存存储。
     */
    @Configuration
    @ConditionalOnMissingClass("com.weacsoft.jaravel.vendor.cache.CacheStore")
    static class MemoryStoreConfig {
        @Bean
        @ConditionalOnMissingBean(CaptchaStore.class)
        CaptchaStore captchaStore() {
            log.info("验证码使用内存存储（未引入 cache 模块）");
            return new MemoryCaptchaStore();
        }
    }

    /**
     * 有 cache 模块时的存储：适配 CacheStore。
     */
    @Configuration
    @ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.cache.CacheStore")
    static class CacheStoreConfig {

        @Bean
        @ConditionalOnMissingBean(CaptchaStore.class)
        CaptchaStore captchaStore(
                ObjectProvider<com.weacsoft.jaravel.vendor.cache.CacheStore> cacheStoreProvider) {
            com.weacsoft.jaravel.vendor.cache.CacheStore cacheStore = cacheStoreProvider.getIfAvailable();
            if (cacheStore != null) {
                log.info("验证码使用 CacheStore 适配存储（跨进程模式）");
                return new com.weacsoft.jaravel.vendor.captcha.CacheStoreCaptchaStore(cacheStore);
            }
            log.info("CacheStore 未注册，验证码回退到内存存储");
            return new MemoryCaptchaStore();
        }
    }

    /**
     * 创建验证码管理器 Bean，注册四种验证码类型。
     *
     * @param properties SpringBoot 配置
     * @param captchaStoreProvider 存储提供者
     * @return 验证码管理器
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaManager.class)
    public CaptchaManager captchaManager(
            CaptchaProperties properties,
            ObjectProvider<CaptchaStore> captchaStoreProvider) {

        CaptchaStore store = captchaStoreProvider.getIfAvailable();
        if (store == null) {
            store = new MemoryCaptchaStore();
        }

        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties coreProps = properties.toCoreProperties();

        CaptchaManager manager = new CaptchaManager();
        manager.register(new com.weacsoft.jaravel.vendor.captcha.NumberCaptcha(store, coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.ArithmeticCaptcha(store, coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.SliderCaptcha(store, coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.RotateCaptcha(store, coreProps));

        log.info("验证码管理器已初始化：types={}, store={}", manager.getTypes(),
                store.getClass().getSimpleName());
        return manager;
    }
}
