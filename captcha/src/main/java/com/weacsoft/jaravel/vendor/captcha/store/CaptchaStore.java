package com.weacsoft.jaravel.vendor.captcha.store;

/**
 * 验证码存储接口（SPI）。
 * <p>
 * 核心层通过此接口存取验证码答案，与具体存储介质解耦。
 * 默认实现 {@link MemoryCaptchaStore}（{@code ConcurrentHashMap} + TTL）。
 * 可适配为 CacheStore（jaravel cache 模块）、Redis 等。
 */
public interface CaptchaStore {

    /**
     * 存储验证码答案。
     *
     * @param captchaKey 验证码标识
     * @param answer     答案（数字验证码填字符，滑动填 x 坐标，旋转填角度）
     * @param ttlSeconds 过期秒数
     */
    void put(String captchaKey, String answer, long ttlSeconds);

    /**
     * 读取验证码答案，不存在或已过期返回 {@code null}。
     *
     * @param captchaKey 验证码标识
     * @return 答案，不存在或已过期返回 {@code null}
     */
    String get(String captchaKey);

    /**
     * 读取并删除（验证成功后一次性消费）。
     *
     * @param captchaKey 验证码标识
     * @return 答案，不存在或已过期返回 {@code null}
     */
    String pull(String captchaKey);

    /**
     * 移除验证码。
     *
     * @param captchaKey 验证码标识
     */
    void remove(String captchaKey);

    /**
     * 原子「仅当不存在（或已过期）时写入」——一次性占用（nonce 消费）的基础原语。
     * <p>
     * <b>为什么需要它</b>：旧实现用「先查 {@code get} 再写 {@code put}」判断 nonce 是否已消费，
     * 并发下两个请求可同时通过检查，使「验证码一次性」保证失效（审计 M5）。
     * <p>
     * 默认实现返回 {@code false}，表示<b>该存储不支持原子语义</b>；调用方会据此退回
     * 「先查后写」的兼容路径（并发窗口已知）。内置实现（内存 / CacheStore）均已覆盖本方法。
     *
     * @param captchaKey 验证码标识
     * @param value      值
     * @param ttlSeconds 过期秒数
     * @return 实际写入返回 true；键已存在返回 false；不支持原子语义也返回 false
     */
    default boolean putIfAbsent(String captchaKey, String value, long ttlSeconds) {
        return false;
    }
}
