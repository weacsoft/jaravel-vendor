package com.weacsoft.jaravel.contract.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存存储接口，定义高级缓存操作的契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Cache\Repository}，
 * 本接口在 {@link CacheDriver} 基础上提供更丰富的缓存操作，
 * 包括批量操作、原子递增/递减、记忆化存储等。</p>
 *
 * <h3>TTL 约定</h3>
 * <ul>
 *   <li>未指定 {@link TimeUnit} 时，TTL 单位为毫秒</li>
 *   <li>TTL &lt;= 0 表示永不过期</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #add} 系列方法为原子操作（键不存在时才写入）</li>
 *   <li>{@link #increment}/{@link #decrement} 应为原子操作</li>
 *   <li>实现类应委托 {@link CacheDriver} 完成底层存储</li>
 * </ul>
 *
 * @see CacheDriver
 */
public interface CacheStore {

    /**
     * 存储键值对（永不过期）。
     */
    boolean put(String key, Object value);

    /**
     * 存储键值对并指定 TTL（毫秒）。
     */
    boolean put(String key, Object value, long ttl);

    /**
     * 存储键值对并指定 TTL 和时间单位。
     */
    boolean put(String key, Object value, long ttl, TimeUnit timeUnit);

    /**
     * 获取缓存值（无类型转换）。
     */
    default Object get(String key) {
        return get(key, Object.class);
    }

    /**
     * 获取缓存值并转换为指定类型。
     */
    <T> T get(String key, Class<T> type);

    /**
     * 检查缓存键是否存在。
     */
    boolean has(String key);

    /**
     * 移除缓存键。
     */
    boolean forget(String key);

    /**
     * 清空所有缓存。
     */
    boolean flush();

    /**
     * 批量存储键值对。
     */
    boolean putMany(Map<String, Object> values);

    /**
     * 批量获取缓存值。
     */
    Map<String, Object> getMany(Collection<String> keys);

    /**
     * 批量移除缓存键。
     */
    boolean forgetMany(Collection<String> keys);

    /**
     * 记忆化存储：键不存在时通过回调获取值并缓存。
     */
    boolean remember(String key, long ttl, Supplier<Object> callback);

    /**
     * 记忆化存储：键不存在时通过回调获取值并缓存（指定时间单位）。
     */
    boolean remember(String key, long ttl, TimeUnit timeUnit, Supplier<Object> callback);

    /**
     * 永久记忆化存储：键不存在时通过回调获取值并永久缓存。
     */
    boolean rememberForever(String key, Supplier<Object> callback);

    /**
     * 获取并移除缓存值。
     */
    Object pull(String key);

    /**
     * 获取并移除缓存值（带类型转换）。
     */
    <T> T pull(String key, Class<T> type);

    /**
     * 仅当键不存在时存储（永不过期）。
     */
    boolean add(String key, Object value);

    /**
     * 仅当键不存在时存储并指定 TTL（毫秒）。
     */
    boolean add(String key, Object value, long ttl);

    /**
     * 仅当键不存在时存储并指定 TTL 和时间单位。
     */
    boolean add(String key, Object value, long ttl, TimeUnit timeUnit);

    /**
     * 原子递增。
     */
    long increment(String key);

    /**
     * 原子递增指定值。
     */
    long increment(String key, long value);

    /**
     * 原子递减。
     */
    long decrement(String key);

    /**
     * 原子递减指定值。
     */
    long decrement(String key, long value);

    /**
     * 获取缓存键前缀。
     */
    String getPrefix();

    /**
     * 设置缓存键前缀。
     */
    void setPrefix(String prefix);
}
