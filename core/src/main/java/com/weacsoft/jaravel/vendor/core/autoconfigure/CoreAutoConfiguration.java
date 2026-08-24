package com.weacsoft.jaravel.vendor.core.autoconfigure;

import com.weacsoft.jaravel.vendor.core.crypto.AppKey;
import com.weacsoft.jaravel.vendor.core.crypto.DefaultAppKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * core 模块自动装配：暴露统一的全局应用密钥 {@link AppKey}。
 * <p>
 * 读取 application 配置 {@code jaravel.key}（Base64 编码的随机串）。
 * 缺失时 {@link DefaultAppKey} 会随机生成临时密钥并告警。
 * 各加密模块通过 {@code ObjectProvider<AppKey>} 注入，
 * 按「模块自身配置优先 → 全局密钥兜底」取用。
 */
@AutoConfiguration
public class CoreAutoConfiguration {

    /**
     * 注册全局应用密钥 Bean。
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
        return new DefaultAppKey(key);
    }
}
