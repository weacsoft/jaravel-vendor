package com.weacsoft.jaravel.vendor.captcha;

/**
 * 验证码接口。
 * <p>
 * 所有验证码类型（图片数字、算术、滑动、旋转等）实现此接口。
 * 核心层不依赖 SpringBoot，可独立使用。
 */
public interface Captcha {

    /**
     * 生成验证码。
     *
     * @param captchaKey 验证码唯一标识（用于后续验证时查找）
     * @return 验证码结果（含 base64 图片、token 等）
     */
    CaptchaResult generate(String captchaKey);

    /**
     * 验证用户提交的答案。
     *
     * @param captchaKey 验证码标识
     * @param userInput  用户输入（数字验证码填字符，滑动填 x 坐标，旋转填角度）
     * @return 验证是否通过
     */
    boolean verify(String captchaKey, String userInput);

    /**
     * 返回验证码类型名称。
     *
     * @return 类型名（如 "number"、"arithmetic"、"slider"、"rotate"）
     */
    String getType();
}
