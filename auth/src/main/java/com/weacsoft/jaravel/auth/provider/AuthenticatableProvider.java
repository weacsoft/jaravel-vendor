package com.weacsoft.jaravel.auth.provider;

import com.weacsoft.jaravel.auth.Authenticatable;

public interface AuthenticatableProvider {

    Authenticatable retrieveById(Object identifier);

    Authenticatable retrieveByToken(Object identifier, String token);

    void updateRememberToken(Authenticatable user, String token);

    Authenticatable retrieveByCredentials(Object[] credentials);

    boolean validateCredentials(Authenticatable user, Object[] credentials);
}
