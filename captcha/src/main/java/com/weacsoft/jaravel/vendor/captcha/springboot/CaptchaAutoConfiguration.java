package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
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
 * <b>无状态设计</b>：captchaKey 自包含加密的答案信息，服务端无需存储任何验证码状态，
 * 无需 CaptchaStore / CacheStore 依赖。
 * <p>
 * <h3>独立使用（无 SpringBoot）</h3>
 * <pre>
 * CaptchaManager manager = CaptchaManager.createDefault();
 * CaptchaResult result = manager.generate("number");
 * boolean ok = manager.verify("number", result.getCaptchaKey(), "ABCD");
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(CaptchaManager.class)
@ConditionalOnProperty(prefix = "jaravel.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CaptchaAutoConfiguration.class);

    /**
     * 创建验证码管理器 Bean，注册五种验证码类型（无状态模式）。
     *
     * @param properties SpringBoot 配置
     * @return 验证码管理器
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaManager.class)
    public CaptchaManager captchaManager(CaptchaProperties properties) {
        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties coreProps = properties.toCoreProperties();

        CaptchaManager manager = new CaptchaManager(coreProps);
        manager.register(new com.weacsoft.jaravel.vendor.captcha.NumberCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.ArithmeticCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.SliderCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.RotateCaptcha(coreProps));
        manager.register(new com.weacsoft.jaravel.vendor.captcha.ClickCaptcha(coreProps));

        // 设置静态默认实例，支持 CaptchaService.generateStatic() / verifyStatic()
        CaptchaManager.setDefault(manager);

        log.info("验证码管理器已初始化（无状态模式）：types={}, encryption={}",
                manager.getTypes(), coreProps.getEncryptionType());
        return manager;
    }
}
