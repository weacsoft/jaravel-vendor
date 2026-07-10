package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * 基于文件系统的缓存驱动，对齐 Laravel {@code "file"} 缓存驱动。
 * <p>
 * 每个 key 对应目录下一个文件，使用 Jackson {@link ObjectMapper} 将 {@link CacheEntry}
 * （含 {@code value} 与 {@code expiryAt}）序列化为 JSON 写入文件；读取时反序列化，
 * 过期则删除文件并视为未命中。
 * <p>
 * <b>TTL 单位为秒</b>：{@code expiryAt = System.currentTimeMillis() + ttlSeconds * 1000}，
 * {@code ttlSeconds <= 0} 时 {@code expiryAt = 0}（永不过期）。
 * <b>已修复旧版 TTL 单位混乱的 bug</b>（旧实现曾把秒当毫秒或重复乘 1000，现统一为秒）。
 * <p>
 * 缓存键通过 UTF-8 十六进制编码作为文件名，保证可逆且文件系统安全，
 * 因此 {@link #allKeys()} 能还原出原始键。
 */
public class FileCacheDriver implements CacheDriver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUFFIX = ".cache";

    /** 缓存文件存放目录 */
    private final File dir;

    /** 默认目录：${java.io.tmpdir}/jaravel-cache */
    public FileCacheDriver() {
        this(new File(System.getProperty("java.io.tmpdir"), "jaravel-cache"));
    }

    /**
     * 指定目录构造器。
     *
     * @param dir 缓存目录，{@code null} 或空串则回退到默认临时目录
     */
    public FileCacheDriver(String dir) {
        this(dir == null || dir.isEmpty()
                ? new File(System.getProperty("java.io.tmpdir"), "jaravel-cache")
                : new File(dir));
    }

    /**
     * 指定目录构造器。
     *
     * @param dir 缓存目录
     */
    public FileCacheDriver(File dir) {
        this.dir = dir;
        if (!this.dir.exists() && !this.dir.mkdirs()) {
            throw new IllegalStateException("无法创建缓存目录: " + this.dir);
        }
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        // 修复旧版 bug：TTL 统一为秒，expiryAt 使用毫秒时间戳
        long expiryAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        try {
            MAPPER.writeValue(fileFor(key), new CacheEntry(value, expiryAt));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Object get(String key) {
        CacheEntry entry = readEntry(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            fileFor(key).delete();
            return null;
        }
        return entry.getValue();
    }

    @Override
    public boolean exists(String key) {
        CacheEntry entry = readEntry(key);
        if (entry == null) {
            return false;
        }
        if (isExpired(entry)) {
            fileFor(key).delete();
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(String key) {
        File f = fileFor(key);
        return f.exists() && f.delete();
    }

    @Override
    public void removeAll() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        if (files == null) {
            return;
        }
        for (File f : files) {
            f.delete();
        }
    }

    @Override
    public Collection<String> allKeys() {
        List<String> keys = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        if (files == null) {
            return keys;
        }
        for (File f : files) {
            try {
                CacheEntry entry = MAPPER.readValue(f, CacheEntry.class);
                if (entry == null) {
                    continue;
                }
                if (isExpired(entry)) {
                    f.delete();
                    continue;
                }
                keys.add(decodeKey(f.getName()));
            } catch (Exception ignore) {
                // 损坏文件跳过，避免影响整体遍历
            }
        }
        return keys;
    }

    /** 读取指定 key 对应的条目，文件不存在或解析失败返回 {@code null} */
    private CacheEntry readEntry(String key) {
        File f = fileFor(key);
        if (!f.exists()) {
            return null;
        }
        try {
            return MAPPER.readValue(f, CacheEntry.class);
        } catch (IOException e) {
            return null;
        }
    }

    /** key -> 缓存文件 */
    private File fileFor(String key) {
        return new File(dir, encodeKey(key) + SUFFIX);
    }

    private static boolean isExpired(CacheEntry entry) {
        long expiryAt = entry.getExpiryAt();
        return expiryAt > 0 && System.currentTimeMillis() >= expiryAt;
    }

    /** 将 key 的 UTF-8 字节转为十六进制串，作为文件名（可逆、文件系统安全） */
    private static String encodeKey(String key) {
        return HexFormat.of().formatHex(key.getBytes(StandardCharsets.UTF_8));
    }

    /** 由文件名还原原始 key */
    private static String decodeKey(String fileName) {
        String hex = fileName.substring(0, fileName.length() - SUFFIX.length());
        return new String(HexFormat.of().parseHex(hex), StandardCharsets.UTF_8);
    }

    /**
     * 文件缓存条目：{@code value} + {@code expiryAt}（0 表示永不过期）。
     * <p>
     * 注意：由于 {@code value} 为 {@code Object}，Jackson 反序列化时复杂对象会还原为
     * {@code LinkedHashMap} / {@code ArrayList} 等基础类型，这是 JSON 文件缓存的固有特性。
     */
    public static class CacheEntry {
        private Object value;
        private long expiryAt;

        /** Jackson 反序列化需要的默认构造器 */
        public CacheEntry() {
        }

        public CacheEntry(Object value, long expiryAt) {
            this.value = value;
            this.expiryAt = expiryAt;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public long getExpiryAt() {
            return expiryAt;
        }

        public void setExpiryAt(long expiryAt) {
            this.expiryAt = expiryAt;
        }
    }
}
