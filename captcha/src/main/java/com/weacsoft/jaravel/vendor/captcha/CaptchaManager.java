package com.weacsoft.jaravel.vendor.captcha;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 验证码管理器：统一管理多种验证码类型的注册与调用。
 * <p>
 * 内部维护 {@code type -> Captcha} 映射，提供按类型生成 / 验证的入口，
 * 并通过 {@link #createDefault()} 提供开箱即用的默认管理器（注册数字、算术、滑动、旋转四种）。
 * 核心层不依赖 Spring，可独立使用；SpringBoot 兼容层可将其包装为 Bean。
 *
 * <pre>
 *   CaptchaManager manager = CaptchaManager.createDefault();
 *   CaptchaResult result = manager.generate("number", uuid);
 *   boolean ok = manager.verify("number", uuid, userInput);
 * </pre>
 */
public class CaptchaManager {

    /** type -> Captcha，使用 LinkedHashMap 保持注册顺序 */
    private final Map<String, Captcha> captchas = new LinkedHashMap<>();

    /** 默认存储与配置（createDefault 使用，单例共享） */
    private CaptchaStore store;

    private CaptchaProperties properties;

    public CaptchaManager() {
        this(new MemoryCaptchaStore(), CaptchaProperties.createDefault());
    }

    public CaptchaManager(CaptchaStore store, CaptchaProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /**
     * 注册验证码实现。
     *
     * @param captcha 验证码实现
     */
    public void register(Captcha captcha) {
        if (captcha == null || captcha.getType() == null) {
            throw new IllegalArgumentException("captcha and its type must not be null");
        }
        captchas.put(captcha.getType(), captcha);
    }

    /**
     * 生成指定类型的验证码。
     *
     * @param type       验证码类型
     * @param captchaKey 验证码标识
     * @return 生成结果
     * @throws IllegalArgumentException 类型未注册时抛出
     */
    public CaptchaResult generate(String type, String captchaKey) {
        Captcha captcha = captchas.get(type);
        if (captcha == null) {
            throw new IllegalArgumentException("Unsupported captcha type: " + type);
        }
        return captcha.generate(captchaKey);
    }

    /**
     * 验证指定类型的验证码。
     *
     * @param type       验证码类型
     * @param captchaKey 验证码标识
     * @param userInput  用户输入
     * @return 是否通过；类型未注册返回 {@code false}
     */
    public boolean verify(String type, String captchaKey, String userInput) {
        Captcha captcha = captchas.get(type);
        if (captcha == null) {
            return false;
        }
        return captcha.verify(captchaKey, userInput);
    }

    /**
     * 返回所有已注册类型。
     *
     * @return 不可修改的类型集合
     */
    public Set<String> getTypes() {
        return Collections.unmodifiableSet(captchas.keySet());
    }

    /**
     * 按类型获取验证码实现。
     *
     * @param type 验证码类型
     * @return 验证码实现，不存在返回 {@code null}
     */
    public Captcha getCaptcha(String type) {
        return captchas.get(type);
    }

    /**
     * 返回全部已注册验证码的不可修改视图。
     *
     * @return type -> Captcha 映射
     */
    public Map<String, Captcha> getCaptchas() {
        return Collections.unmodifiableMap(captchas);
    }

    public CaptchaStore getStore() {
        return store;
    }

    public void setStore(CaptchaStore store) {
        this.store = store;
    }

    public CaptchaProperties getProperties() {
        return properties;
    }

    public void setProperties(CaptchaProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建默认管理器：使用内存存储与默认配置，注册数字、算术、滑动、旋转四种验证码。
     * <p>
     * 四种验证码共享同一 {@link CaptchaStore} 与 {@link CaptchaProperties}。
     *
     * @return 默认管理器
     */
    public static CaptchaManager createDefault() {
        CaptchaProperties properties = CaptchaProperties.createDefault();
        CaptchaStore store = new MemoryCaptchaStore();
        CaptchaManager manager = new CaptchaManager(store, properties);
        manager.register(new NumberCaptcha(store, properties));
        manager.register(new ArithmeticCaptcha(store, properties));
        manager.register(new SliderCaptcha(store, properties));
        manager.register(new RotateCaptcha(store, properties));
        return manager;
    }
}
