package com.weacsoft.jaravel.vendor.captcha;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 验证码管理器：统一管理多种验证码类型的注册与调用。
 * <p>
 * 内部维护 {@code type -> Captcha} 映射，提供按类型生成 / 验证的入口，
 * 并通过 {@link #createDefault()} 提供开箱即用的默认管理器（注册数字、算术、滑动、旋转、文字点选五种）。
 * 核心层不依赖 Spring，可独立使用；SpringBoot 兼容层可将其包装为 Bean。
 * <p>
 * <h3>基本用法</h3>
 * <pre>
 *   CaptchaManager manager = CaptchaManager.createDefault();
 *   CaptchaResult result = manager.generate("number");
 *   boolean ok = manager.verify("number", result.getCaptchaKey(), userInput);
 * </pre>
 * <p>
 * <h3>运行时配置覆盖</h3>
 * <pre>
 *   CaptchaProperties custom = CaptchaProperties.createDefault();
 *   custom.setWidth(300);
 *   custom.setHeight(100);
 *   CaptchaResult result = manager.generate("number", custom);
 * </pre>
 * <p>
 * <h3>运行时加密密钥</h3>
 * <pre>
 *   CaptchaResult result = manager.generate("number", null, "my-secret-key");
 *   boolean ok = manager.verify("number", captchaKey, userInput, "my-secret-key");
 * </pre>
 * <p>
 * <h3>静态调用</h3>
 * <pre>
 *   CaptchaResult result = CaptchaManager.generateStatic("number");
 *   boolean ok = CaptchaManager.verifyStatic("number", captchaKey, userInput);
 * </pre>
 */
public class CaptchaManager {

    /** type -> Captcha，使用 LinkedHashMap 保持注册顺序 */
    private final Map<String, Captcha> captchas = new LinkedHashMap<>();

    /** 配置属性 */
    private CaptchaProperties properties;

    /** 验证码存储（用于防复用），默认内存实现 */
    private CaptchaStore store = new MemoryCaptchaStore();

    /** 静态默认实例（延迟初始化） */
    private static volatile CaptchaManager defaultInstance;

    public CaptchaManager() {
        this(CaptchaProperties.createDefault());
    }

    public CaptchaManager(CaptchaProperties properties) {
        this.properties = properties;
    }

    /**
     * 指定存储和配置构造。
     * <p>
     * 传入的 {@code store} 用于防复用：验证成功后 nonce 被写入 store，
     * 再次验证同一 captchaKey 时会被拒绝。
     *
     * @param store      验证码存储（用于防复用），null 则使用 {@link MemoryCaptchaStore}
     * @param properties 配置属性
     */
    public CaptchaManager(CaptchaStore store, CaptchaProperties properties) {
        this.properties = properties;
        if (store != null) {
            this.store = store;
        }
    }

    /**
     * 设置验证码存储（用于防复用），会同步更新所有已注册的 AbstractCaptcha 实例。
     *
     * @param store 验证码存储，null 则忽略
     */
    public void setStore(CaptchaStore store) {
        if (store != null) {
            this.store = store;
            for (Captcha captcha : captchas.values()) {
                if (captcha instanceof AbstractCaptcha) {
                    ((AbstractCaptcha) captcha).setStore(store);
                }
            }
        }
    }

    public CaptchaStore getStore() {
        return store;
    }

    /**
     * 注册验证码实现，自动注入当前 store。
     *
     * @param captcha 验证码实现
     */
    public void register(Captcha captcha) {
        if (captcha == null || captcha.getType() == null) {
            throw new IllegalArgumentException("captcha and its type must not be null");
        }
        if (captcha instanceof AbstractCaptcha) {
            ((AbstractCaptcha) captcha).setStore(this.store);
        }
        captchas.put(captcha.getType(), captcha);
    }

    // ==================== 生成 ====================

    /**
     * 生成指定类型的验证码（使用默认配置）。
     *
     * @param type 验证码类型
     * @return 生成结果
     * @throws IllegalArgumentException 类型未注册时抛出
     */
    public CaptchaResult generate(String type) {
        return generate(type, null, null);
    }

    /**
     * 生成指定类型的验证码（带运行时配置覆盖）。
     *
     * @param type      验证码类型
     * @param overrides 运行时配置覆盖（null 表示使用默认配置）
     * @return 生成结果
     */
    public CaptchaResult generate(String type, CaptchaProperties overrides) {
        return generate(type, overrides, null);
    }

    /**
     * 生成指定类型的验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param type          验证码类型
     * @param overrides     运行时配置覆盖（null 表示使用默认配置）
     * @param encryptionKey 运行时加密密钥（null 表示使用配置中的密钥）
     * @return 生成结果
     */
    public CaptchaResult generate(String type, CaptchaProperties overrides, String encryptionKey) {
        Captcha captcha = captchas.get(type);
        if (captcha == null) {
            throw new IllegalArgumentException("Unsupported captcha type: " + type);
        }
        if (captcha instanceof AbstractCaptcha) {
            return ((AbstractCaptcha) captcha).generate(overrides, encryptionKey);
        }
        return captcha.generate();
    }

    // ==================== 验证 ====================

    /**
     * 验证指定类型的验证码（使用默认配置，自动解密加密输入）。
     *
     * @param type       验证码类型
     * @param captchaKey 验证码标识（自包含加密令牌）
     * @param userInput  用户输入（可能是加密的）
     * @return 是否通过；类型未注册返回 {@code false}
     */
    public boolean verify(String type, String captchaKey, String userInput) {
        return verify(type, captchaKey, userInput, null, null);
    }

    /**
     * 验证指定类型的验证码（带运行时加密密钥）。
     *
     * @param type          验证码类型
     * @param captchaKey    验证码标识
     * @param userInput     用户输入
     * @param encryptionKey 运行时加密密钥（null 表示使用配置中的密钥）
     * @return 是否通过
     */
    public boolean verify(String type, String captchaKey, String userInput, String encryptionKey) {
        return verify(type, captchaKey, userInput, null, encryptionKey);
    }

    /**
     * 验证指定类型的验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param type          验证码类型
     * @param captchaKey    验证码标识
     * @param userInput     用户输入
     * @param overrides     运行时配置覆盖
     * @param encryptionKey 运行时加密密钥
     * @return 是否通过
     */
    public boolean verify(String type, String captchaKey, String userInput,
                          CaptchaProperties overrides, String encryptionKey) {
        return verifyDetailed(type, captchaKey, userInput, overrides, encryptionKey).isPassed();
    }

    /**
     * 验证指定类型的验证码，返回详细结果（含是否已被使用）。
     *
     * @param type          验证码类型
     * @param captchaKey    验证码标识
     * @param userInput     用户输入
     * @param overrides     运行时配置覆盖
     * @param encryptionKey 运行时加密密钥
     * @return 验证结果（含是否通过、是否已被使用）
     */
    public VerifyResult verifyDetailed(String type, String captchaKey, String userInput,
                          CaptchaProperties overrides, String encryptionKey) {
        Captcha captcha = captchas.get(type);
        if (captcha == null) {
            return VerifyResult.fail();
        }
        if (captcha instanceof AbstractCaptcha) {
            return ((AbstractCaptcha) captcha).verify(captchaKey, userInput, overrides, encryptionKey);
        }
        return captcha.verify(captchaKey, userInput) ? VerifyResult.pass() : VerifyResult.fail();
    }

    // ==================== 查询 ====================

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

    public CaptchaProperties getProperties() {
        return properties;
    }

    public void setProperties(CaptchaProperties properties) {
        this.properties = properties;
        // 同步更新所有已注册的 AbstractCaptcha 实例
        for (Captcha captcha : captchas.values()) {
            if (captcha instanceof AbstractCaptcha) {
                ((AbstractCaptcha) captcha).setProperties(properties);
            }
        }
    }

    // ==================== 静态方法 ====================

    /**
     * 获取静态默认实例（延迟初始化，线程安全）。
     *
     * @return 默认管理器实例
     */
    public static CaptchaManager getDefault() {
        if (defaultInstance == null) {
            synchronized (CaptchaManager.class) {
                if (defaultInstance == null) {
                    defaultInstance = createDefault();
                }
            }
        }
        return defaultInstance;
    }

    /**
     * 设置静态默认实例。
     *
     * @param manager 管理器实例
     */
    public static void setDefault(CaptchaManager manager) {
        defaultInstance = manager;
    }

    /**
     * 静态方法：生成验证码（使用默认实例）。
     *
     * @param type 验证码类型
     * @return 生成结果
     */
    public static CaptchaResult generateStatic(String type) {
        return getDefault().generate(type);
    }

    /**
     * 静态方法：生成验证码（带运行时配置覆盖）。
     *
     * @param type      验证码类型
     * @param overrides 运行时配置覆盖
     * @return 生成结果
     */
    public static CaptchaResult generateStatic(String type, CaptchaProperties overrides) {
        return getDefault().generate(type, overrides);
    }

    /**
     * 静态方法：生成验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param type          验证码类型
     * @param overrides     运行时配置覆盖
     * @param encryptionKey 运行时加密密钥
     * @return 生成结果
     */
    public static CaptchaResult generateStatic(String type, CaptchaProperties overrides, String encryptionKey) {
        return getDefault().generate(type, overrides, encryptionKey);
    }

    /**
     * 静态方法：验证验证码（使用默认实例）。
     *
     * @param type       验证码类型
     * @param captchaKey 验证码标识
     * @param userInput  用户输入
     * @return 是否通过
     */
    public static boolean verifyStatic(String type, String captchaKey, String userInput) {
        return getDefault().verify(type, captchaKey, userInput);
    }

    /**
     * 静态方法：验证验证码（带运行时加密密钥）。
     *
     * @param type          验证码类型
     * @param captchaKey    验证码标识
     * @param userInput     用户输入
     * @param encryptionKey 运行时加密密钥
     * @return 是否通过
     */
    public static boolean verifyStatic(String type, String captchaKey, String userInput, String encryptionKey) {
        return getDefault().verify(type, captchaKey, userInput, encryptionKey);
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建默认管理器：使用默认配置，注册数字、算术、滑动、旋转、文字点选五种验证码。
     * <p>
     * 五种验证码共享同一 {@link CaptchaProperties}。
     *
     * @return 默认管理器
     */
    public static CaptchaManager createDefault() {
        CaptchaProperties properties = CaptchaProperties.createDefault();
        CaptchaManager manager = new CaptchaManager(properties);
        manager.register(new NumberCaptcha(properties));
        manager.register(new ArithmeticCaptcha(properties));
        manager.register(new SliderCaptcha(properties));
        manager.register(new RotateCaptcha(properties));
        manager.register(new ClickCaptcha(properties));
        return manager;
    }
}
