package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.database.ConnectionManager;
import com.weacsoft.jaravel.vendor.database.RegisterConnection;
import gaarason.database.bootstrap.ContainerBootstrap;
import gaarason.database.contract.connection.GaarasonDataSource;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * 扫描 {@link RegisterConnection} 注解方法，调用并注册到 {@link ConnectionManager}。
 * <p>
 * 继承 {@link AnnotationDrivenRegistrar}，在所有单例 Bean 初始化完成后执行扫描，
 * 从 Spring 容器按类型解析方法参数后反射调用，将返回的
 * {@link GaarasonDataSource} 按 {@link RegisterConnection#value()} 指定的别名注册。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>产物不注册为 {@code @Bean}，因此连接别名（如 {@code mysql}）不会与同名 bean 冲突；</li>
 *   <li>方法参数从 Spring 容器按类型自动注入，可直接声明 {@code ContainerBootstrap} 参数；</li>
 *   <li>若方法返回裸 {@link javax.sql.DataSource}，自动用<b>全局唯一</b>的
 *       {@code ContainerBootstrap} 包装，保证所有数据源共用同一容器。</li>
 * </ul>
 *
 * <h3>ContainerBootstrap 一致性校验</h3>
 * {@link #beforeScan()} 会先把容器中的 {@code ContainerBootstrap} bean 交给
 * {@link ConnectionManager#setContainer(ContainerBootstrap)}，确保后续所有包装动作
 * 都复用这一个实例；若业务工程误建了多个实例，会在此处快速失败并给出明确提示。
 */
public class ConnectionRegistrar extends AnnotationDrivenRegistrar<RegisterConnection> {

    public ConnectionRegistrar(ApplicationContext context) {
        super(context, RegisterConnection.class);
    }

    /**
     * 扫描前把 Spring 容器中的 {@code ContainerBootstrap} 绑定为全局唯一实例。
     */
    @Override
    protected void beforeScan() {
        ContainerBootstrap bootstrap = context.getBeanProvider(ContainerBootstrap.class).getIfAvailable();
        if (bootstrap != null) {
            ConnectionManager.setContainer(bootstrap);
            log.info("[database] 绑定全局 ContainerBootstrap: {}", bootstrap);
        }
    }

    /**
     * 登记 {@link RegisterConnection @RegisterConnection} 方法返回的数据库连接。
     */
    @Override
    protected void register(Object result, Method method, RegisterConnection annotation) {
        String alias = annotation.value();
        GaarasonDataSource dataSource = adapt(result, method);
        javax.sql.DataSource raw = (result instanceof javax.sql.DataSource)
                ? (javax.sql.DataSource) result : null;

        ConnectionManager.addConnection(alias, dataSource, raw);
        log.info("[database] @RegisterConnection 注册连接: name={}, type={}{}",
                alias, result.getClass().getSimpleName(),
                annotation.defaultConnection() ? " (默认)" : "");

        if (annotation.defaultConnection()) {
            ConnectionManager.setDefaultConnection(alias);
            log.info("[database] @RegisterConnection 设置默认连接: {}", alias);
        }
    }

    /**
     * 将方法返回值适配为 {@link GaarasonDataSource}。
     * <p>
     * 已是 {@code GaarasonDataSource} 则直接返回；若是裸 {@link javax.sql.DataSource}，
     * 用全局唯一的 {@code ContainerBootstrap} 包装。
     *
     * @param result 方法返回值
     * @param method 注解所在方法，用于异常信息
     * @return gaarason 数据源
     */
    private GaarasonDataSource adapt(Object result, Method method) {
        if (result instanceof GaarasonDataSource) {
            return (GaarasonDataSource) result;
        }
        if (result instanceof javax.sql.DataSource) {
            log.debug("[database] {} 返回裸 DataSource，使用全局 ContainerBootstrap 包装", describe(method));
            return ConnectionManager.wrap((javax.sql.DataSource) result);
        }
        return requireType(result, GaarasonDataSource.class, method);
    }
}
