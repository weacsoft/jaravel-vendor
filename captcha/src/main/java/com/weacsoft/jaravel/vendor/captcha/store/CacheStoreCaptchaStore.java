package com.weacsoft.jaravel.vendor.captcha.store;

import com.weacsoft.jaravel.vendor.cache.CacheStore;

/**
 * 基于 jaravel {@link CacheStore} 的验证码存储适配器。
 * <p>
 * 当项目引入了 jaravel cache 模块时，可通过此类将验证码答案存入 CacheStore（底层可以是 Redis、数据库等），
 * 实现跨进程验证码共享。未引入 cache 模块时使用 {@link MemoryCaptchaStore}。
 * <p>
 * key 前缀默认为 {@code captcha:}，避免与其他业务缓存冲突。
 */
public class CacheStoreCaptchaStore implements CaptchaStore {

    private static final String KEY_PREFIX = "captcha:";

    private final CacheStore cacheStore;

    public CacheStoreCaptchaStore(CacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    @Override
    public void put(String captchaKey, String answer, long ttlSeconds) {
        cacheStore.put(KEY_PREFIX + captchaKey, answer, ttlSeconds);
    }

    @Override
    public String get(String captchaKey) {
        Object value = cacheStore.get(KEY_PREFIX + captchaKey);
        return value != null ? value.toString() : null;
    }

    @Override
    public String pull(String captchaKey) {
        Object value = cacheStore.pull(KEY_PREFIX + captchaKey);
        return value != null ? value.toString() : null;
    }

    @Override
    public void remove(String captchaKey) {
        cacheStore.forget(KEY_PREFIX + captchaKey);
    }

    /**
     * 原子占用：委托给 {@link CacheStore#add}（「仅当键不存在时写入」）。
     * <p>
     * 若底层驱动实现了 {@code AtomicCacheDriver}（内存 / Redis / 数据库），
     * 该操作是真原子；否则是尽力而为 —— 与 {@code CacheStore} 的契约一致。
     */
    @Override
    public boolean putIfAbsent(String captchaKey, String value, long ttlSeconds) {
        return cacheStore.add(KEY_PREFIX + captchaKey, value, ttlSeconds);
    }
}
