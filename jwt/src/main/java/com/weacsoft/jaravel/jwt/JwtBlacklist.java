package com.weacsoft.jaravel.jwt;

import com.weacsoft.jaravel.cache.Cache;

import java.util.Date;
import java.util.UUID;

public class JwtBlacklist {

    private final Cache cache;

    private final JwtConfig config;

    public JwtBlacklist(JwtConfig config) {
        this.config = config;
        this.cache = new com.weacsoft.jaravel.cache.ArrayCache(3600);
    }

    public JwtBlacklist(JwtConfig config, Cache cache) {
        this.config = config;
        this.cache = cache;
    }

    public void add(String token, Date expiresAt) {
        if (!config.isBlacklistEnabled()) {
            return;
        }
        long ttl = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        if (ttl > 0) {
            cache.put(token, expiresAt.getTime(), ttl);
        }
    }

    public void add(String token, long ttl) {
        if (!config.isBlacklistEnabled()) {
            return;
        }
        cache.put(token, System.currentTimeMillis() + ttl * 1000, ttl);
    }

    public boolean isBlacklisted(String token) {
        if (!config.isBlacklistEnabled()) {
            return false;
        }
        return cache.has(token);
    }

    public void remove(String token) {
        cache.forget(token);
    }

    public void clear() {
        cache.flush();
    }

    public int size() {
        return 0;
    }

    public String generateBlacklistId() {
        return UUID.randomUUID().toString();
    }

    public Cache getCache() {
        return cache;
    }
}
