package com.weacsoft.jaravel.vendor.springboot.database;

import com.weacsoft.jaravel.vendor.database.JaravelDataSource;
import com.weacsoft.jaravel.vendor.database.autoconfigure.ConnectionRegistrar;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.Map;

import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

/**
 * 数据库模块核心自动装配（不依赖 auth）。
 * <p>
 * 只要引入 {@code database} 模块即生效（D2 起装配位于 springboot 模块，database 模块零 Spring），负责注册：
 * <ul>
 *   <li>{@code @RegisterConnection} 扫描器（{@link ConnectionRegistrar}，纯类 + SmartInitializingSingleton 触发）</li>
 *   <li>默认连接的惰性委托数据源 {@link JaravelDataSource}（暴露为 {@link javax.sql.DataSource}）</li>
 *   <li>可发布配置 {@link com.weacsoft.jaravel.vendor.database.autoconfigure.DatabasePublishableConfig}（{@code vendor:publish --tag=database}，模板类留 database 模块）</li>
 *   <li>{@code ModelShadow} 修复器（{@link ModelShadowPatcher}）</li>
 *   <li>{@link BaseModelDataSourceBindingPostProcessor}——为所有 {@code BaseModel} Bean 自动绑定
 *       {@code GaarasonDataSource}（承接 D2 前 BaseModel 字段上的 {@code @Autowired @Lazy} 注入语义）</li>
 * </ul>
 * <p>
 * <b>不再依赖 auth 模块</b>：auth 的 {@code EloquentUserProviderDriver} 由
 * {@link EloquentUserProviderAutoConfiguration} 在检测到 auth 存在时才注册。
 */
@AutoConfiguration
public class DatabaseAutoConfiguration {
    static {
        PublishableRegistry.register(new com.weacsoft.jaravel.vendor.database.autoconfigure.DatabasePublishableConfig());
    }

    /**
     * 注册 {@code @RegisterConnection} 扫描器（D2/P3：core 纯扫描器，
     * 扫描时机由下方 SmartInitializingSingleton 触发，保持原「所有单例就绪后扫描」时序）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionRegistrar connectionRegistrar() {
        return new ConnectionRegistrar();
    }

    /**
     * 连接注册器扫描触发：所有单例初始化完成后执行 {@code @RegisterConnection} 扫描。
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton connectionRegistrarScanner(
            ConnectionRegistrar registrar) {
        return registrar::scan;
    }

    /**
     * 把 {@code @RegisterConnection} 的<b>默认连接</b>暴露为 Spring 的 {@link javax.sql.DataSource} Bean。
     * <p>
     * 详见 database 模块 {@code ConnectionManager} 说明：Bean 早期创建不冲突，
     * 真正取连接时才委托到默认连接的原始数据源；业务自定义 {@code DataSource} Bean 时自动让位。
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

    /**
     * BaseModel 数据源绑定后处理器：把所有 {@code BaseModel} Bean 的
     * {@code GaarasonDataSource} 字段绑定为容器的 {@code GaarasonDataSource} Bean。
     * <p>
     * 承接 D2 前 {@code BaseModel} 字段上的 {@code @Autowired @Lazy} 注入：
     * 字段本身保持纯 Java（setter 注入），Spring 注入机制收敛到本装配侧，
     * 容器中没有 {@code GaarasonDataSource} Bean 时跳过（与字段回退逻辑一致：
     * {@code getGaarasonDataSource()} 仍会按别名/默认连接经 {@code ConnectionManager} 解析）。
     */
    @Bean
    public static BaseModelDataSourceBindingPostProcessor baseModelDataSourceBindingPostProcessor() {
        return new BaseModelDataSourceBindingPostProcessor();
    }
}
