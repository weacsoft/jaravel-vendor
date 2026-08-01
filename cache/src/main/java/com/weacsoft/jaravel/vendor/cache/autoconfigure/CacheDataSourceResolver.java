package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * 缓存模块的数据源解析器：<b>先找 jaravel database 模块的连接，找不到再找 Spring 容器</b>。
 *
 * <h3>为什么不用 {@code @ConditionalOnBean(DataSource.class)}</h3>
 * cache 模块不应与 Spring 的 {@code DataSource} Bean 强绑定：
 * <ul>
 *   <li>jaravel 的连接通过 {@code @RegisterConnection} 注册在自己的
 *       {@code ConnectionManager} 里，未必以 Spring Bean 形式存在；</li>
 *   <li>{@code @ConditionalOnBean} 在自动配置阶段求值，时序脆弱，
 *       容易出现「明明有连接却判定为没有」。</li>
 * </ul>
 * 因此改为运行时惰性解析：只有真的用到 {@code driver: database} 才解析，
 * 且解析顺序与 Model 的连接解析保持一致。
 *
 * <h3>对 database 模块的软依赖</h3>
 * cache 模块不强依赖 database 模块，这里通过反射桥接。
 * 未引入 database 模块时自动降级为「只查 Spring 容器」。
 */
public class CacheDataSourceResolver implements Supplier<DataSource> {

    private static final String CONNECTION_MANAGER =
            "com.weacsoft.jaravel.vendor.database.ConnectionManager";

    private final ApplicationContext context;

    public CacheDataSourceResolver(ApplicationContext context) {
        this.context = context;
    }

    /**
     * 解析默认数据源。
     *
     * @return 数据源，两处都找不到时返回 {@code null}
     */
    @Override
    public DataSource get() {
        DataSource fromJaravel = fromConnectionManager();
        if (fromJaravel != null) {
            return fromJaravel;
        }
        return fromSpring();
    }

    /**
     * 从 database 模块的连接注册表取默认连接的原始数据源。
     *
     * @return 数据源，模块不存在或无连接时返回 {@code null}
     */
    private DataSource fromConnectionManager() {
        try {
            Class<?> managerClass = Class.forName(CONNECTION_MANAGER);
            return (DataSource) managerClass.getMethod("defaultRawDataSource").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 回退到 Spring 容器中的 {@link DataSource} Bean。
     *
     * @return 数据源，容器中没有时返回 {@code null}
     */
    private DataSource fromSpring() {
        ObjectProvider<DataSource> provider = context.getBeanProvider(DataSource.class);
        return provider.getIfAvailable();
    }
}
