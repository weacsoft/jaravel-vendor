package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.event.ListenerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ListenerProperties.class)
public class ListenerAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ListenerService listenerService(ListenerProperties listenerProperties) {
        ListenerService listenerService = ListenerService.getInstance();
        if (listenerProperties.isQueueEnabled()) {
            listenerService.enableQueue();
        }
        return listenerService;
    }
}
