package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.auth.AuthDriver;
import com.weacsoft.jaravel.contract.auth.Authenticatable;
import com.weacsoft.jaravel.contract.auth.AuthenticatableProvider;
import com.weacsoft.jaravel.contract.auth.StatefulGuard;

public class Guard<T extends Authenticatable> implements StatefulGuard<T> {
    private final String name;
    private final AuthDriver authDriver;
    private final AuthenticatableProvider<T> provider;
    private final ThreadLocal<T> currentUser = new ThreadLocal<>();

    public Guard(String name, AuthDriver driver, AuthenticatableProvider<T> provider) {
        this.name = name;
        this.authDriver = driver;
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    @Override
    public T user() {
        if (currentUser.get() == null) {
            String id = authDriver.getId();
            if (id == null) {
                return null;
            }
            currentUser.set(provider.authById(id));
        }
        return currentUser.get();
    }

    @Override
    public boolean login(T user) {
        authDriver.setId(user.getAuthIdentifier());
        currentUser.set(user);
        return true;
    }

    @Override
    public void logout() {
        authDriver.removeId();
        currentUser.remove();
    }
}
