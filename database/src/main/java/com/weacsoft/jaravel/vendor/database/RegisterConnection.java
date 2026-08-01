package com.weacsoft.jaravel.vendor.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册数据库连接（{@code GaarasonDataSource}），替代 {@code @Bean} 方式。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回
 * {@link gaarason.database.contract.connection.GaarasonDataSource GaarasonDataSource}
 * （也可返回 {@link javax.sql.DataSource}，框架会用统一的 {@code ContainerBootstrap} 自动包装）。
 * {@link com.weacsoft.jaravel.vendor.database.autoconfigure.ConnectionRegistrar ConnectionRegistrar}
 * 会在所有 Bean 初始化完成后扫描此注解，调用方法并按 {@link #value()} 指定的别名注册到
 * {@link ConnectionManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean("mysql")} 的 bean name 在整个 Spring 容器内必须唯一。如果另一处也有
 * {@code @Bean("mysql")}（返回不同类型），Spring Boot 会抛出
 * {@code BeanDefinitionOverrideException}。使用本注解后，连接别名与 bean name 解耦，
 * 不会注册为 Spring Bean，因此不会与同名 bean 冲突。这与 auth 模块的
 * {@code @RegisterGuard} / cache 模块的 {@code @RegisterCacheStore} 是同一套机制。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class DatabaseConfig {
 *
 *     // 主连接：defaultConnection = true，Model 未指定别名时使用
 *     &#64;RegisterConnection(value = "primary", defaultConnection = true)
 *     public GaarasonDataSource primaryConnection(ContainerBootstrap bootstrap, Environment env) {
 *         DruidDataSource druid = new DruidDataSource();
 *         druid.setUrl(env.getProperty("spring.datasource.url"));
 *         return GaarasonDataSourceBuilder.build(druid, bootstrap);
 *     }
 *
 *     // 额外连接：别名 "mysql"，Model 通过 &#64;DataSource("mysql") 使用
 *     &#64;RegisterConnection("mysql")
 *     public GaarasonDataSource mysqlConnection(ContainerBootstrap bootstrap) {
 *         DruidDataSource druid = new DruidDataSource();
 *         druid.setUrl("jdbc:mysql://127.0.0.1:3306/demo");
 *         return GaarasonDataSourceBuilder.build(druid, bootstrap);
 *     }
 * }
 * </pre>
 *
 * <h3>方法参数注入</h3>
 * 方法可声明任意参数，{@code ConnectionRegistrar} 会从 Spring 容器中按类型自动解析注入，
 * 行为与 {@code @Bean} 方法的参数注入一致。因此可直接声明
 * {@code ContainerBootstrap} 参数拿到<b>全局唯一</b>的 gaarason 容器。
 *
 * <h3>ContainerBootstrap 唯一性</h3>
 * gaarason 在 SpringBoot 环境下要求所有 {@code GaarasonDataSource} 共用<b>同一个</b>
 * {@code ContainerBootstrap}。框架通过 {@link DB#container()} 持有全局实例，
 * 若本注解方法返回的是裸 {@link javax.sql.DataSource}，框架会自动用该全局实例包装，
 * 从根本上杜绝「多个 Container 导致 Model 解析错乱」的问题。
 *
 * @see ConnectionManager#addConnection(String, gaarason.database.contract.connection.GaarasonDataSource)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterConnection {

    /**
     * 连接别名，用于 {@code DB.connection(name)} 或 Model 的 {@code @DataSource(name)} 解析。
     *
     * @return 连接别名
     */
    String value();

    /**
     * 是否设为默认连接。
     * <p>
     * 设为 {@code true} 时，等效于调用 {@link ConnectionManager#setDefaultConnection(String)}，
     * Model 未声明别名时将使用此连接。
     * <p>
     * 若多个 {@code @RegisterConnection} 同时标记 {@code defaultConnection = true}，
     * 最后注册的生效。
     *
     * @return 是否为默认连接，默认 {@code false}
     */
    boolean defaultConnection() default false;
}
