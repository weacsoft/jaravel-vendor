package com.weacsoft.jaravel.vendor.cache;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 高级缓存操作契约，对齐 Laravel {@code Illuminate\Cache\Repository}（即 {@code Cache::} 背后的仓库）。
 * <p>
 * 在底层 {@link CacheDriver} 之上提供 put / get / has / forget / flush / pull / add /
 * increment / decrement / putMany / getMany / remember / rememberForever 等语义。
 * TTL 单位统一为<b>秒</b>（对齐 Laravel）。
 */
public interface CacheStore {

    /**
     * 写入缓存，指定过期秒数，{@code ttlSeconds <= 0} 表示永不过期。
     */
    boolean put(String key, Object value, long ttlSeconds);

    /**
     * 永久写入缓存。
     */
    boolean put(String key, Object value);

    /**
     * 读取原始缓存值，不存在或已过期返回 {@code null}。
     */
    Object get(String key);

    /**
     * 读取并按指定类型转换缓存值，对齐 Laravel {@code Cache::get(key, Type)} 的类型化读取。
     */
    <T> T get(String key, Class<T> type);

    /**
     * 判断缓存键是否存在，对齐 Laravel {@code Cache::has}。
     */
    boolean has(String key);

    /**
     * 移除指定缓存键，对齐 Laravel {@code Cache::forget}。
     */
    boolean forget(String key);

    /**
     * 清空当前 store 下所有缓存，对齐 Laravel {@code Cache::flush}。
     */
    void flush();

    /**
     * 读取后立即删除，对齐 Laravel {@code Cache::pull}。
     */
    Object pull(String key);

    /**
     * 仅当键不存在时写入，对齐 Laravel {@code Cache::add}。返回是否实际写入。
     */
    boolean add(String key, Object value, long ttlSeconds);

    /**
     * 自增 1，对齐 Laravel {@code Cache::increment}。键不存在或非数字时按 0 起算。
     */
    long increment(String key);

    /**
     * 自增指定步长，对齐 Laravel {@code Cache::increment(key, amount)}。
     */
    long increment(String key, long amount);

    /**
     * 自减 1，对齐 Laravel {@code Cache::decrement}。
     */
    long decrement(String key);

    /**
     * 自减指定步长，对齐 Laravel {@code Cache::decrement(key, amount)}。
     */
    long decrement(String key, long amount);

    /**
     * 批量写入，对齐 Laravel {@code Cache::putMany}。
     */
    void putMany(Map<String, Object> values, long ttlSeconds);

    /**
     * 批量读取，对齐 Laravel {@code Cache::getMany}。返回 key->value 映射，缺失键对应 null。
     */
    Map<String, Object> getMany(Collection<String> keys);

    /**
     * 读取或回填，对齐 Laravel {@code Cache::remember}。命中则返回；未命中则调用 loader 取值并写入后返回。
     */
    Object remember(String key, long ttlSeconds, Supplier<Object> loader);

    /**
     * 永久读取或回填，对齐 Laravel {@code Cache::rememberForever}。
     */
    Object rememberForever(String key, Supplier<Object> loader);
}
