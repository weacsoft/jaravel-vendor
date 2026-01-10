package com.weacsoft.jaravel.auth;

public interface Guard {

    Authenticatable user();

    boolean check();

    boolean guest();

    Authenticatable login(Authenticatable user);

    void logout();

    boolean validate(Authenticatable user);

    boolean attempt(Object[] credentials);

    boolean once(Object[] credentials);

    boolean onceBasic(String[] credentials);

    Authenticatable basic(String[] credentials);
}
