package com.weacsoft.jaravel.vendor.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 迁移配置，前缀 {@code jaravel.migration}。
 * <pre>
 * jaravel:
 *   migration:
 *     enabled: true
 *     table: migrations
 *     auto-run: false   # 启动时是否自动执行 migrate
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.migration")
public class MigrationProperties {

    /** 是否启用迁移模块 */
    private boolean enabled = true;

    /** 迁移记录表名 */
    private String table = "migrations";

    /** 启动时是否自动执行 migrate */
    private boolean autoRun = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public boolean isAutoRun() { return autoRun; }
    public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }
}
