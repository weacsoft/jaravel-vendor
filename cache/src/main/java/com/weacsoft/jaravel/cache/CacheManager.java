package com.weacsoft.jaravel.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CacheManager {

    private static CacheManager manager;

    private final Map<String, Cache> stores = new HashMap<>();

    private String defaultStore = "default";

    public CacheManager() {
        this.defaultStore = "default";
    }

    public static void setManager(CacheManager manager) {
        CacheManager.manager = manager;
    }

    public static CacheManager manager() {
        return manager;
    }

    public Cache store() {
        return store(defaultStore);
    }

    public Cache store(String name) {
        if (!stores.containsKey(name)) {
            throw new IllegalArgumentException("Cache store [" + name + "] is not defined.");
        }
        return stores.get(name);
    }

    public CacheManager extend(String name, Function<String, Cache> callback) {
        stores.put(name, callback.apply(name));
        return this;
    }

    public CacheManager addStore(String name, Cache cache) {
        stores.put(name, cache);
        return this;
    }

    public CacheManager setDefaultStore(String name) {
        this.defaultStore = name;
        return this;
    }

    public String getDefaultStore() {
        return defaultStore;
    }

    public CacheManager forgetStore(String name) {
        stores.remove(name);
        return this;
    }

    public CacheManager flushAll() {
        for (Cache cache : stores.values()) {
            cache.flush();
        }
        return this;
    }

    public Map<String, Cache> getStores() {
        return new HashMap<>(stores);
    }

    public boolean hasStore(String name) {
        return stores.containsKey(name);
    }
}
