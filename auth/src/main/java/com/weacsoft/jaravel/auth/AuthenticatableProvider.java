package com.weacsoft.jaravel.auth;

import java.util.HashMap;
import java.util.Map;

public interface AuthenticatableProvider {

    //通过id获取用户
    Authenticatable authById(String identifier);

    //通过特定信息获取用户
    Authenticatable authByCredentials(Map<String, String> credentials);

    //通过“记住用户”token获取用户
    default Authenticatable authByRememberToken(String rememberToken) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("rememberToken", rememberToken);
        return authByCredentials(credentials);
    }

    //验证信息是否和用户匹配
    default boolean validateCredentials(Authenticatable user, Map<String, String> credentials) {
        //判断放进来的用户的信心是否和认证拿到的信息匹配
        return user.equals(authByCredentials(credentials));
    }

    //获得认证用的id
    String getAuthIdentifier(Authenticatable user);

    //更新长时token
    void updateRememberToken(Authenticatable user, String token);
}
