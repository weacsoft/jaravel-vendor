package com.weacsoft.jaravel.vendor.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JsonCodec 全局持有器。
 * <p>
 * 优先使用由 Spring 自动装配注入的实例（{@link #setCodec}）；
 * 若未被设置（如非 Spring 环境或单元测试），则懒加载并通过 classpath 自动检测：
 * <ul>
 *   <li>检测到 {@code tools.jackson.databind.ObjectMapper}（Jackson 3）→ {@link Jackson3JsonCodec}</li>
 *   <li>检测到 {@code com.fasterxml.jackson.databind.ObjectMapper}（Jackson 2）→ {@link Jackson2JsonCodec}</li>
 * </ul>
 */
public final class JsonCodecHolder {

    private static final Logger log = LoggerFactory.getLogger(JsonCodecHolder.class);

    private static volatile JsonCodec codec;

    private JsonCodecHolder() {
    }

    /**
     * 获取全局 JsonCodec 实例。
     * <p>
     * 若尚未被 {@link #setCodec} 设置，则自动检测 classpath 并创建。
     */
    public static JsonCodec codec() {
        JsonCodec local = codec;
        if (local == null) {
            synchronized (JsonCodecHolder.class) {
                local = codec;
                if (local == null) {
                    local = detectDefault();
                    codec = local;
                }
            }
        }
        return local;
    }

    /**
     * 由 Spring 自动装配调用，注入容器管理的 JsonCodec。
     */
    public static void setCodec(JsonCodec codec) {
        JsonCodecHolder.codec = codec;
    }

    /**
     * 检测 classpath 中可用的 Jackson 版本，创建对应的 JsonCodec。
     * 优先尝试 Jackson 3（SB4），回退 Jackson 2（SB3）。
     */
    static JsonCodec detectDefault() {
        // 优先 Jackson 3（SB4 运行时）
        try {
            Class.forName("tools.jackson.databind.ObjectMapper");
            log.info("JsonCodec: detected Jackson 3, using Jackson3JsonCodec");
            return new Jackson3JsonCodec();
        } catch (ClassNotFoundException e3) {
            // 回退 Jackson 2（SB3 运行时）
        }
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            log.info("JsonCodec: detected Jackson 2, using Jackson2JsonCodec");
            return new Jackson2JsonCodec();
        } catch (ClassNotFoundException e2) {
            throw new IllegalStateException(
                    "No Jackson implementation found on classpath. " +
                    "Add jackson-databind (Jackson 2) or tools.jackson.core:jackson-databind (Jackson 3).");
        }
    }
}
