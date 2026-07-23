package com.weacsoft.jaravel.vendor.queue.database.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.queue.database.DatabaseQueueDriver;
import com.weacsoft.jaravel.vendor.queue.database.QueueDatabaseProperties;
import com.weacsoft.jaravel.vendor.queue.database.QueueProperties;

import javax.sql.DataSource;

/**
 * Artisan 命令：{@code queue:table}，创建队列任务表。
 * <p>
 * 对齐 Laravel {@code php artisan queue:table}，在数据库中创建 {@code jobs} 与
 * {@code failed_jobs} 表（表名可通过 {@code jaravel.queue.database.table} 配置）。
 * <p>
 * 仅当使用 {@code database} 队列驱动时需要执行此命令。
 * 使用 {@code sync}（默认）或 {@code redis} 驱动时无需执行。
 * <p>
 * 命令在任意 driver 设置下均可执行，方便提前建表后切换到 database 驱动。
 * <p>
 * 当 queue-database 和 artisan 模块同时存在于 classpath 时，由
 * {@code QueueArtisanAutoConfiguration} 自动注册为 Spring Bean。
 */
public class QueueTableCommand extends ArtisanCommand {

    private final DataSource dataSource;
    private final QueueDatabaseProperties dbProps;
    private final QueueProperties queueProps;

    public QueueTableCommand(DataSource dataSource,
                             QueueDatabaseProperties dbProps,
                             QueueProperties queueProps) {
        this.dataSource = dataSource;
        this.dbProps = dbProps;
        this.queueProps = queueProps;
    }

    @Override
    public String signature() {
        return "queue:table";
    }

    @Override
    public String description() {
        return "创建队列任务表 jobs/failed_jobs（仅 database 驱动需要）";
    }

    @Override
    public int handle() {
        String table = dbProps.getTable();
        String failedTable = "failed_jobs";
        info("正在创建队列任务表: " + table + ", " + failedTable);

        // 创建临时驱动实例用于建表（不注册为 Bean，避免影响 driver=sync 时的默认行为）
        DatabaseQueueDriver driver = new DatabaseQueueDriver(
                dataSource, table, failedTable,
                dbProps.getRetryAfter(), queueProps.getFailedJobRetentionDays());
        boolean success = driver.createTable();

        if (success) {
            info("队列任务表创建成功: " + table + ", " + failedTable);
            info("提示：请将 jaravel.queue.driver 设置为 database 以使用数据库队列");
            return 0;
        } else {
            error("队列任务表创建失败，请检查数据库权限或表是否已存在");
            return 1;
        }
    }
}
