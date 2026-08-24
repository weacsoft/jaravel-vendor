package com.weacsoft.jaravel.vendor.captcha.generator;

import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;

/**
 * 验证码接口。
 * <p>
 * 所有验证码类型（图片数字、算术、滑动、旋转、文字点选）实现此接口。
 * 核心层不依赖 SpringBoot，可独立使用。
 * <p>
 * 采用无状态设计：{@link #generate()} 生成的 captchaKey 自包含答案（加密编码），
 * {@link #verify(String, String)} 通过解密 captchaKey 获取答案进行验证，
 * 服务端无需保存任何验证码状态。
 */
public interface Captcha {

    /**
     * 生成验证码（无状态模式）。
     * <p>
     * 内部自动生成自包含的 captchaKey（加密的 answer|expireTime|nonce），
     * 服务端无需存储任何状态。
     *
     * @return 验证码结果（含 base64 图片、captchaKey、过期时间等）
     */
    CaptchaResult generate();

    /**
     * 验证用户提交的答案。
     * <p>
     * 通过解密 captchaKey 获取真实答案与过期时间，检查是否过期后交由子类比对。
     *
     * @param captchaKey 生成时返回的验证码 key（自包含加密令牌）
     * @param userInput  用户输入（可能是加密的）
     * @return 验证是否通过
     */
    boolean verify(String captchaKey, String userInput);

    /**
     * 返回验证码类型名称。
     *
     * @return 类型名（如 "number"、"arithmetic"、"slider"、"rotate"、"click"）
     */
    String getType();
}
