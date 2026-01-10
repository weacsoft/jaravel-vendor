package com.weacsoft.jaravel.jwt;

import com.weacsoft.jaravel.utils.ExpiryMap;

import java.util.Date;
import java.util.UUID;

public class JwtBlacklist {

    private final ExpiryMap<String, Long> blacklist;

    private final JwtConfig config;

    public JwtBlacklist(JwtConfig config) {
        this.config = config;
        this.blacklist = new ExpiryMap<>();
    }

    public void add(String token, Date expiresAt) {
        if (!config.isBlacklistEnabled()) {
            return;
        }
        long ttl = expiresAt.getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            blacklist.put(token, ttl);
        }
    }

    public void add(String token, long ttl) {
        if (!config.isBlacklistEnabled()) {
            return;
        }
        blacklist.put(token, ttl);
    }

    public boolean isBlacklisted(String token) {
        if (!config.isBlacklistEnabled()) {
            return false;
        }
        return blacklist.containsKey(token);
    }

    public void remove(String token) {
        blacklist.remove(token);
    }

    public void clear() {
        blacklist.clear();
    }

    public int size() {
        return blacklist.size();
    }

    public String generateBlacklistId() {
        return UUID.randomUUID().toString();
    }
}
