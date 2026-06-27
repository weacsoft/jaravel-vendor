package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.artisan.make.MakeAllCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCommandCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeControllerCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeEventCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeListenerCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeMiddlewareCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeMigrationCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeModelCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

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
}
