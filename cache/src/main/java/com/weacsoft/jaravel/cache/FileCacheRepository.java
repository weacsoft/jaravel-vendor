package com.weacsoft.jaravel.cache;

import com.alibaba.fastjson.JSON;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FileCacheRepository implements CacheRepository {

    private final String directory;

    private final long defaultTtl;

    public FileCacheRepository() {
        this(System.getProperty("java.io.tmpdir") + File.separator + "jaravel_cache", 3600);
    }

    public FileCacheRepository(String directory) {
        this(directory, 3600);
    }

    public FileCacheRepository(String directory, long defaultTtl) {
        this.directory = directory;
        this.defaultTtl = defaultTtl;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        Path path = Paths.get(directory);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create cache directory: " + directory, e);
        }
    }

    private String getFilePath(String key) {
        String safeKey = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        return directory + File.separator + safeKey + ".cache";
    }

    private CacheEntry readCacheEntry(String key) {
        String filePath = getFilePath(key);
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (CacheEntry) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean writeCacheEntry(String key, CacheEntry entry) {
        String filePath = getFilePath(key);
        File file = new File(filePath);

        try (FileOutputStream fos = new FileOutputStream(file);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(entry);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean put(String key, Object value) {
        return put(key, value, defaultTtl);
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        CacheEntry entry = new CacheEntry(value, System.currentTimeMillis() + ttl * 1000);
        return writeCacheEntry(key, entry);
    }

    @Override
    public boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        return put(key, value, timeUnit.toSeconds(ttl));
    }

    @Override
    public Object get(String key) {
        CacheEntry entry = readCacheEntry(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            forget(key);
            return null;
        }

        return entry.getValue();
    }

    @Override
    public boolean has(String key) {
        CacheEntry entry = readCacheEntry(key);
        if (entry == null) {
            return false;
        }

        if (entry.isExpired()) {
            forget(key);
            return false;
        }

        return true;
    }

    @Override
    public boolean forget(String key) {
        String filePath = getFilePath(key);
        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    @Override
    public boolean flush() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".cache"));
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        return true;
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            Object value = get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        for (String key : keys) {
            forget(key);
        }
        return true;
    }

    @Override
    public boolean add(String key, Object value) {
        if (has(key)) {
            return false;
        }
        return put(key, value);
    }

    @Override
    public boolean add(String key, Object value, long ttl) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl);
    }

    @Override
    public boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl, timeUnit);
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long value) {
        Object current = get(key);
        long newValue;
        if (current == null) {
            newValue = value;
        } else if (current instanceof Number) {
            newValue = ((Number) current).longValue() + value;
        } else {
            throw new IllegalArgumentException("Cannot increment non-numeric value");
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        return increment(key, -value);
    }

    private static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Object value;
        private final long expiryTime;

        public CacheEntry(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public Object getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}
