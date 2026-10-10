package com.weacsoft.jaravel.vendor.springboot.core;

import com.weacsoft.jaravel.vendor.core.crypto.AppKey;
import com.weacsoft.jaravel.vendor.core.crypto.DefaultAppKey;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * jaravel 核心装配（P3 起为 Spring 宿主与零 Spring core 的唯一桥）。
 * <p>
 * <h3>职责</h3>
 * <ul>
 *   <li><b>安装全局 Bean 提供者</b>：创建 {@link ContextBeanProvider} 并
 *       {@code GlobalLookup.install(...)}，此后 core 的
 *       {@code SpringContext / Facade / Application.make / 注册器扫描(需宿主触发)}
 *       均可解析容器 Bean（替代 P3 前 {@code SpringContext} 直持 {@code ApplicationContext} 的方式）。</li>
 *   <li><b>全局应用密钥</b>：{@link AppKey}（{@code jaravel.key}，缺失时
 *       {@link DefaultAppKey} 随机生成并告警），继承自 P3 前的 core {@code CoreAutoConfiguration}。</li>
 * </ul>
 * <p>
 * 两阶段引导（{@code ProviderRegistry}）与代码级配置（{@code ConfigDefinitionRegistrar}）
 * 的 Bean 及其触发仍由 {@code jaravel-starter} 的 {@code JaravelAutoConfiguration} 声明，
 * 本类只做 Spring 桥接与全局密钥；两者 Bean 互不重复。
 * <p>
 * 在 {@code META-INF/spring/...AutoConfiguration.imports} 中排在最前，
 * 保证其余自动配置在容器装配阶段即可使用全局查找。
 */
@AutoConfiguration
public class CoreSpringConfiguration {

    /**
     * 全局 Bean 提供者：安装到 {@link GlobalLookup}，供 core 静态门面与注册器解析 Bean。
     * <p>
     * 用户若已自行提供 {@link GlobalBeanProvider} Bean（如需要自定义查找语义），
     * 本 Bean 让位但仍执行安装，保证 {@link GlobalLookup} 始终有提供者。
     *
     * @param context Spring 应用上下文
     * @return 已安装的 GlobalBeanProvider
     */
    @Bean
    @ConditionalOnMissingBean(GlobalBeanProvider.class)
    public GlobalBeanProvider globalBeanProvider(ApplicationContext context) {
        GlobalBeanProvider provider = new ContextBeanProvider(context);
        GlobalLookup.install(provider);
        return provider;
    }

    /**
     * 全局应用密钥 Bean（继承 P3 前 core CoreAutoConfiguration 的语义）。
     * <p>
     * 使用 {@code @ConditionalOnMissingBean} 允许用户自行提供 {@link AppKey} 实现
     * （例如从 KMS / 环境变量读取）。
     *
     * @param key application 配置 {@code jaravel.key}（缺失则为空串，触发随机生成）
     * @return 全局应用密钥
     */
    @Bean
    @ConditionalOnMissingBean(AppKey.class)
    public AppKey appKey(@Value("${jaravel.key:}") String key) {
        if (key == null || key.isBlank()) {
            // 临时密钥的统一告警点（架构/安全/验收三方一致）：AppKey 有 **4 个消费方** ——
            // captcha（token 加密）、wire（快照签名）、EncryptCookies（Cookie 加密）、jwt（验签）。
            // 随机临时密钥「每次启动都不同」，因此多实例/重启后：
            //   ① 验证码永远校验不过；② EncryptCookies 旧 Cookie 无法解密（反复掉登录态）；
            //   ③ JWT 旧 token 全部验签失败（401 抖动）；④ wire 快照 403。
            // 放在生产点做一次 ERROR，避免只在 captcha 局部告警而漏掉其余三条。
            // （DefaultAppKey 自身已有 WARN；这里是「影响面清单」级别的可操作提示。）
            org.slf4j.LoggerFactory.getLogger(CoreSpringConfiguration.class).error(
                    "[AppKey] 未配置 jaravel.key，正在使用进程级随机临时密钥：重启/多实例后所有基于它"
                            + "派生的凭证都会失效 —— 验证码校验、加密 Cookie 解密、JWT 验签、wire 快照签名。"
                            + "生产环境请执行 `artisan key:generate` 并写入 jaravel.key。");
        }
        return new DefaultAppKey(key);
    }
}
