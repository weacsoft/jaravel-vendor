package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.auth.Auth;
import com.weacsoft.jaravel.auth.AuthManager;
import com.weacsoft.jaravel.auth.EloquentUserProvider;
import com.weacsoft.jaravel.auth.UserProvider;
import com.weacsoft.jaravel.http.request.RequestFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserProvider userProvider() {
        return new EloquentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthManager authManager(UserProvider userProvider) {
        return new AuthManager(RequestFactory.getCurrentRequest(), userProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Auth auth(AuthManager authManager) {
        Auth.setManager(authManager);
        return new Auth();
    }
}
