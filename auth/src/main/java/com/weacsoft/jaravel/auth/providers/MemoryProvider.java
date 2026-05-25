package com.weacsoft.jaravel.auth.providers;

import com.weacsoft.jaravel.contract.auth.Authenticatable;
import com.weacsoft.jaravel.contract.auth.AuthProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存用户提供者实现。
 *
 * <p>将用户数据存储在内存中，应用重启后数据丢失。
 * 支持通过标识或凭据检索用户。</p>
 */
public class MemoryProvider<T extends Authenticatable> implements AuthProvider<T, String> {

    private final Map<String, T> users = new ConcurrentHashMap<>();


    @Override
    public T authById(String identifier) {
        return users.get(identifier);
    }

    @Override
    public AuthFunction<T, String> getAuthFunction() {
        return this::authById;
    }

}
