package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import com.weacsoft.jaravel.vendor.database.EloquentUserProviderDriver;
import com.weacsoft.jaravel.vendor.database.JaravelDataSource;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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
     * 注册 {@code @RegisterConnection} 扫描器。
     * <p>
     * 在所有单例就绪后扫描配置类中的
     * {@link com.weacsoft.jaravel.vendor.database.RegisterConnection @RegisterConnection}
     * 方法，把连接按别名登记到
     * {@link com.weacsoft.jaravel.vendor.database.ConnectionManager ConnectionManager}，
     * 并绑定全局唯一的 {@code ContainerBootstrap}。
     *
     * @param applicationContext Spring 上下文
     * @return 连接注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionRegistrar connectionRegistrar(ApplicationContext applicationContext) {
        return new ConnectionRegistrar(applicationContext);
    }

    /**
     * 把 {@code @RegisterConnection} 的<b>默认连接</b>暴露为 Spring 的 {@link javax.sql.DataSource} Bean。
     * <p>
     * 连接改用注解声明后，业务工程不再手写 {@code @Bean DataSource}，但 Spring 生态里
     * {@code DataSourceTransactionManager}、{@code JdbcTemplate} 以及各类
     * {@code @ConditionalOnBean(DataSource.class)} 仍需要容器中存在该类型的 Bean。
     * 这里注册 {@link JaravelDataSource} 惰性委托即可同时满足两者：
     * <ul>
     *   <li>Bean 本身可以很早创建，不会与 {@code @RegisterConnection} 的扫描时机冲突；</li>
     *   <li>真正取连接时才委托到 {@code ConnectionManager} 的默认连接。</li>
     * </ul>
     * 默认连接 = 标记了 {@code defaultConnection = true} 的连接；若一个都没标记，
     * 则第一个注册的连接自动成为默认连接。
     * <p>
     * 若业务工程自己定义了 {@code DataSource} Bean（历史写法），
     * {@code @ConditionalOnMissingBean} 会让本 Bean 自动让位，保持向后兼容。
     *
     * @return 默认连接的惰性委托数据源
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(javax.sql.DataSource.class)
    public JaravelDataSource jaravelDataSource() {
        return new JaravelDataSource();
    }

    /**
     * 声明 {@code config/DatabaseConfig.java} 为可发布配置。
     * <p>
     * 使 {@code artisan vendor:publish --tag=database} 能发布数据库配置类。
     *
     * @return 可发布配置模板
     */
    @Bean
    @ConditionalOnMissingBean(DatabasePublishableConfig.class)
    public DatabasePublishableConfig databasePublishableConfig() {
        return new DatabasePublishableConfig();
    }

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
