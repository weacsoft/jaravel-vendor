package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据库缓存驱动工厂，支持 {@code "database"} 驱动名。
 * <p>
 * 需要注入 {@link DataSource}，从配置中读取 {@code table}（缓存表名，默认 {@code jaravel_cache}），
 * 创建 {@link DatabaseCacheDriver}。
 * <p>
 * 由 {@code CacheAutoConfiguration.DatabaseCacheConfiguration} 在 DataSource 存在时注册为 Bean。
 */
public class DatabaseCacheDriverFactory implements CacheDriverFactory {

    private final DataSource dataSource;

    public DatabaseCacheDriverFactory(DataSource dataSource) {
        this.dataSource = dataSource;
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
        return new DatabaseCacheDriver(dataSource, tableName);
    }
}
