package com.weacsoft.jaravel.auth.drivers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileDriver extends MemoryDriver {

    private final String basePath;
    private final String fileName;

    /**
     * 创建文件用户提供者。
     *
     * @param fileName 存储用户数据的目录路径
     */
    public FileDriver(String basePath, String fileName) {
        this.basePath = basePath;
        this.fileName = fileName;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        saveToFile(id);
    }

    @Override
    public String getId() {
        String id = super.getId();
        if (id == null) {
            id = loadFromFile();
        }
        return id;
    }

    @Override
    public void removeId() {
        deleteFile();
        super.removeId();
    }


    private String loadFromFile() {
        Path filePath = getFilePath();
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(filePath);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                return (String) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to load id from file: " + filePath, e);
        }
    }

    private void saveToFile(String id) {
        Path filePath = getFilePath();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(id);
            }
            Files.write(filePath, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save id to file: " + filePath, e);
        }
    }

    private void deleteFile() {
        Path filePath = getFilePath();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete user data file: " + filePath, e);
        }
    }

    private Path getFilePath() {
        // 使用安全的文件名（替换特殊字符）
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get(basePath, "driver_" + safeName + ".dat");
    }
}
