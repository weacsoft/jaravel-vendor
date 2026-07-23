package com.weacsoft.jaravel.vendor.queue.database.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseAutoConfiguration;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;
import com.weacsoft.jaravel.vendor.queue.database.QueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 队列模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）和
 * {@link DataSource}（数据库可用）时，自动注册 {@code queue:table} 命令为
 * Artisan 命令 Bean。
 * <p>
 * 注册的命令：
 * <ul>
 *   <li>{@code queue:table} — 创建队列任务表（jobs / failed_jobs）</li>
 * </ul>
 * <p>
 * 该命令在任意 driver 设置下均可执行，方便提前建表后切换到 database 驱动。
 */
@AutoConfiguration
@AutoConfigureAfter(QueueDatabaseAutoConfiguration.class)
@ConditionalOnClass(ArtisanCommand.class)
@ConditionalOnBean(DataSource.class)
public class QueueArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QueueArtisanAutoConfiguration.class);

    @Bean
    public QueueTableCommand queueTableCommand(DataSource dataSource,
                                               QueueDatabaseProperties dbProps,
                                               QueueProperties queueProps) {
        log.info("[queue-artisan] 注册命令: queue:table");
        return new QueueTableCommand(dataSource, dbProps, queueProps);
    }
}
