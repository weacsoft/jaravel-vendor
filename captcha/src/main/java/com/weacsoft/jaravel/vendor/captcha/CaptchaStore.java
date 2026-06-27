package com.weacsoft.jaravel.vendor.captcha;

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
}
