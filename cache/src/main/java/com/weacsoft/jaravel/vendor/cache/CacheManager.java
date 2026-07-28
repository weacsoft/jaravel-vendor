package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheProperties;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓存管理器，对齐 Laravel {@code Illuminate\Cache\CacheManager}。
 * <p>
 * 维护多个命名 {@link CacheStore}（如 {@code array}、{@code file}、{@code redis} 等），
 * 按名称解析 store 并提供默认 store。线程安全（基于 {@link ConcurrentHashMap}）。
 * <p>
 * 采用工厂模式 + support 方法匹配（对齐 Auth 模块的双层工厂设计）：
 * <ul>
 *   <li><b>驱动工厂</b>（{@link CacheDriverFactory}）：创建缓存驱动实例（如 ArrayCacheDriver、RedisCacheDriver）</li>
 * </ul>
 * CacheManager 在创建 store 时遍历所有已注册的工厂，找到第一个匹配的工厂并调用 {@code create}。
 * 第三方模块只需将工厂实现注册为 Spring Bean，{@code CacheAutoConfiguration} 会自动收集并注册。
 *
 * <h3>按需创建 store</h3>
 * CacheManager 启动时根据 {@code CacheProperties.stores} 配置创建 store：
 * <ul>
 *   <li>只有配置在 stores 中的 store 才会被创建</li>
 *   <li>若 stores 为空，则只创建 default-store 对应的默认 store（driver 名与 store 名相同）</li>
 *   <li>每个 store 的 driver 在创建时才实例化（用到才构建），不会全部预创建</li>
 * </ul>
 *
 * <h3>手动注册（编程式）</h3>
 * 业务方也可通过 {@link #addStore(String, CacheStore)} 手动注册自定义 store，
 * 手动注册优先于配置式（同名时覆盖）。
 */
public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    /** 命名 store 注册表：name -> CacheStore */
    private final Map<String, CacheStore> stores = new ConcurrentHashMap<>();

    /** 驱动工厂列表（工厂模式），进程级共享，启动后只读 */
    private final List<CacheDriverFactory> driverFactories = new CopyOnWriteArrayList<>();

    /** 默认 store 名称 */
    private String defaultStore = "array";

    /** 缓存键前缀 */
    private String prefix = "";

    /** 无参构造器（供 fallback 使用） */
    public CacheManager() {
    }

    /**
     * 注册驱动工厂（工厂模式）。
     *
     * @param factory 驱动工厂实例
     */
    public void registerDriverFactory(CacheDriverFactory factory) {
        driverFactories.add(factory);
    }

    /**
     * 从配置初始化 store。
     * <p>
     * 根据 {@code CacheProperties.stores} 配置创建 store：
     * <ol>
     *   <li>若 stores 为空，只创建 default-store（driver 名 = store 名）</li>
     *   <li>若 stores 非空，为每个配置项创建对应的 store</li>
     *   <li>确保 default-store 一定存在（stores 中未配置时自动补充）</li>
     * </ol>
     *
     * @param properties 缓存配置
     */
    public void initFromConfig(CacheProperties properties) {
        this.defaultStore = properties.getDefaultStore();
        this.prefix = properties.getPrefix() == null ? "" : properties.getPrefix();

        Map<String, CacheProperties.StoreConfig> storesConfig = properties.getStores();

        if (storesConfig == null || storesConfig.isEmpty()) {
            // stores 未配置：只创建 default-store（driver 名与 store 名相同）
            logger.info("[cache] stores 未配置，只创建默认 store: {}", defaultStore);
            createStore(defaultStore, defaultStore, Map.of(), properties);
            return;
        }

        // 按 stores 配置创建
        for (Map.Entry<String, CacheProperties.StoreConfig> entry : storesConfig.entrySet()) {
            String storeName = entry.getKey();
            CacheProperties.StoreConfig cfg = entry.getValue();
            String driverName = cfg.getDriver() != null ? cfg.getDriver() : storeName;
            createStore(storeName, driverName, cfg.toConfigMap(), properties);
        }

        // 确保 default-store 存在
        if (!stores.containsKey(defaultStore)) {
            logger.info("[cache] default-store '{}' 未在 stores 中配置，自动创建", defaultStore);
            createStore(defaultStore, defaultStore, Map.of(), properties);
        }
    }

    /**
     * 创建并注册一个 store。
     *
     * @param storeName  store 名称
     * @param driverName 驱动名称
     * @param config     驱动配置参数
     * @param properties 缓存全局配置（用于补充顶层配置如 file-dir、database-table）
     */
    private void createStore(String storeName, String driverName, Map<String, Object> config,
                             CacheProperties properties) {
        // 补充顶层快捷配置到 config
        Map<String, Object> fullConfig = new java.util.LinkedHashMap<>(config);
        if ("file".equalsIgnoreCase(driverName) && !fullConfig.containsKey("dir")
                && properties.getFileDir() != null && !properties.getFileDir().isEmpty()) {
            fullConfig.put("dir", properties.getFileDir());
        }
        if ("database".equalsIgnoreCase(driverName) && !fullConfig.containsKey("table")
                && properties.getDatabaseTable() != null && !properties.getDatabaseTable().isEmpty()) {
            fullConfig.put("table", properties.getDatabaseTable());
        }

        // 工厂模式：遍历所有工厂，找到第一个匹配的
        for (CacheDriverFactory factory : driverFactories) {
            if (factory.support(driverName)) {
                CacheDriver driver = factory.create(fullConfig);
                CacheStore store = new DefaultCacheStore(driver, prefix);
                stores.put(storeName, store);
                logger.info("[cache] 创建 store: name={}, driver={}", storeName, driverName);
                return;
            }
        }
        throw new IllegalStateException(
                "未知 cache driver: " + driverName + "，请引入对应插件（如 redis-cache 模块）");
    }

    /**
     * 返回默认 store。
     */
    public CacheStore store() {
        return store(defaultStore);
    }

    /**
     * 按名称返回指定 store，未注册则抛出异常。
     */
    public CacheStore store(String name) {
        CacheStore store = stores.get(name);
        if (store == null) {
            throw new IllegalStateException("未注册的缓存 store: " + name);
        }
        return store;
    }

    /**
     * 手动注册一个命名 store（编程式注册，优先于配置式）。
     *
     * @param name  store 名称
     * @param store store 实例
     */
    public void addStore(String name, CacheStore store) {
        stores.put(name, store);
    }

    /**
     * 设置默认 store 名称。
     */
    public void setDefaultStore(String name) {
        this.defaultStore = name;
    }

    /**
     * @return 默认 store 名称
     */
    public String getDefaultStore() {
        return defaultStore;
    }

    /**
     * 创建默认的内存缓存 store（基于 ArrayCacheDriver），供模块在 CacheManager 未注入时作为 fallback 使用。
     *
     * @return 内存缓存 store
     */
    public static CacheStore createDefaultStore() {
        return new DefaultCacheStore(new ArrayCacheDriver(), "");
    }
}
