package com.weacsoft.jaravel.auth.providers;

import com.weacsoft.jaravel.contract.auth.Authenticatable;
import com.weacsoft.jaravel.contract.auth.AuthenticatableProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryProvider<T extends Authenticatable> implements AuthenticatableProvider<T> {

    private Map<String, T> users = new ConcurrentHashMap<>();


    public void addUser(String identifier, T user) {
        users.put(identifier, user);
    }

    public void removeUser(String identifier) {
        users.remove(identifier);
    }

    @Override
    public T authById(String identifier) {
        return users.get(identifier);
    }

    @Override
    public T authByCredentials(Map<String, String> credentials) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
