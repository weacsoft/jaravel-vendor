package com.weacsoft.jaravel.auth;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Authenticatable extends Serializable {

    Map<String, String> externData = new HashMap<>();

    default Map<String, String> putExternData(String key, String value) {
        externData.put(key, value);
        return externData;
    }

    default Map<String, String> removeExternData(String key) {
        externData.remove(key);
        return externData;
    }

    default Map<String, String> removeAllExternData() {
        externData.clear();
        return externData;
    }

    default String getAuthIdentifierName() {
        return "id";
    }

    String getAuthIdentifier();

    default List<String> getCredentialName() {
        List<String> credentialName = new ArrayList<>();
        credentialName.add("number");
        credentialName.add("password");
        return credentialName;
    }

    default String getRememberTokenName() {
        return "remember_token";
    }

    String getRememberToken();
}
