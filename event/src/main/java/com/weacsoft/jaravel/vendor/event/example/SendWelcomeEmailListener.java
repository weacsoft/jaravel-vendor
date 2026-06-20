package com.weacsoft.jaravel.vendor.event.example;

import com.weacsoft.jaravel.vendor.event.Listener;
import com.weacsoft.jaravel.vendor.event.ShouldQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例监听器：发送欢迎邮件。
 * <p>
 * 实现 {@link ShouldQueue}，因此会被异步分发到 "email" 队列执行，
 * 对齐 Laravel 中实现 {@code ShouldQueue} 的队列化监听器。
 */
public class SendWelcomeEmailListener implements Listener<UserRegisteredEvent>, ShouldQueue {

    private static final Logger log = LoggerFactory.getLogger(SendWelcomeEmailListener.class);

    @Override
    public void handle(UserRegisteredEvent event) {
        log.info("[队列] 发送欢迎邮件给用户 {} ({})", event.getName(), event.getUserId());
    }

    @Override
    public String queue() {
        return "email";
    }
}
