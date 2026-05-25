package com.weacsoft.jaravel.auth.providers;

import com.weacsoft.jaravel.contract.auth.Authenticatable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件用户提供者实现。
 *
 * <p>将用户数据持久化到文件系统，应用重启后数据仍然保留。
 * 每个用户对应一个属性文件。</p>
 *
 * <h3>文件存储结构</h3>
 * <pre>
 * {basePath}/
 *   user_{identifier}.properties   -- 每个用户一个文件
 * </pre>
 *
 * <p>用户实体需要实现 {@link Authenticatable} 接口，且必须有一个无参构造函数。
 * 用户属性通过 Java 序列化机制存储。</p>
 */
public class FileProvider<T extends Authenticatable> extends MemoryProvider<T> {

    private final String basePath;

    /**
     * 内存缓存，避免频繁读取文件。
     */
    private final Map<String, T> cache = new ConcurrentHashMap<>();

    /**
     * 创建文件用户提供者。
     *
     * @param basePath  存储用户数据的目录路径
     */
    public FileProvider(String basePath) {
        this.basePath = basePath;
        ensureDirectoryExists();
    }

    /**
     * 添加用户到提供者（持久化到文件）。
     *
     * @param identifier 用户唯一标识
     * @param user       用户实体
     */
    public void addUser(String identifier, T user) {
        cache.put(identifier, user);
        saveToFile(identifier, user);
    }

    /**
     * 从提供者中移除用户。
     *
     * @param identifier 用户唯一标识
     */
    public void removeUser(String identifier) {
        cache.remove(identifier);
        deleteFile(identifier);
    }

    @Override
    public T authById(String identifier) {
        // 先查缓存
        T cached = cache.get(identifier);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中，从文件加载
        T user = loadFromFile(identifier);
        if (user != null) {
            cache.put(identifier, user);
        }
        return user;
    }


    @SuppressWarnings("unchecked")
    private T loadFromFile(String identifier) {
        Path filePath = getFilePath(identifier);
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(filePath);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                return (T) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to load user data from file: " + filePath, e);
        }
    }

    private void saveToFile(String identifier, T user) {
        Path filePath = getFilePath(identifier);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(user);
            }
            Files.write(filePath, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save user data to file: " + filePath, e);
        }
    }

    private void deleteFile(String identifier) {
        Path filePath = getFilePath(identifier);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete user data file: " + filePath, e);
        }
    }

    private Path getFilePath(String identifier) {
        // 使用安全的文件名（替换特殊字符）
        String safeName = identifier.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get(basePath, "user_" + safeName + ".dat");
    }

    private void ensureDirectoryExists() {
        File dir = new File(basePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
