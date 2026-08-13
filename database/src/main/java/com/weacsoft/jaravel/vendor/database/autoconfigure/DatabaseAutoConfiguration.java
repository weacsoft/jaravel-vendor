package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.database.JaravelDataSource;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * 数据库模块核心自动装配（不依赖 auth）。
 * <p>
 * 只要引入 {@code database} 模块即生效，负责注册：
 * <ul>
 *   <li>{@code @RegisterConnection} 扫描器（{@link com.weacsoft.jaravel.vendor.database.ConnectionRegistrar}）</li>
 *   <li>默认连接的惰性委托数据源 {@link JaravelDataSource}（暴露为 {@link javax.sql.DataSource}）</li>
 *   <li>可发布配置 {@code DatabasePublishableConfig}（{@code vendor:publish --tag=database}）</li>
 *   <li>{@code ModelShadow} 修复器</li>
 * </ul>
 * <p>
 * <b>不再依赖 auth 模块</b>：auth 的 {@code EloquentUserProviderDriver} 由独立的
 * {@link com.weacsoft.jaravel.vendor.database.autoconfigure.EloquentUserProviderAutoConfiguration}
 * 在检测到 auth 存在时才注册，避免「未引入 auth 却用数据库」时整个数据库装配被禁用。
 */
@AutoConfiguration
public class DatabaseAutoConfiguration {
    static {
        PublishableRegistry.register(new DatabasePublishableConfig());
    }

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
