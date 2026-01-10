package com.weacsoft.jaravel.auth;

import java.util.HashMap;
import java.util.Map;

public class EloquentUserProvider implements UserProvider {

    private final Map<Object, Authenticatable> users = new HashMap<>();

    public EloquentUserProvider() {
    }

    @Override
    public Authenticatable retrieveById(Object identifier) {
        return users.get(identifier);
    }

    @Override
    public Authenticatable retrieveByToken(Object identifier, String token) {
        Authenticatable user = retrieveById(identifier);
        if (user != null && token.equals(user.getRememberToken())) {
            return user;
        }
        return null;
    }

    @Override
    public void updateRememberToken(Authenticatable user, String token) {
        user.setRememberToken(token);
    }

    @Override
    public Authenticatable retrieveByCredentials(Object[] credentials) {
        if (credentials == null || credentials.length == 0) {
            return null;
        }
        Object identifier = credentials[0];
        return retrieveById(identifier);
    }

    @Override
    public boolean validateCredentials(Authenticatable user, Object[] credentials) {
        if (user == null || credentials == null || credentials.length < 2) {
            return false;
        }
        String password = credentials[1].toString();
        return user.getAuthPassword().equals(password);
    }

    public void addUser(Authenticatable user) {
        users.put(user.getAuthIdentifier(), user);
    }

    public void removeUser(Object identifier) {
        users.remove(identifier);
    }
}
