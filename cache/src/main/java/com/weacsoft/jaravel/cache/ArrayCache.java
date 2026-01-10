package com.weacsoft.jaravel.cache;

public class ArrayCache extends AbstractCache {

    private final ArrayCacheRepository repository;

    public ArrayCache() {
        this.repository = new ArrayCacheRepository();
    }

    public ArrayCache(long defaultTtl) {
        this.repository = new ArrayCacheRepository(defaultTtl);
    }

    public ArrayCache(long defaultTtl, int initialCapacity) {
        this.repository = new ArrayCacheRepository(defaultTtl, initialCapacity);
    }

    @Override
    protected CacheRepository getRepository() {
        return repository;
    }
}
