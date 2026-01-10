package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.request.RequestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AuthManager {

    private final Map<String, Function<String, Guard>> customCreators = new HashMap<>();
    private final Map<String, Guard> guards = new HashMap<>();
    private final Request request;
    private UserProvider userProvider;
    private String defaultGuard = "web";

    public AuthManager(Request request) {
        this.request = request;
    }

    public AuthManager(Request request, UserProvider userProvider) {
        this.request = request;
        this.userProvider = userProvider;
    }

    public Guard guard(String name) {
        if (guards.containsKey(name)) {
            return guards.get(name);
        }

        Guard guard = resolve(name);
        guards.put(name, guard);
        return guard;
    }

    public Guard guard() {
        return guard(defaultGuard);
    }

    private Guard resolve(String name) {
        if (customCreators.containsKey(name)) {
            return customCreators.get(name).apply(name);
        }

        return createSessionDriver(name);
    }

    private Guard createSessionDriver(String name) {
        return new SessionGuard(name, userProvider, request);
    }

    public void extend(String name, Function<String, Guard> callback) {
        customCreators.put(name, callback);
    }

    public void setUserProvider(UserProvider userProvider) {
        this.userProvider = userProvider;
    }

    public UserProvider getUserProvider() {
        return userProvider;
    }

    public void setDefaultGuard(String defaultGuard) {
        this.defaultGuard = defaultGuard;
    }

    public String getDefaultGuard() {
        return defaultGuard;
    }

    public void forgetGuard(String name) {
        guards.remove(name);
    }
}
