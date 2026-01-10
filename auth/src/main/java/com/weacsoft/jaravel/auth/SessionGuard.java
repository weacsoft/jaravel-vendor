package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.request.RequestFactory;

public class SessionGuard implements Guard {

    private final UserProvider provider;
    private final Request request;
    private final String name;
    private Authenticatable user;

    public SessionGuard(String name, UserProvider provider, Request request) {
        this.name = name;
        this.provider = provider;
        this.request = request;
    }

    @Override
    public Authenticatable user() {
        if (user != null) {
            return user;
        }

        String id = request.session(name + "_id");
        if (id != null) {
            Authenticatable retrievedUser = provider.retrieveById(id);
            if (retrievedUser != null) {
                user = retrievedUser;
                return user;
            }
        }

        return null;
    }

    @Override
    public boolean check() {
        return user() != null;
    }

    @Override
    public boolean guest() {
        return !check();
    }

    @Override
    public Authenticatable login(Authenticatable user) {
        this.user = user;
        request.replaceSession(name + "_id", user.getAuthIdentifier().toString());
        return user;
    }

    @Override
    public void logout() {
        if (user != null) {
            request.removeSession(name + "_id");
            user = null;
        }
    }

    @Override
    public boolean validate(Authenticatable user) {
        return user != null && provider.validateCredentials(user, new Object[]{});
    }

    @Override
    public boolean attempt(Object[] credentials) {
        Authenticatable user = provider.retrieveByCredentials(credentials);
        if (user != null && provider.validateCredentials(user, credentials)) {
            login(user);
            return true;
        }
        return false;
    }

    @Override
    public boolean once(Object[] credentials) {
        Authenticatable user = provider.retrieveByCredentials(credentials);
        if (user != null && provider.validateCredentials(user, credentials)) {
            this.user = user;
            return true;
        }
        return false;
    }

    @Override
    public boolean onceBasic(String[] credentials) {
        return false;
    }

    @Override
    public Authenticatable basic(String[] credentials) {
        return null;
    }

    public void setUser(Authenticatable user) {
        this.user = user;
    }
}
