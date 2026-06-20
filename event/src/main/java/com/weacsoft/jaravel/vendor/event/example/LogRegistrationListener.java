package com.weacsoft.jaravel.vendor.event.example;

import com.weacsoft.jaravel.vendor.event.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例监听器：记录用户注册日志。
 * <p>
 * 未实现 {@link com.weacsoft.jaravel.vendor.event.ShouldQueue}，因此会被同步执行，
 * 对齐 Laravel 中普通的同步监听器。
 */
public class LogRegistrationListener implements Listener<UserRegisteredEvent> {

    private static final Logger log = LoggerFactory.getLogger(LogRegistrationListener.class);

    @Override
    public void handle(UserRegisteredEvent event) {
        log.info("[同步] 记录用户注册日志: {} ({})", event.getName(), event.getUserId());
    }
}
