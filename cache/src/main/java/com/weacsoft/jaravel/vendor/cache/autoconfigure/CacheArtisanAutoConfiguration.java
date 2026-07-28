package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.cache.driver.DatabaseCacheDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 缓存模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）和
 * {@link DatabaseCacheDriver}（cache 模块的 database 驱动），且 {@link DataSource} 可用时，
 * 自动注册 {@code cache:table} 命令为 Artisan 命令 Bean。
 * <p>
 * 注册的命令：
 * <ul>
 *   <li>{@code cache:table} — 创建数据库缓存表</li>
 * </ul>
 * <p>
 * 命令在执行时才按需创建 {@link DatabaseCacheDriver}（对齐工厂模式 + 用到才构建）。
 */
@AutoConfiguration
@AutoConfigureAfter(CacheAutoConfiguration.class)
@ConditionalOnClass({ArtisanCommand.class, DatabaseCacheDriver.class})
@ConditionalOnBean(DataSource.class)
public class CacheArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheArtisanAutoConfiguration.class);

    /**
     * 注册 {@code cache:table} 命令。
     *
     * @param dataSource 数据源
     * @param properties 缓存配置（用于获取表名）
     * @return 缓存建表命令
     */
    @Bean
    public com.weacsoft.jaravel.vendor.cache.artisan.CacheTableCommand cacheTableCommand(
            DataSource dataSource, CacheProperties properties) {
        log.info("[cache-artisan] 注册命令: cache:table");
        return new com.weacsoft.jaravel.vendor.cache.artisan.CacheTableCommand(
                dataSource, properties.getDatabaseTable());
    }
}
