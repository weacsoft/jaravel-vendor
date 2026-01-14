package com.weacsoft.jaravel.auth;

import java.util.Map;

public class Guard {
    //guard的名字
    private String name;
    //存取唯一id的驱动
    private AuthDriver authDriver;
    //提供id到用户转换的服务
    private AuthenticatableProvider provider;
    //当前用户
    private final ThreadLocal<Authenticatable> currentUser = new ThreadLocal<>();

    public Guard(String name, AuthDriver driver, AuthenticatableProvider provider) {
        this.name = name;
        this.authDriver = driver;
        this.provider = provider;
    }

    //获得登录用户
    public Authenticatable user() {
        if (currentUser.get() == null) {
            String id = authDriver.getId();
            if (id == null) {
                return null;
            }
            currentUser.set(provider.authById(id));
        }
        return currentUser.get();
    }

    //登录
    public boolean login(Authenticatable user) {
        //设置使用的id
        authDriver.setId(user.getAuthIdentifier());
        currentUser.set(user);
        return true;
    }

    //登出
    public void logout() {
        authDriver.removeId();
        currentUser.remove();
    }

    //通过身份信息登录
    public boolean attempt(Map<String, String> credentials) {
        Authenticatable authenticate = provider.authByCredentials(credentials);
        if (authenticate == null) {
            return false;
        }
        return login(authenticate);
    }

    //一次登录（就是不写入）
    public boolean once(Map<String, String> credentials) {
        Authenticatable authenticate = provider.authByCredentials(credentials);
        if (authenticate == null) {
            return false;
        }
        return true;
    }
}
