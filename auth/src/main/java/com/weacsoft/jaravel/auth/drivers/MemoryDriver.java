package com.weacsoft.jaravel.auth.drivers;

import com.weacsoft.jaravel.contract.auth.AuthDriver;

public class MemoryDriver implements AuthDriver {

    private String id;

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void removeId() {
        id = null;
    }
}
