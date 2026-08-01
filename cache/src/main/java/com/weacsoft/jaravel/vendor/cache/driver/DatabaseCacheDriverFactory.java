package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据库缓存驱动工厂，支持 {@code "database"} 驱动名。
 *
 * <h3>惰性 + 解耦</h3>
 * 工厂本身<b>不持有</b> {@link DataSource} 实例，而是持有一个 {@link Supplier}，
 * 只有当 {@code jaravel.cache.stores} 中真的配置了 {@code driver: database}、
 * 触发 {@link #create(Map)} 时，才去解析数据源。带来两个好处：
 * <ul>
 *   <li><b>不与 Spring 绑定</b>：解析顺序为「先查 jaravel database 模块的连接注册表，
 *       找不到再回退 Spring 容器」，与 Model 的连接解析语义完全一致；</li>
 *   <li><b>用上了才装配</b>：没用 database 缓存驱动时，不会因为缺少 {@code DataSource}
 *       而影响应用启动。</li>
 * </ul>
 *
 * @see com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheDataSourceResolver
 */
public class DatabaseCacheDriverFactory implements CacheDriverFactory {

    /** 数据源解析器，延迟到真正创建驱动时才调用。 */
    private final Supplier<DataSource> dataSourceSupplier;

    /**
     * 以固定数据源构建（测试或明确指定数据源时使用）。
     *
     * @param dataSource 数据源
     */
    public DatabaseCacheDriverFactory(DataSource dataSource) {
        this(() -> dataSource);
    }

    /**
     * 以惰性解析器构建（框架默认使用）。
     *
     * @param dataSourceSupplier 数据源解析器，在 {@link #create(Map)} 时调用
     */
    public DatabaseCacheDriverFactory(Supplier<DataSource> dataSourceSupplier) {
        this.dataSourceSupplier = dataSourceSupplier;
    }

    @Override
    public boolean support(String driver) {
        return "database".equalsIgnoreCase(driver);
    }

    @Override
    public CacheDriver create(Map<String, Object> config) {
        Object table = config.get("table");
        String tableName = (table != null && !table.toString().isEmpty())
                ? table.toString()
                : "jaravel_cache";

        Object connection = config.get("connection");
        DataSource dataSource = resolve(connection == null ? null : connection.toString());
        return new DatabaseCacheDriver(dataSource, tableName);
    }

    /**
     * 解析数据源；缺失时给出可操作的错误提示，而不是让应用在启动期莫名失败。
     *
     * @param connection store 配置里可选的 {@code connection} 连接别名
     * @return 数据源
     */
    private DataSource resolve(String connection) {
        DataSource dataSource = (connection == null || connection.isEmpty())
                ? dataSourceSupplier.get()
                : resolveByAlias(connection);
        if (dataSource == null) {
            throw new IllegalStateException(
                    "缓存 store 使用了 driver: database，但未找到可用的数据库连接"
                            + (connection == null ? "" : "（别名: " + connection + "）")
                            + "。请使用 @RegisterConnection 注册连接，"
                            + "或把该 store 的 driver 改为 array / file。");
        }
        return dataSource;
    }

    /**
     * 按别名解析连接，database 模块不存在时返回 {@code null}。
     *
     * @param alias 连接别名
     * @return 数据源
     */
    private DataSource resolveByAlias(String alias) {
        try {
            Class<?> managerClass = Class.forName(
                    "com.weacsoft.jaravel.vendor.database.ConnectionManager");
            return (DataSource) managerClass
                    .getMethod("rawDataSource", String.class)
                    .invoke(null, alias);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
