package com.weacsoft.jaravel.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jaravel.listener")
public class ListenerProperties {
    
    private boolean queueEnabled = false;
    
    public boolean isQueueEnabled() {
        return queueEnabled;
    }
    
    public void setQueueEnabled(boolean queueEnabled) {
        this.queueEnabled = queueEnabled;
    }
}
