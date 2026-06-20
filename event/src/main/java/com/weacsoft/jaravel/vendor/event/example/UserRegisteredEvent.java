package com.weacsoft.jaravel.vendor.event.example;

import com.weacsoft.jaravel.vendor.event.Event;

/**
 * 示例事件：用户注册，对齐 Laravel 中典型的 {@code UserRegistered} 事件。
 * <p>
 * 仅供模板项目参考，演示如何定义一个携带业务数据的事件。
 */
public class UserRegisteredEvent implements Event {

    private final Long userId;
    private final String name;

    public UserRegisteredEvent(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }
}
