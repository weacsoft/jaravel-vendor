package com.weacsoft.jaravel.http.response;

import com.weacsoft.jaravel.contract.http.Cookie;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.contract.http.SimpleCookie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractResponse<T extends AbstractResponse<?>> implements Response {
    protected final Map<String, String> headers = new ConcurrentHashMap<>();
    protected final List<Cookie> cookies = new ArrayList<>();

    @Override
    public int getStatus() {
        return 200;
    }

    @Override
    public String getContent() {
        return "ok";
    }

    @Override
    public Map<String, String> getHeaders() {
        return new ConcurrentHashMap<>(headers);
    }

    @Override
    public T addHeader(String name, String value) {
        headers.put(name, value);
        return (T) this;
    }

    @Override
    public T replaceHeader(String name, String value) {
        headers.put(name, value);
        return (T) this;
    }

    @Override
    public List<Cookie> getCookies() {
        return new ArrayList<>(cookies);
    }

    @Override
    public T addCookie(Cookie cookie) {
        cookies.add(cookie);
        return (T) this;
    }

    @Override
    public T replaceCookie(String name, String value) {
        for (int i = 0; i < cookies.size(); i++) {
            if (cookies.get(i).getName().equals(name)) {
                cookies.get(i).setValue(value);
                return (T) this;
            }
        }
        return addCookie(new SimpleCookie(name, value));
    }

    @Override
    public T replaceCookieAll(List<Cookie> newCookies) {
        cookies.clear();
        cookies.addAll(newCookies);
        return (T) this;
    }
}