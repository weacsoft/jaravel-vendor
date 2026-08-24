package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓存管理器，对齐 Laravel {@code Illuminate\Cache\CacheManager}。
 * <p>
 * 维护多个命名 {@link CacheStore}（如 {@code array}、{@code file}、{@code redis} 等），
 * 按名称解析 store 并提供默认 store。线程安全（基于 {@link ConcurrentHashMap}）。
 * <p>
 * 采用工厂模式 + support 方法匹配（对齐 Auth/Storage 模块的工厂设计）：
 * <ul>
 *   <li><b>驱动工厂</b>（{@link CacheDriverFactory}）：创建缓存驱动实例（如 ArrayCacheDriver、RedisCacheDriver）</li>
 * </ul>
 * CacheManager 在创建 store 时遍历所有已注册的工厂，找到第一个匹配的工厂并调用 {@code create}。
 * 第三方模块只需将工厂实现注册为 Spring Bean，{@code CacheAutoConfiguration} 会自动收集并注册。
 *
 * <h3>启发式注册（用到才创建）</h3>
 * 配置式/注解式声明的 store 定义在注册时<b>不会</b>立即创建 {@link CacheStore} 实例，
 * 而是在首次 {@link #store(String)} 解析时通过驱动工厂创建并缓存。
 * 这样可以避免启动时因某个 store（如 redis）不可用而导致整个应用启动失败，
 * 同时也保证驱动工厂的注册顺序不影响 store 定义的注册顺序。
 * 对齐 {@code StorageManager} 的 definitions + instances 双层设计。
 *
 * <h3>三种注册方式（可共存，注解声明优先）</h3>
 * <ol>
 *   <li><b>注解声明式</b>（推荐）：在 Config 类中用
 *       {@link RegisterCacheStore @RegisterCacheStore} 声明 {@link CacheStore}。
 *       {@code CacheStoreRegistrar} 扫描注解并注册（直接放入实例缓存，优先级最高）</li>
 *   <li><b>配置式</b>：通过 {@code jaravel.cache.stores} 配置，由工厂驱动按配置自动创建（延迟）</li>
 *   <li><b>手动调用</b>：直接调用 {@link #addStore}（向后兼容 / 测试友好）</li>
 * </ol>
 *
 * <h3>手动注册（编程式）</h3>
 * 业务方也可通过 {@link #addStore(String, CacheStore)} 手动注册自定义 store，
 * 手动注册优先于配置式（同名时覆盖，并清除对应的延迟定义）。
 */
public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    /** store 定义（延迟创建）：name -> StoreDefinition，进程级共享，启动后只读 */
    private final Map<String, StoreDefinition> storeConfigs = new ConcurrentHashMap<>();

    /** 已创建的 store 实例（延迟创建后缓存）：name -> CacheStore */
    private final Map<String, CacheStore> stores = new ConcurrentHashMap<>();

    /** 驱动工厂列表（工厂模式），进程级共享，启动后只读 */
    private final List<CacheDriverFactory> driverFactories = new CopyOnWriteArrayList<>();

    /** 缓存全局配置（供延迟创建时补充顶层快捷配置如 file-dir、database-table） */
    private CacheConfig properties;

    /** 默认 store 名称 */
    private String defaultStore = "array";

    /** 缓存键前缀 */
    private String prefix = "";

    /** store 定义记录：驱动名 + 驱动配置 */
    private record StoreDefinition(String driver, Map<String, Object> config) {
    }

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
     * 从配置初始化 store 定义（延迟创建，不立即实例化）。
     * <p>
     * 根据 {@code CacheConfig.stores} 配置注册 store 定义：
     * <ol>
     *   <li>若 stores 为空，只注册 default-store 定义（driver 名 = store 名）</li>
     *   <li>若 stores 非空，为每个配置项注册对应的 store 定义</li>
     *   <li>确保 default-store 一定有定义（stores 中未配置时自动补充）</li>
     * </ol>
     * 实际的 {@link CacheStore} 实例在首次 {@link #store(String)} 调用时才创建。
     *
     * @param properties 缓存配置（纯 Java POJO，无框架依赖）
     */
    public void initFromConfig(CacheConfig properties) {
        this.properties = properties;
        this.defaultStore = properties.getDefaultStore();
        this.prefix = properties.getPrefix() == null ? "" : properties.getPrefix();

        Map<String, CacheConfig.StoreConfig> storesConfig = properties.getStores();

        if (storesConfig == null || storesConfig.isEmpty()) {
            // stores 未配置：只注册 default-store 定义（driver 名与 store 名相同）
            logger.info("[cache] stores 未配置，只注册默认 store 定义: {}", defaultStore);
            storeConfigs.put(defaultStore, new StoreDefinition(defaultStore, Map.of()));
            return;
        }

        // 按 stores 配置注册定义
        for (Map.Entry<String, CacheConfig.StoreConfig> entry : storesConfig.entrySet()) {
            String storeName = entry.getKey();
            CacheConfig.StoreConfig cfg = entry.getValue();
            String driverName = cfg.getDriver() != null ? cfg.getDriver() : storeName;
            storeConfigs.put(storeName, new StoreDefinition(driverName, cfg.toConfigMap()));
        }

        // 确保 default-store 有定义
        if (!storeConfigs.containsKey(defaultStore)) {
            logger.info("[cache] default-store '{}' 未在 stores 中配置，自动补充定义", defaultStore);
            storeConfigs.put(defaultStore, new StoreDefinition(defaultStore, Map.of()));
        }
    }

    /**
     * 返回默认 store（首次访问时延迟创建）。
     */
    public CacheStore store() {
        return store(defaultStore);
    }

    /**
     * 按名称返回指定 store（首次访问时延迟创建并缓存）。
     * <p>
     * 解析顺序：
     * <ol>
     *   <li>实例缓存（已创建的 store，含手动注册和注解注册的）</li>
     *   <li>定义缓存（配置式注册的 store 定义）→ 首次命中时通过驱动工厂创建并缓存</li>
     *   <li>均未命中 → 抛出 {@link IllegalStateException}</li>
     * </ol>
     *
     * @param name store 名称，{@code null} 时使用默认 store
     * @return store 实例
     * @throws IllegalStateException store 未注册或驱动未知
     */
    public CacheStore store(String name) {
        String storeName = (name == null || name.isEmpty()) ? defaultStore : name;
        CacheStore cached = stores.get(storeName);
        if (cached != null) {
            return cached;
        }
        return stores.computeIfAbsent(storeName, this::createStore);
    }

    /**
     * 按定义创建 store 实例，遍历驱动工厂列表找到第一个匹配的工厂。
     *
     * @param storeName store 名称
     * @return store 实例
     * @throws IllegalStateException store 未注册定义或驱动未知
     */
    private CacheStore createStore(String storeName) {
        StoreDefinition def = storeConfigs.get(storeName);
        if (def == null) {
            throw new IllegalStateException("未注册的缓存 store: " + storeName);
        }

        // 补充顶层快捷配置到 config
        Map<String, Object> fullConfig = new LinkedHashMap<>(def.config());
        if (properties != null) {
            if ("file".equalsIgnoreCase(def.driver()) && !fullConfig.containsKey("dir")
                    && properties.getFileDir() != null && !properties.getFileDir().isEmpty()) {
                fullConfig.put("dir", properties.getFileDir());
            }
            if ("database".equalsIgnoreCase(def.driver()) && !fullConfig.containsKey("table")
                    && properties.getDatabaseTable() != null && !properties.getDatabaseTable().isEmpty()) {
                fullConfig.put("table", properties.getDatabaseTable());
            }
        }

        // 工厂模式：遍历所有工厂，找到第一个匹配的
        for (CacheDriverFactory factory : driverFactories) {
            if (factory.support(def.driver())) {
                CacheDriver driver = factory.create(fullConfig);
                logger.info("[cache] 创建 store: name={}, driver={}", storeName, def.driver());
                return new DefaultCacheStore(driver, prefix);
            }
        }
        throw new IllegalStateException(
                "未知 cache driver: " + def.driver() + "，请引入对应插件（如 redis-cache 模块）");
    }

    /**
     * 手动注册一个命名 store（编程式注册，优先于配置式）。
     * <p>
     * 会同时清除同名的延迟定义，保证本实例优先生效。
     *
     * @param name  store 名称
     * @param store store 实例
     */
    public void addStore(String name, CacheStore store) {
        storeConfigs.remove(name);
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
     * 检查是否注册了指定名称的 store（含尚未实例化的定义）。
     *
     * @param name store 名称
     * @return 已注册返回 true
     */
    public boolean hasStore(String name) {
        return name != null && (storeConfigs.containsKey(name) || stores.containsKey(name));
    }

    /**
     * 检查是否注册了任何 store。
     *
     * @return 已注册至少一个 store 返回 true
     */
    public boolean hasStores() {
        return !storeConfigs.isEmpty() || !stores.isEmpty();
    }

    /**
     * 获取所有已注册的 store 名称（含尚未实例化的定义）。
     *
     * @return store 名称集合（不可变）
     */
    public Set<String> storeNames() {
        Set<String> names = ConcurrentHashMap.newKeySet();
        names.addAll(storeConfigs.keySet());
        names.addAll(stores.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * 清空所有 store 实例缓存（配置热更新或测试用），保留定义与驱动工厂。
     */
    public void flushInstances() {
        stores.clear();
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
