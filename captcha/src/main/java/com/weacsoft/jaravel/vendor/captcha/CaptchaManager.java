package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.generator.AbstractCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.ArithmeticCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.Captcha;
import com.weacsoft.jaravel.vendor.captcha.generator.ClickCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.RotateCaptcha;
import com.weacsoft.jaravel.vendor.captcha.generator.SliderCaptcha;
import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.store.MemoryCaptchaStore;
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
 *   // 下发给前端的是「合并凭证」result.getKey()（格式 type.captchaKey）
 *   boolean ok = manager.verify(result.getKey(), userInput);
 * </pre>
 * <p>
 * 校验接口<b>只接收两个参数</b>：合并凭证 + 用户输入。验证码类型已编码在凭证里，
 * 因此业务方可以把验证码与登录表单等字段一次性提交、一次性校验，
 * 不需要「先验证码、后业务」的两段式请求（那种做法存在可被重放的时间窗）。
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
 *   boolean ok = manager.verify(result.getKey(), userInput, "my-secret-key");
 * </pre>
 * <p>
 * <h3>静态调用</h3>
 * <pre>
 *   CaptchaResult result = CaptchaManager.generateStatic("number");
 *   boolean ok = CaptchaManager.verifyStatic(result.getKey(), userInput);
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

    /**
     * 注销指定类型的验证码实现（运行时动态移除）。
     * <p>
     * 配合 {@link #register(Captcha)} 即可在运行时动态增删验证码类型，
     * 实现「各类型相互独立、可插拔」。注销后该类型不再参与生成 / 校验。
     *
     * @param type 要注销的验证码类型
     * @return 被注销的实现（不存在返回 {@code null}）
     */
    public Captcha unregister(String type) {
        if (type == null) {
            return null;
        }
        return captchas.remove(type);
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

    // ==================== 验证（合并凭证，唯一入口） ====================

    /**
     * 用<b>合并凭证</b>校验验证码（使用默认配置，自动解密加密输入）。
     * <p>
     * 合并凭证 {@code key} 由生成时下发（{@link CaptchaResult#getKey()}，
     * 格式 {@code type + "." + captchaKey}），本身已包含验证码类型，
     * 因此校验只需要「凭证 + 用户输入」两个参数。
     * <p>
     * <b>为什么合并：</b>拆成 {@code type} / {@code captchaKey} 两个参数时，
     * 前端往往被迫先单独调一次「校验验证码」接口、通过后再提交业务表单，
     * 两次请求之间存在可被利用的时间窗（拿到"验证通过"状态后重放业务请求）。
     * 合并成单一凭证后，验证码可以和用户名、密码等字段<b>一次性提交</b>，
     * 服务端在同一个事务里完成校验，从根本上消除这个漏洞。
     *
     * @param key       合并凭证（生成时返回的 {@code key} 字段）
     * @param userInput 用户输入（可能是加密的）
     * @return 是否通过；凭证格式非法或类型未注册返回 {@code false}
     */
    public boolean verify(String key, String userInput) {
        return verifyDetailed(key, userInput, null, null).isPassed();
    }

    /**
     * 用合并凭证校验验证码（带运行时加密密钥）。
     *
     * @param key           合并凭证
     * @param userInput     用户输入
     * @param encryptionKey 运行时加密密钥（null 表示使用配置中的密钥）
     * @return 是否通过
     */
    public boolean verify(String key, String userInput, String encryptionKey) {
        return verifyDetailed(key, userInput, null, encryptionKey).isPassed();
    }

    /**
     * 用合并凭证校验验证码（带运行时配置覆盖和加密密钥）。
     *
     * @param key           合并凭证
     * @param userInput     用户输入
     * @param overrides     运行时配置覆盖
     * @param encryptionKey 运行时加密密钥
     * @return 是否通过
     */
    public boolean verify(String key, String userInput,
                          CaptchaProperties overrides, String encryptionKey) {
        return verifyDetailed(key, userInput, overrides, encryptionKey).isPassed();
    }

    /**
     * 用<b>合并凭证</b>校验验证码，返回详细结果（含是否已被使用）。
     * <p>
     * 合并凭证格式为 {@code type + "." + captchaKey}；本方法解析出类型后
     * 分发到对应生成器，调用方无需再传 {@code type}。
     *
     * @param key           合并凭证（生成时返回的 {@code key} 字段）
     * @param userInput     用户输入
     * @param overrides     运行时配置覆盖
     * @param encryptionKey 运行时加密密钥
     * @return 验证结果（含是否通过、是否已被使用）
     */
    public VerifyResult verifyDetailed(String key, String userInput,
                          CaptchaProperties overrides, String encryptionKey) {
        if (key == null) {
            return VerifyResult.fail();
        }
        int idx = key.indexOf('.');
        if (idx <= 0 || idx == key.length() - 1) {
            return VerifyResult.fail();
        }
        String type = key.substring(0, idx);
        String captchaKey = key.substring(idx + 1);

        Captcha captcha = captchas.get(type);
        if (captcha == null) {
            return VerifyResult.fail();
        }
        if (captcha instanceof AbstractCaptcha) {
            return ((AbstractCaptcha) captcha).verify(captchaKey, userInput, overrides, encryptionKey);
        }
        return captcha.verify(captchaKey, userInput) ? VerifyResult.pass() : VerifyResult.fail();
    }

    /**
     * 用合并凭证校验验证码，返回详细结果（使用默认配置）。
     *
     * @param key       合并凭证
     * @param userInput 用户输入
     * @return 验证结果
     */
    public VerifyResult verifyDetailed(String key, String userInput) {
        return verifyDetailed(key, userInput, null, null);
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
     * 静态方法：用合并凭证校验验证码（使用默认实例）。
     *
     * @param key       合并凭证（生成时返回的 {@code key} 字段）
     * @param userInput 用户输入
     * @return 是否通过
     */
    public static boolean verifyStatic(String key, String userInput) {
        return getDefault().verify(key, userInput);
    }

    /**
     * 静态方法：用合并凭证校验验证码（带运行时加密密钥）。
     *
     * @param key           合并凭证
     * @param userInput     用户输入
     * @param encryptionKey 运行时加密密钥
     * @return 是否通过
     */
    public static boolean verifyStatic(String key, String userInput, String encryptionKey) {
        return getDefault().verify(key, userInput, encryptionKey);
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
