package com.weacsoft.jaravel.vendor.springboot.queuedatabase;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.RegisterCommand;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;
import com.weacsoft.jaravel.vendor.queue.database.artisan.QueueTableCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 队列模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中存在 {@link ArtisanCommand}（artisan 模块）时，
 * 通过 {@link RegisterCommand} 注解注册 {@code queue:table} 命令。
 * 命令实例<b>不作为 Spring Bean</b>，由 CommandRegistrar 扫描后注册到 ArtisanApplication。
 * <p>
 * 注册的命令：
 * <ul>
 *   <li>{@code queue:table} — 生成队列任务表迁移文件（jobs / failed_jobs）</li>
 * </ul>
 * <p>
 * 该命令在任意 driver 设置下均可执行，方便提前生成迁移后切换到 database 驱动。
 */
@AutoConfiguration
@AutoConfigureAfter(QueueDatabaseAutoConfiguration.class)
@ConditionalOnClass({ArtisanCommand.class, QueueTableCommand.class})
public class QueueArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QueueArtisanAutoConfiguration.class);

    @RegisterCommand("生成队列任务表迁移文件")
    public QueueTableCommand queueTableCommand(QueueDatabaseProperties dbProps) {
        log.info("[queue-artisan] 注册命令: queue:table");
        return new QueueTableCommand(dbProps);
    }
}
