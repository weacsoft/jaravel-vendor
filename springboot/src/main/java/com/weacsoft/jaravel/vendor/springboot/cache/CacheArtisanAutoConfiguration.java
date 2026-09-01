package com.weacsoft.jaravel.vendor.springboot.cache;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.RegisterCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.cache.database.artisan.CacheTableCommand;
import com.weacsoft.jaravel.vendor.cache.database.DatabaseCacheDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 缓存模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）和
 * {@link DatabaseCacheDriver}（cache-database 模块的 database 驱动）时，
 * 通过 {@link RegisterCommand} 注解注册 {@code cache:table} 命令。
 * 命令实例<b>不作为 Spring Bean</b>，由 CommandRegistrar 扫描后注册到 ArtisanApplication。
 * <p>
 * 注册的命令：
 * <ul>
 *   <li>{@code cache:table} — 生成数据库缓存表迁移文件</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(CacheAutoConfiguration.class)
@ConditionalOnClass({ArtisanCommand.class, DatabaseCacheDriver.class})
public class CacheArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheArtisanAutoConfiguration.class);

    /**
     * 注册 {@code cache:table} 命令。
     *
     * @param properties  缓存配置（用于获取表名）
     * @param makeProps   artisan 代码生成配置（决定迁移文件落盘目录与包名，与 vendor:publish 一致）
     * @return 缓存建表命令
     */
    @RegisterCommand("生成缓存表迁移文件")
    public CacheTableCommand cacheTableCommand(CacheProperties properties, MakeCodeProperties makeProps) {
        log.info("[cache-artisan] 注册命令: cache:table");
        return new CacheTableCommand(properties.getDatabaseTable(), makeProps);
    }
}
