package com.weacsoft.jaravel.vendor.schedule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定时任务配置属性，前缀 {@code jaravel.schedule}。
 * <pre>
 * jaravel:
 *   schedule:
 *     enabled: true    # 是否启用定时任务调度
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.schedule")
public class ScheduleProperties {

    /** 是否启用定时任务调度 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
