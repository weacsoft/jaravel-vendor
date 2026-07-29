package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.database.EloquentUserProviderDriver;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 数据库模块自动装配。
 * <p>
 * 注册 {@link EloquentUserProviderDriver}，使 auth 模块能通过配置式（{@code jaravel.auth.providers}
 * 中 {@code driver: eloquent}）自动创建 {@link com.weacsoft.jaravel.vendor.database.EloquentUserProvider}。
 * <p>
 * 同时注册 {@link ModelShadowPatcher}，修复 gaarason ORM 的 {@code model_shadow} 字段扫描 bug，
 * 使数据库表无需添加 {@code model_shadow} 列。
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

    /**
     * 注册 ModelShadow 修复器，在 Spring 容器就绪后从所有 Model 的 SELECT 列表中
     * 移除 gaarason 内部的 {@code model_shadow} 列。
     * <p>
     * {@link ModelShadowProvider} 是 gaarason 内部容器的 bean（非 Spring bean），
     * 修复器在运行时通过 {@link Model#getContainer()} 获取，此处不直接注入。
     *
     * @param applicationContext Spring 上下文（用于收集所有 Model Bean）
     * @return 修复器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ModelShadowProvider.class)
    @SuppressWarnings("rawtypes")
    public ModelShadowPatcher modelShadowPatcher(ApplicationContext applicationContext) {
        Map<String, Model> models = applicationContext.getBeansOfType(Model.class);
        return new ModelShadowPatcher(models);
    }
}
