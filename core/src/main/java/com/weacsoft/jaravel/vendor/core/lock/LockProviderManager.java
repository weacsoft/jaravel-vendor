package com.weacsoft.jaravel.vendor.core.lock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式锁提供者管理器，维护多个命名 {@link LockProvider}，按名称解析或返回默认实例。
 * <p>
 * 对齐 {@code CacheManager} / {@code StorageManager} 的设计思路：多种注册方式共存，
 * 注解声明优先，产物不进 Spring 容器，只存入本 Manager 的内部注册表。
 *
 * <h3>注册方式</h3>
 * <ol>
 *   <li><b>注解声明式</b>（推荐）：通过 {@link RegisterLockProvider @RegisterLockProvider} 声明</li>
 *   <li><b>手动调用</b>：直接调用 {@link #addProvider(String, LockProvider)}</li>
 * </ol>
 */
public class LockProviderManager {

    /** 已注册的 provider：name -> LockProvider，进程级共享，线程安全 */
    private final Map<String, LockProvider> providers = new ConcurrentHashMap<>();

    /** 默认 provider 名称 */
    private String defaultProvider = "sync";

    /** 同步锁（默认兜底） */
    private final LockProvider syncProvider = new LockProvider() {
        @Override
        public boolean tryLock(String key, long ttlSeconds) {
            return true;
        }

        @Override
        public void unlock(String key) {
        }
    };

    /**
     * 返回默认 provider。首次访问时若默认名称的 provider 未注册，自动兜底为同步锁。
     */
    public LockProvider provider() {
        return provider(defaultProvider);
    }

    /**
     * 按名称返回指定 provider。
     *
     * @param name provider 名称
     * @return provider 实例
     * @throws IllegalStateException provider 未注册
     */
    public LockProvider provider(String name) {
        String providerName = (name == null || name.isEmpty()) ? defaultProvider : name;
        LockProvider p = providers.get(providerName);
        if (p != null) {
            return p;
        }
        // 未注册时兜底为同步锁
        return syncProvider;
    }

    /**
     * 手动注册一个命名 provider（编程式注册）。
     *
     * @param name    provider 名称
     * @param provider provider 实例
     */
    public void addProvider(String name, LockProvider provider) {
        providers.put(name, provider);
    }

    /**
     * 设置默认 provider 名称。
     */
    public void setDefaultProvider(String name) {
        this.defaultProvider = name;
    }

    /**
     * @return 默认 provider 名称
     */
    public String getDefaultProvider() {
        return defaultProvider;
    }

    /**
     * 检查是否注册了指定名称的 provider。
     *
     * @param name provider 名称
     * @return 已注册返回 true
     */
    public boolean hasProvider(String name) {
        return name != null && providers.containsKey(name);
    }

    /**
     * 获取所有已注册的 provider 名称。
     *
     * @return provider 名称集合
     */
    public Set<String> providerNames() {
        return Set.copyOf(providers.keySet());
    }
}