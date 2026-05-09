package com.weacsoft.jaravel.cache;

import com.weacsoft.jaravel.contract.cache.CacheDriver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileCacheDriver implements CacheDriver {
    private final String directory;

    public FileCacheDriver() {
        this(System.getProperty("java.io.tmpdir") + File.separator + "jaravel_cache");
    }


    public FileCacheDriver(String directory) {
        this.directory = directory;
        ensureDirectoryExists();
    }

    //创建对应目录
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
    public boolean put(String key, Object value, long ttl) {
        CacheEntry entry;
        if (ttl <= 0) {
            entry = new CacheEntry(value, -1);
        } else {
            entry = new CacheEntry(value, System.currentTimeMillis() + ttl * 1000);
        }
        return writeCacheEntry(key, entry);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        CacheEntry entry = readCacheEntry(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            remove(key);
            return null;
        }
        return (T) entry.getValue();
    }

    @Override
    public boolean exist(String key) {
        CacheEntry entry = readCacheEntry(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            remove(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(String key) {
        String filePath = getFilePath(key);
        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    @Override
    public void removeAll() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".cache"));
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
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
            if (expiryTime < 0) {
                return false;
            }
            return System.currentTimeMillis() > expiryTime;
        }
    }
}
