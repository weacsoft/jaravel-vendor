package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.utils.cache.SimpleMemoryCache;

import java.util.Collection;

/**
 * 基于内存的缓存驱动，对齐 Laravel {@code "array"} 缓存驱动。
 * <p>
 * 内部委托给 {@link SimpleMemoryCache}（utils 模块提供的零依赖内存缓存），
 * 支持 TTL 过期、惰性清理等特性。线程安全，仅在当前 JVM 进程内有效，
 * 进程重启即失，常用于单元测试与本地开发。
 * <p>
 * TTL 单位为秒：{@code ttlSeconds <= 0} 表示永不过期。读取 / 存在性判断时会惰性清理过期条目。
 */
public class ArrayCacheDriver implements CacheDriver, com.weacsoft.jaravel.vendor.cache.TtlAwareCacheDriver {

    /** 委托的内存缓存实现 */
    private final SimpleMemoryCache cache = new SimpleMemoryCache();

    /** 默认构造器 */
    public ArrayCacheDriver() {
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        cache.put(key, value, ttlSeconds);
        return true;
    }

    @Override
    public Object get(String key) {
        return cache.get(key);
    }

    @Override
    public boolean exists(String key) {
        return cache.exists(key);
    }

    @Override
    public boolean remove(String key) {
        return cache.remove(key);
    }

    @Override
    public void removeAll() {
        cache.removeAll();
    }

    @Override
    public Collection<String> allKeys() {
        return cache.allKeys();
    }

    /**
     * 报告剩余 TTL：键存在时返回剩余秒数（永不过期为 0），键不存在返回 empty。
     * <p>
     * {@link SimpleMemoryCache#remainingTtlSeconds(String)} 用 -1/-2 区分「永不过期/不存在」，
     * 这里转成本能力接口的三态约定（empty / 0 / 正数）。
     */
    @Override
    public java.util.OptionalLong remainingTtlSeconds(String key) {
        long remaining = cache.remainingTtlSeconds(key);
        if (remaining == -2) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of(remaining < 0 ? 0L : remaining);
    }
}
