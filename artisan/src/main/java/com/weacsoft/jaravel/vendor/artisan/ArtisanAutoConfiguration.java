package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.artisan.key.KeyGenerateCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeAllCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCommandCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeControllerCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeEventCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeListenerCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeMiddlewareCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeMigrationCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeModelCommand;
import com.weacsoft.jaravel.vendor.artisan.vendor.VendorPublishCommand;
import com.weacsoft.jaravel.vendor.artisan.vendor.VendorPublishStaticCommand;
import com.weacsoft.jaravel.vendor.core.publish.AppPublishableConfig;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Artisan 自动装配。
 * <p>
 * 创建 {@link ArtisanApplication} bean，自动从 Spring 容器发现所有 {@link ArtisanCommand} bean。
 * <p>
 * 同时注册 8 个 {@code make:xxx} 代码生成命令和 {@link MakeCodeProperties} 配置。
 * <p>
 * 业务方在主类中通过 {@link ArtisanRunner#isArtisanMode(String[])} 检测 artisan 模式，
 * 并调用 {@link ArtisanRunner#run(ArtisanApplication, String[])} 执行命令。
 */
@AutoConfiguration
@ConditionalOnClass(ArtisanApplication.class)
public class ArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ArtisanAutoConfiguration.class);

    /**
     * Artisan 应用 bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public ArtisanApplication artisanApplication(ApplicationContext applicationContext) {
        return new ArtisanApplication(applicationContext);
    }

    /**
     * 代码生成配置 bean，绑定 {@code jaravel.artisan.make.*} 配置。
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.artisan.make")
    public MakeCodeProperties makeCodeProperties() {
        return new MakeCodeProperties();
    }

    // ==================== make:xxx 命令注册 ====================

    @Bean
    public MakeControllerCommand makeControllerCommand(MakeCodeProperties properties) {
        MakeControllerCommand cmd = new MakeControllerCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeMiddlewareCommand makeMiddlewareCommand(MakeCodeProperties properties) {
        MakeMiddlewareCommand cmd = new MakeMiddlewareCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeModelCommand makeModelCommand(MakeCodeProperties properties) {
        MakeModelCommand cmd = new MakeModelCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeMigrationCommand makeMigrationCommand(MakeCodeProperties properties) {
        MakeMigrationCommand cmd = new MakeMigrationCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeCommandCommand makeCommandCommand(MakeCodeProperties properties) {
        MakeCommandCommand cmd = new MakeCommandCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeEventCommand makeEventCommand(MakeCodeProperties properties) {
        MakeEventCommand cmd = new MakeEventCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeListenerCommand makeListenerCommand(MakeCodeProperties properties) {
        MakeListenerCommand cmd = new MakeListenerCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    @Bean
    public MakeAllCommand makeAllCommand(MakeCodeProperties properties) {
        MakeAllCommand cmd = new MakeAllCommand();
        cmd.setProperties(properties);
        return cmd;
    }

    // ==================== key:generate 命令注册 ====================

    /**
     * {@code key:generate} 命令：生成 Base64 全局应用密钥并写入 application 配置。
     * <p>
     * 生成的密钥对应 core 模块的 {@code jaravel.key}，是 captcha / jwt / cookies
     * 等模块的兜底主密钥。
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyGenerateCommand keyGenerateCommand(MakeCodeProperties properties) {
        return new KeyGenerateCommand(properties);
    }

    // ==================== vendor:publish 命令注册 ====================

    /**
     * {@code vendor:publish} 命令。
     * <p>
     * 通过 {@link ObjectProvider} 收集容器中所有 {@link PublishableConfig}，
     * 实现「有则发布，无则提示」：未引入任何声明可发布配置的模块时，
     * 列表为空，命令不报错。
     */
    /**
     * 声明 {@code config/AppConfig.java} 为可发布配置。
     * <p>
     * AppConfig 属于 core 模块的产物，但 core 不含自动配置类，
     * 故在此注册（artisan 必然依赖 core，且 {@code vendor:publish} 由本模块提供）。
     * 这样执行 {@code artisan vendor:publish --all} 时会一并发布 AppConfig，
     * 其中完整保留 session / router / auth / cache 等全部访问器方法。
     *
     * @return 可发布配置模板
     */
    @Bean
    @ConditionalOnMissingBean(AppPublishableConfig.class)
    public AppPublishableConfig appPublishableConfig() {
        return new AppPublishableConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public VendorPublishCommand vendorPublishCommand(ObjectProvider<PublishableConfig> publishables,
                                                     MakeCodeProperties properties) {
        List<PublishableConfig> configs = publishables.orderedStream().collect(Collectors.toList());
        log.debug("[Artisan] vendor:publish 发现 {} 个可发布配置", configs.size());
        return new VendorPublishCommand(configs, properties);
    }

    // ==================== vendor:publish:static 命令注册 ====================

    /**
     * {@code vendor:publish:static} 命令。
     * <p>
     * 只收集 {@link PublishableStatic}，与 {@link #vendorPublishCommand} 使用的
     * {@link PublishableConfig} 完全隔离：执行 {@code vendor:publish} 不会触发静态资源发布。
     */
    @Bean
    @ConditionalOnMissingBean
    public VendorPublishStaticCommand vendorPublishStaticCommand(ObjectProvider<PublishableStatic> publishables,
                                                                 MakeCodeProperties properties) {
        List<PublishableStatic> statics = publishables.orderedStream().collect(Collectors.toList());
        log.debug("[Artisan] vendor:publish:static 发现 {} 个静态资源发布器", statics.size());
        return new VendorPublishStaticCommand(statics, properties);
    }
}
