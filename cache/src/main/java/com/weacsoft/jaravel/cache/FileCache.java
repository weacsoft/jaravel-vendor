package com.weacsoft.jaravel.cache;

public class FileCache extends AbstractCache {

    private final FileCacheRepository repository;

    public FileCache() {
        this.repository = new FileCacheRepository();
    }

    public FileCache(String directory) {
        this.repository = new FileCacheRepository(directory);
    }

    public FileCache(String directory, long defaultTtl) {
        this.repository = new FileCacheRepository(directory, defaultTtl);
    }

    @Override
    protected CacheRepository getRepository() {
        return repository;
    }
}
