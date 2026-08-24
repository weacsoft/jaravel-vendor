package com.weacsoft.jaravel.vendor.captcha.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * 验证码模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=captcha} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/CaptchaConfig.java}。
 * 该配置类与 {@code application.yml} 的 {@code jaravel.captcha.*} 协同工作：
 * <ul>
 *   <li>{@code application.yml} 负责填写各项数值（宽度、难度、密钥等）；</li>
 *   <li>本文件负责把各项数值装配成 {@link com.weacsoft.jaravel.vendor.captcha.CaptchaManager}
 *       并注册五种验证码类型，业务方可以在此增删类型、注入自定义实现，
 *       实现「各类型相互独立、可插拔」。</li>
 * </ul>
 * <p>
 * 发布后 {@code CaptchaManager} 由本文件创建（{@code @ConditionalOnMissingBean} 保证不与
 * 框架自动装配冲突），其余 Bean（场景白名单、静态资源发布）仍由框架自动注册。
 */
public class CaptchaPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "captcha";
    }

    @Override
    public String className() {
        return "CaptchaConfig";
    }

    @Override
    public String description() {
        return "验证码管理器装配（五种类型注册，可插拔增删）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.cache.CacheStore;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.generator.ArithmeticCaptcha;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.generator.ClickCaptcha;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.generator.RotateCaptcha;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.generator.SliderCaptcha;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.springboot.CaptchaProperties;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.store.CacheStoreCaptchaStore;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;\n"
                + "import com.weacsoft.jaravel.vendor.captcha.store.MemoryCaptchaStore;\n"
                + "import org.springframework.beans.factory.ObjectProvider;\n"
                + "import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "/**\n"
                + " * 验证码配置，对齐 Laravel config/captcha.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=captcha} 发布生成，可自由修改。\n"
                + " * 实际数值来自 {@code application.yml} 的 {@code jaravel.captcha.*}；\n"
                + " * 本文件负责把数值装配成 {@link CaptchaManager} 并注册验证码类型。\n"
                + " *\n"
                + " * <h3>可插拔</h3>\n"
                + " * 在 {@code captchaManager()} 中增删 {@code manager.register(...)} 即可运行时切换\n"
                + " * 启用的验证码类型；也可传入自定义 {@link com.weacsoft.jaravel.vendor.captcha.generator.Captcha}\n"
                + " * 实现，做到各类型相互独立、可插拔。\n"
                + " *\n"
                + " * <h3>应用密钥兜底</h3>\n"
                + " * 若 {@code jaravel.captcha.encryption-key} 未显式配置（等于出厂默认值），\n"
                + " * 框架会自动回退到全局 {@code jaravel.key}（见 core 模块 {@code AppKey}）。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class CaptchaConfig {\n"
                + "\n"
                + "    /**\n"
                + "     * 装配验证码管理器，注册五种验证码类型。\n"
                + "     * <p>\n"
                + "     * 防复用存储：优先使用 cache 模块的 {@link CacheStore}（跨进程），\n"
                + "     * 否则回退到内存 {@link MemoryCaptchaStore}（单机）。\n"
                + "     *\n"
                +     "     * @param properties          SpringBoot 配置（绑定 jaravel.captcha.*）\n"
                + "     * @param cacheStoreProvider   jaravel CacheStore（可选）\n"
                + "     * @return 验证码管理器\n"
                + "     */\n"
                + "    @Bean\n"
                + "    @ConditionalOnMissingBean(CaptchaManager.class)\n"
                + "    public CaptchaManager captchaManager(CaptchaProperties properties,\n"
                + "                                         ObjectProvider<CacheStore> cacheStoreProvider) {\n"
                + "        com.weacsoft.jaravel.vendor.captcha.CaptchaProperties core = properties.toCoreProperties();\n"
                + "        CaptchaStore store = cacheStoreProvider.getIfAvailable() != null\n"
                + "                ? new CacheStoreCaptchaStore(cacheStoreProvider.getIfAvailable())\n"
                + "                : new MemoryCaptchaStore();\n"
                + "\n"
                + "        CaptchaManager manager = new CaptchaManager(store, core);\n"
                + "        manager.register(new NumberCaptcha(core));\n"
                + "        manager.register(new ArithmeticCaptcha(core));\n"
                + "        manager.register(new SliderCaptcha(core));\n"
                + "        manager.register(new RotateCaptcha(core));\n"
                + "        manager.register(new ClickCaptcha(core));\n"
                + "        return manager;\n"
                + "    }\n"
                + "}\n";
    }
}
