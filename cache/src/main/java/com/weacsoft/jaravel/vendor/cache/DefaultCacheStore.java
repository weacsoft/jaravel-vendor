package com.weacsoft.jaravel.vendor.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 默认缓存仓库实现，对齐 Laravel {@code Illuminate\Cache\Repository}。
 * <p>
 * 委托给底层 {@link CacheDriver}，所有 key 操作前自动前置 {@code prefix + ":"}，
 * 用于隔离不同模块 / 应用的缓存命名空间。TTL 单位统一为<b>秒</b>。
 * <p>
 * {@code increment} / {@code decrement} 采用 get-then-put 实现（非原子，但简单直观），
 * 当键不存在或值非数字时按 0 起算；{@code remember} / {@code rememberForever} 实现
 * “命中即返回、未命中则加载并回填”的常规模式。
 */
public class DefaultCacheStore implements CacheStore {

    private final CacheDriver driver;
    private final String prefix;

    /**
     * @param driver 底层缓存驱动
     * @param prefix 键前缀，{@code null} 视为无前缀
     */
    public DefaultCacheStore(CacheDriver driver, String prefix) {
        this.driver = driver;
        this.prefix = prefix == null ? "" : prefix;
    }

    /** 拼接带前缀的实际键 */
    private String key(String key) {
        return prefix.isEmpty() ? key : prefix + ":" + key;
    }

    @Override
    public boolean put(String key, Object value, long ttlSeconds) {
        return driver.put(key(key), value, ttlSeconds);
    }

    @Override
    public boolean put(String key, Object value) {
        return driver.put(key(key), value, 0);
    }

    @Override
    public Object get(String key) {
        return driver.get(key(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = driver.get(key(key));
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // 数字类型互转
        if (value instanceof Number n) {
            if (type == Integer.class || type == int.class) return (T) Integer.valueOf(n.intValue());
            if (type == Long.class || type == long.class) return (T) Long.valueOf(n.longValue());
            if (type == Double.class || type == double.class) return (T) Double.valueOf(n.doubleValue());
            if (type == Float.class || type == float.class) return (T) Float.valueOf(n.floatValue());
            if (type == Short.class || type == short.class) return (T) Short.valueOf(n.shortValue());
            if (type == Byte.class || type == byte.class) return (T) Byte.valueOf(n.byteValue());
        }
        // 转字符串
        if (type == String.class) {
            return (T) value.toString();
        }
        // 布尔
        if ((type == Boolean.class || type == boolean.class) && value instanceof Boolean b) {
            return (T) b;
        }
        // 兜底：尝试以字符串构造
        try {
            return type.getConstructor(String.class).newInstance(value.toString());
        } catch (Exception e) {
            throw new ClassCastException("无法将缓存值 [" + value + "] 转换为 " + type.getName());
        }
    }

    @Override
    public boolean has(String key) {
        return driver.exists(key(key));
    }

    @Override
    public boolean forget(String key) {
        return driver.remove(key(key));
    }

    @Override
    public void flush() {
        driver.removeAll();
    }

    @Override
    public Object pull(String key) {
        Object value = get(key);
        if (value != null) {
            forget(key);
        }
        return value;
    }

    @Override
    public boolean add(String key, Object value, long ttlSeconds) {
        if (has(key)) {
            return false;
        }
        put(key, value, ttlSeconds);
        return true;
    }

    @Override
    public long increment(String key) {
        return increment(key, 1L);
    }

    @Override
    public long increment(String key, long amount) {
        // get-then-put：非原子但简单；null / 非数字按 0 起算
        long current = toLong(get(key));
        long next = current + amount;
        put(key, next, 0);
        return next;
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1L);
    }

    @Override
    public long decrement(String key, long amount) {
        long current = toLong(get(key));
        long next = current - amount;
        put(key, next, 0);
        return next;
    }

    @Override
    public void putMany(Map<String, Object> values, long ttlSeconds) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            put(e.getKey(), e.getValue(), ttlSeconds);
        }
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Map<String, Object> result = new HashMap<>();
        if (keys == null) {
            return result;
        }
        for (String k : keys) {
            result.put(k, get(k));
        }
        return result;
    }

    @Override
    public Object remember(String key, long ttlSeconds, Supplier<Object> loader) {
        Object value = get(key);
        if (value != null) {
            return value;
        }
        value = loader.get();
        put(key, value, ttlSeconds);
        return value;
    }

    @Override
    public Object rememberForever(String key, Supplier<Object> loader) {
        Object value = get(key);
        if (value != null) {
            return value;
        }
        value = loader.get();
        put(key, value);
        return value;
    }

    /** 将缓存值转为 long，{@code null} / 非数字返回 0 */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
