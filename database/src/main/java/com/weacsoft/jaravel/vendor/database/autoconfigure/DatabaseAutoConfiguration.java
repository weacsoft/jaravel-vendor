package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.database.EloquentUserProviderDriver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 数据库模块自动装配。
 * <p>
 * 注册 {@link EloquentUserProviderDriver}，使 auth 模块能通过配置式（{@code jaravel.auth.providers}
 * 中 {@code driver: eloquent}）自动创建 {@link com.weacsoft.jaravel.vendor.database.EloquentUserProvider}。
 * <p>
 * auth 模块的 {@code AuthAutoConfiguration} 会自动收集所有 {@link UserProviderDriver} Bean，
 * 无需手动注册。
 */
@AutoConfiguration
@ConditionalOnClass({UserProviderDriver.class, EloquentUserProviderDriver.class})
public class DatabaseAutoConfiguration {

    /**
     * 注册 Eloquent 用户提供者驱动。
     *
     * @param applicationContext Spring 上下文（用于获取 Model Bean）
     * @return 驱动实例
     */
    @Bean
    @ConditionalOnMissingBean
    public EloquentUserProviderDriver eloquentUserProviderDriver(ApplicationContext applicationContext) {
        return new EloquentUserProviderDriver(applicationContext);
    }
}
