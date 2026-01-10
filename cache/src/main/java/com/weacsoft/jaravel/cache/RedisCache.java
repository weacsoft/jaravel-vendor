package com.weacsoft.jaravel.cache;

import redis.clients.jedis.JedisPool;

public class RedisCache extends AbstractCache {

    private final RedisCacheRepository repository;

    public RedisCache() {
        this.repository = new RedisCacheRepository();
    }

    public RedisCache(String host, int port) {
        this.repository = new RedisCacheRepository(host, port);
    }

    public RedisCache(String host, int port, long defaultTtl) {
        this.repository = new RedisCacheRepository(host, port, defaultTtl);
    }

    public RedisCache(JedisPool pool, long defaultTtl) {
        this.repository = new RedisCacheRepository(pool, defaultTtl);
    }

    @Override
    protected CacheRepository getRepository() {
        return repository;
    }

    public void close() {
        repository.close();
    }
}
