package com.weacsoft.jaravel.vendor.springboot.storage;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.RegisterCommand;
import com.weacsoft.jaravel.vendor.storage.database.artisan.StorageTableCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Storage 模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）和
 * {@link StorageTableCommand}（storage-database 模块的建表命令）时，
 * 自动注册 {@code storage:table} 命令。
 * <p>
 * 命令通过 {@link RegisterCommand} 注解注册，<b>不作为 Spring Bean</b>，
 * 而是由 CommandRegistrar 扫描后注册到 ArtisanApplication 内部注册表。
 */
@AutoConfiguration
@AutoConfigureAfter(StorageAutoConfiguration.class)
@ConditionalOnClass({ArtisanCommand.class, StorageTableCommand.class})
public class StorageArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageArtisanAutoConfiguration.class);

    /**
     * 注册 {@code storage:table} 命令（生成建表迁移文件，不直接建表）。
     *
     * @return 建表命令
     */
    @RegisterCommand("生成 storage 数据库表迁移文件")
    public StorageTableCommand storageTableCommand() {
        log.info("[storage-artisan] 注册命令: storage:table");
        return new StorageTableCommand();
    }
}
