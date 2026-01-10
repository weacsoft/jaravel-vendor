package com.weacsoft.jaravel.auth;

import java.io.Serializable;

public interface Authenticatable extends Serializable {

    Object getAuthIdentifier();

    String getAuthIdentifierName();

    String getAuthPassword();

    String getRememberToken();

    void setRememberToken(String token);

    String getRememberTokenName();
}
