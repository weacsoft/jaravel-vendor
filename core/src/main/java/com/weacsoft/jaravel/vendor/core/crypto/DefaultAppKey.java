package com.weacsoft.jaravel.vendor.core.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * {@link AppKey} 的默认实现。
 * <p>
 * 优先使用 application 配置中的 {@code jaravel.key}（Base64 编码的随机串）。
 * 若该配置缺失，则<b>每次启动随机生成一把密钥</b>并打印强烈告警——
 * 随机密钥意味着重启后旧令牌 / 旧验证码全部失效，仅可用于本地开发，
 * 生产环境务必通过 {@code artisan key:generate} 生成并写入配置。
 */
public class DefaultAppKey implements AppKey {

    private static final Logger log = LoggerFactory.getLogger(DefaultAppKey.class);

    private final String key;

    /**
     * 构造全局应用密钥。
     *
     * @param configuredKey application 配置中的 {@code jaravel.key}（可能为 null 或空）
     */
    public DefaultAppKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            this.key = configuredKey.trim();
            log.info("[AppKey] 使用配置中的全局应用密钥 (jaravel.key)，长度 {} 字符", this.key.length());
        } else {
            this.key = generateRandomKey();
            log.warn("********************************************************************************");
            log.warn("[AppKey] 未配置 jaravel.key，已随机生成临时密钥（重启即失效，仅可用于本地开发）。");
            log.warn("[AppKey] 生产环境请执行 `artisan key:generate` 生成密钥并写入 application 配置。");
            log.warn("********************************************************************************");
        }
    }

    @Override
    public String getKey() {
        return key;
    }

    /**
     * 生成 32 字节随机密钥的 Base64 表示（256-bit，适配 AES-256 / HMAC-SHA256）。
     *
     * @return Base64 编码的随机密钥
     */
    public static String generateRandomKey() {
        return generateRandomKey(32);
    }

    /**
     * 生成指定字节长度随机密钥的 Base64 表示。
     * <p>
     * 供 {@code artisan key:generate} 命令复用，保证 CLI 生成的密钥
     * 与运行时兜底生成的密钥格式完全一致。
     *
     * @param byteLength 随机字节数（建议 16 / 24 / 32）
     * @return Base64 编码的随机密钥
     */
    public static String generateRandomKey(int byteLength) {
        if (byteLength < 16) {
            byteLength = 16;
        }
        byte[] bytes = new byte[byteLength];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
