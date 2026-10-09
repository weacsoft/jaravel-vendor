package com.weacsoft.jaravel.vendor.cache.store;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheStore;

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
 * {@code increment} / {@code decrement} 采用 get-then-put 实现，<b>非原子</b>
 * （并发自增存在丢失更新；需要严格原子自增请用实现了 {@link com.weacsoft.jaravel.vendor.cache.TtlAwareCacheDriver}
 * 且底层支持原子自增的驱动），当键不存在或值非数字时按 0 起算。
 * <b>TTL 语义</b>：自增/自减会<b>保留原键的剩余 TTL</b>（不再把键写成永不过期）；驱动无法报告
 * 剩余 TTL 时按无 TTL 写入并告警一次。{@code add} / {@code pull} 的原子性是<b>尽力而为</b>：
 * 基础实现是 has→put / get→forget 两步，只有底层驱动提供原子原语时才是真原子。
 * {@code remember} / {@code rememberForever} 实现「命中即返回、未命中则加载并回填」的常规模式。
 */
public class DefaultCacheStore implements CacheStore {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(DefaultCacheStore.class);

    /** 不支持 TTL 能力的驱动只告警一次（按驱动类名去重） */
    private static final java.util.Set<String> TTL_UNSUPPORTED_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        // 驱动提供原子取走原语时优先使用：默认实现是 get→forget 两步，
        // 并发下同一个一次性令牌可能被两个线程同时取到（captcha 等消费方依赖该语义）
        if (driver instanceof com.weacsoft.jaravel.vendor.cache.AtomicCacheDriver atomic) {
            return atomic.pullValue(key(key));
        }
        Object value = get(key);
        if (value != null) {
            forget(key);
        }
        return value;
    }

    @Override
    public boolean add(String key, Object value, long ttlSeconds) {
        // 同理：默认 has→put 两步会让并发 add 都返回 true（互斥门闩失效）
        if (driver instanceof com.weacsoft.jaravel.vendor.cache.AtomicCacheDriver atomic) {
            return atomic.addIfAbsent(key(key), value, ttlSeconds);
        }
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
        // 驱动具备原子自增能力时优先使用（消除丢失更新，且 TTL 由驱动保留）
        if (driver instanceof com.weacsoft.jaravel.vendor.cache.AtomicCacheDriver atomic) {
            return atomic.incrementAndGet(key(key), amount, 0L);
        }
        // 回退：get-then-put 仍非原子，但必须保留原 TTL ——
        // 原实现写死 put(key,next,0)，会把「有期限」的键变成永不过期：
        // 限流计数永久生效、model-cache 版本键无限堆积（审计 M9）。
        long current = toLong(get(key));
        long next = current + amount;
        put(key, next, resolveTtlForRewrite(key));
        return next;
    }

    /**
     * 解析「读改写」操作应写回的 TTL。
     * <p>
     * 取值规则：
     * <ul>
     *   <li>驱动实现 {@link com.weacsoft.jaravel.vendor.cache.TtlAwareCacheDriver}：
     *       按原键剩余 TTL 写回（{@code 0} = 原本就永不过期 → 保持永久）；</li>
     *   <li>键不存在：按无 TTL 写入（与原行为一致，新建计数键）；</li>
     *   <li>驱动不支持该能力：沿用无 TTL + <b>每个驱动类告警一次</b>。</li>
     * </ul>
     * <b>禁止「退化为短 TTL」</b>：把一个长期计数器悄悄变成会中途过期的计数器会引发正确性问题 ——
     * 例如 model-cache 的版本键一旦过期，版本号回落会让旧版本的缓存条目集体「复活」，造成陈旧读。
     *
     * @param key 逻辑键
     * @return 写回的 TTL 秒数
     */
    private long resolveTtlForRewrite(String key) {
        if (driver instanceof com.weacsoft.jaravel.vendor.cache.TtlAwareCacheDriver ttlAware) {
            java.util.OptionalLong remaining = ttlAware.remainingTtlSeconds(key(key));
            return remaining.isPresent() ? remaining.getAsLong() : 0L;
        }
        if (TTL_UNSUPPORTED_WARNED.add(driver.getClass().getName())) {
            logger.warn("[cache] 驱动 {} 未实现 TtlAwareCacheDriver：increment/decrement "
                    + "无法保留原 TTL，键会变成永不过期。给该驱动补上 remainingTtlSeconds(...) 即可修复。",
                    driver.getClass().getName());
        }
        return 0L;
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1L);
    }

    @Override
    public long decrement(String key, long amount) {
        if (driver instanceof com.weacsoft.jaravel.vendor.cache.AtomicCacheDriver atomic) {
            return atomic.incrementAndGet(key(key), -amount, 0L);
        }
        long current = toLong(get(key));
        long next = current - amount;
        put(key, next, resolveTtlForRewrite(key));
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
