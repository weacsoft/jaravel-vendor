package com.weacsoft.jaravel.cache;

import java.sql.Connection;

public class DatabaseCache extends AbstractCache {

    private final DatabaseCacheRepository repository;

    public DatabaseCache(Connection connection) {
        this.repository = new DatabaseCacheRepository(connection);
    }

    public DatabaseCache(Connection connection, String table) {
        this.repository = new DatabaseCacheRepository(connection, table);
    }

    public DatabaseCache(Connection connection, String table, long defaultTtl) {
        this.repository = new DatabaseCacheRepository(connection, table, defaultTtl);
    }

    @Override
    protected CacheRepository getRepository() {
        return repository;
    }

    public void cleanupExpired() {
        repository.cleanupExpired();
    }
}
