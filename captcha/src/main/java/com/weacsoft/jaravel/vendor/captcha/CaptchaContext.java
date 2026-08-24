package com.weacsoft.jaravel.vendor.captcha;

/**
 * 验证码生成上下文。
 * <p>
 * 由 {@link com.weacsoft.jaravel.vendor.captcha.generator.AbstractCaptcha#generate()} 在调用 {@code doGenerate} 前构造，
 * 携带本次生成所需的 {@link CaptchaProperties}，并提供一个
 * {@code answer} 写回通道：子类在 {@code doGenerate} 中计算答案后通过
 * {@link #setAnswer(String)} 写入，由模板方法加密编码为无状态 captchaKey。
 * <p>
 * 这样可以避免把答案放进会下发到前端的 {@link CaptchaResult}，降低答案泄露风险。
 */
public final class CaptchaContext {

    private final String captchaKey;
    private final CaptchaProperties properties;
    private String answer;

    public CaptchaContext(String captchaKey, CaptchaProperties properties) {
        this.captchaKey = captchaKey;
        this.properties = properties;
    }

    public String getCaptchaKey() { return captchaKey; }
    public CaptchaProperties getProperties() { return properties; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
}
