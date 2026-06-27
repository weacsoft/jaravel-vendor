package com.weacsoft.jaravel.vendor.captcha;

/**
 * 验证码生成上下文。
 * <p>
 * 由 {@link AbstractCaptcha#generate(String)} 在调用 {@code doGenerate} 前构造，
 * 携带本次生成所需的 {@code captchaKey} 与 {@link CaptchaProperties}，并提供一个
 * {@code answer} 写回通道：子类在 {@code doGenerate} 中计算答案后通过
 * {@link #setAnswer(String)} 写入，由模板方法统一存入 {@link CaptchaStore}。
 * <p>
 * 这样可以避免把答案放进会下发到前端的 {@link CaptchaResult}，降低答案泄露风险。
 */
public class CaptchaContext {

    /** 验证码标识 */
    private final String captchaKey;

    /** 配置属性 */
    private final CaptchaProperties properties;

    /** 答案（由子类在 doGenerate 中写入） */
    private String answer;

    public CaptchaContext(String captchaKey, CaptchaProperties properties) {
        this.captchaKey = captchaKey;
        this.properties = properties;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public CaptchaProperties getProperties() {
        return properties;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
