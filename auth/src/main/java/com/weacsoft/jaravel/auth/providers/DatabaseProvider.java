package com.weacsoft.jaravel.auth.providers;


import com.weacsoft.jaravel.auth.Authenticatable;
import com.weacsoft.jaravel.auth.AuthenticatableProvider;

import java.util.Map;

public class DatabaseProvider implements AuthenticatableProvider {

    @Override
    public Authenticatable authById(String identifier) {
        return null;
    }

    @Override
    public Authenticatable authByCredentials(Map<String, String> credentials) {
        return null;
    }

    @Override
    public String getAuthIdentifier(Authenticatable user) {
        return "";
    }

    @Override
    public void updateRememberToken(Authenticatable user, String token) {

    }
}
