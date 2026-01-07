package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.event.ListenerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration

public class ListenerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ListenerService listenerService() {
        ListenerService listenerService = ListenerService.getInstance();
        return listenerService;
    }
}
