package com.weacsoft.jaravel.auth.guard;

import com.weacsoft.jaravel.auth.Authenticatable;

public class Guard {

    public Authenticatable user() {
        return null;
    }

    public boolean check() {
        return user() != null;
    }

    public  boolean guest() {
        return !check();
    }

    public Authenticatable login(Authenticatable user){
        return null;
    }

    public void logout(){

    }

    public boolean validate(Authenticatable user){
        return false;
    }

    public boolean attempt(Object[] credentials){
        return false;
    }

    public boolean once(Object[] credentials){
        return false;
    }
}
