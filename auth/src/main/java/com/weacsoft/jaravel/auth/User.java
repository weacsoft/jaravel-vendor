package com.weacsoft.jaravel.auth;

import lombok.Data;

@Data
public class User implements Authenticatable {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String rememberToken;

    public User() {
    }

    public User(Long id, String username, String password, String email) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public Object getAuthIdentifier() {
        return id;
    }

    @Override
    public String getAuthIdentifierName() {
        return "id";
    }

    @Override
    public String getAuthPassword() {
        return password;
    }

    @Override
    public String getRememberToken() {
        return rememberToken;
    }

    @Override
    public void setRememberToken(String token) {
        this.rememberToken = token;
    }

    @Override
    public String getRememberTokenName() {
        return "remember_token";
    }
}
