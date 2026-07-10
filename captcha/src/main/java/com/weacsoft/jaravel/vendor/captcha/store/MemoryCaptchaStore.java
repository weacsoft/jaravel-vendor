package com.weacsoft.jaravel.vendor.captcha.store;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link ConcurrentHashMap} 的内存验证码存储默认实现。
 * <p>
 * 线程安全，自带 TTL 过期机制：{@link #get(String)} 与 {@link #pull(String)} 读取时会检查过期，
 * 过期则自动删除并返回 {@code null}。另提供 {@link #cleanup()} 主动清理全部过期项。
 * <p>
 * 适用于单机、低并发场景；分布式环境请改用基于 Redis / jaravel cache 模块的实现。
 */
public class MemoryCaptchaStore implements CaptchaStore {

    /** 存储项：answer + 过期时间戳（毫秒） */
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 存储条目，记录答案与过期时间。
     */
    static class Entry {

        final String answer;

        final long expireTime;

        Entry(String answer, long expireTime) {
            this.answer = answer;
            this.expireTime = expireTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    @Override
    public void put(String captchaKey, String answer, long ttlSeconds) {
        long expireTime = System.currentTimeMillis() + ttlSeconds * 1000L;
        store.put(captchaKey, new Entry(answer, expireTime));
    }

    @Override
    public String get(String captchaKey) {
        Entry entry = store.get(captchaKey);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(captchaKey);
            return null;
        }
        return entry.answer;
    }

    @Override
    public String pull(String captchaKey) {
        Entry entry = store.remove(captchaKey);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            return null;
        }
        return entry.answer;
    }

    @Override
    public void remove(String captchaKey) {
        store.remove(captchaKey);
    }

    /**
     * 主动清理所有已过期条目。
     *
     * @return 实际清理的条目数
     */
    public int cleanup() {
        int removed = 0;
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * 返回当前存储条目数（含可能已过期但未触发清理的）。
     *
     * @return 条目数
     */
    public int size() {
        return store.size();
    }
}
