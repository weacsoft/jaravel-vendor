package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * 模型缓存核心服务，对齐 Laravel {@code laravel-model-caching} 的查询缓存能力。
 * <p>
 * 通过 {@link CachableModel} 注解在 Model 类上手动开启缓存。采用<b>版本号机制</b>实现
 * 缓存失效：每个模型类维护一个版本号，所有缓存键都包含当前版本号；失效时递增版本号，
 * 旧版本缓存随 TTL 自然过期清除，无需 tag 支持（{@link CacheStore} 不支持 tag）。
 * <p>
 * 缓存键结构：
 * <ul>
 *   <li>版本号键：{@code {keyPrefix}{modelPrefix}:version}</li>
 *   <li>主键查询：{@code {keyPrefix}{modelPrefix}:v{version}:find:{id}}</li>
 *   <li>任意查询：{@code {keyPrefix}{modelPrefix}:v{version}:query:{queryKey}}</li>
 * </ul>
 * 其中 {@code modelPrefix} 取自 {@link CachableModel#prefix()}，为空时使用类名。
 * <p>
 * 由 {@link ModelCacheAutoConfiguration} 注册为 Bean，业务方可通过 {@link ModelCache} 门面
 * 或直接注入本类使用。TTL 单位为秒（对齐 cache 模块）。
 */
public class ModelCacheService {

    private static final Logger log = LoggerFactory.getLogger(ModelCacheService.class);

    private final CacheManager cacheManager;
    private final ModelCacheProperties properties;

    /**
     * @param cacheManager 缓存管理器（用于按名称解析 store）
     * @param properties   模型缓存配置
     */
    public ModelCacheService(CacheManager cacheManager, ModelCacheProperties properties) {
        this.cacheManager = cacheManager;
        this.properties = properties;
    }

    /**
     * 缓存按主键查询，对齐 Laravel {@code Model::find($id)} 的缓存版本。
     * <p>
     * 未标注 {@link CachableModel} 或全局开关关闭时直接调用 loader 回源。
     * loader 返回 {@code null}（表示未找到）时不缓存，避免缓存穿透时占用空间。
     *
     * @param modelClass 模型类
     * @param id         主键值
     * @param loader     回源加载器
     * @return 实体，未找到返回 {@code null}
     */
    public <T, K> T find(Class<T> modelClass, K id, Supplier<T> loader) {
        if (!isCachable(modelClass)) {
            return loader.get();
        }
        CacheStore store = resolveStore();
        String key = buildKey(modelClass, "find:" + id);
        long ttl = getTtl(modelClass);
        return rememberSkipNull(store, key, ttl, loader);
    }

    /**
     * 缓存查询（返回列表），对齐 Laravel {@code Model::all()} / 条件查询的缓存版本。
     *
     * @param modelClass 模型类
     * @param queryKey   查询标识（用于区分不同查询，建议使用 SQL 哈希或条件字符串）
     * @param loader     回源加载器
     * @return 实体列表
     */
    public <T> List<T> findAll(Class<T> modelClass, String queryKey, Supplier<List<T>> loader) {
        if (!isCachable(modelClass)) {
            return loader.get();
        }
        CacheStore store = resolveStore();
        String key = buildKey(modelClass, "query:" + queryKey);
        long ttl = getTtl(modelClass);
        return rememberSkipNull(store, key, ttl, loader);
    }

    /**
     * 缓存任意查询（返回 Object，如聚合 count、单值字段等）。
     *
     * @param modelClass 模型类
     * @param queryKey   查询标识
     * @param loader     回源加载器
     * @return 查询结果
     */
    public <T> Object query(Class<T> modelClass, String queryKey, Supplier<Object> loader) {
        if (!isCachable(modelClass)) {
            return loader.get();
        }
        CacheStore store = resolveStore();
        String key = buildKey(modelClass, "query:" + queryKey);
        long ttl = getTtl(modelClass);
        return rememberSkipNull(store, key, ttl, loader);
    }

    /**
     * 失效模型类的所有缓存（主键查询 + 任意查询）。
     * <p>
     * 递增版本号，使旧版本缓存键不再被命中，旧缓存随 TTL 自然过期清除。
     *
     * @param modelClass 模型类
     */
    public <T> void invalidate(Class<T> modelClass) {
        CacheStore store = resolveStore();
        String vKey = versionKey(modelClass);
        // 确保版本键已初始化，避免首次失效时 increment 从 0 起算导致版本号未变化
        if (store.get(vKey) == null) {
            store.put(vKey, 1L);
        }
        long newVersion = store.increment(vKey);
        log.debug("模型缓存失效 class={} newVersion={}", modelClass.getName(), newVersion);
    }

    /**
     * 失效单条记录的主键查询缓存。
     * <p>
     * 直接 forget 对应 find 键，不影响版本号与其他查询缓存。
     *
     * @param modelClass 模型类
     * @param id         主键值
     */
    public <T, K> void invalidate(Class<T> modelClass, K id) {
        CacheStore store = resolveStore();
        String key = buildKey(modelClass, "find:" + id);
        store.forget(key);
        log.debug("模型缓存失效（单条） class={} id={}", modelClass.getName(), id);
    }

    /**
     * 获取模型类的当前缓存版本号。
     * <p>
     * 从缓存读取版本号，不存在时初始化为 1 并写入缓存。
     *
     * @param modelClass 模型类
     * @return 当前版本号，初始为 1
     */
    public <T> long getVersion(Class<T> modelClass) {
        CacheStore store = resolveStore();
        String key = versionKey(modelClass);
        Object val = store.get(key);
        if (val == null) {
            store.put(key, 1L);
            return 1L;
        }
        return toLong(val);
    }

    /**
     * 获取模型的缓存 TTL（秒）。
     * <p>
     * 优先读 {@link CachableModel#ttl()}，为负数（含 -1 哨兵值）时使用全局 {@code default-ttl}。
     *
     * @param modelClass 模型类
     * @return TTL 秒数
     */
    public <T> long getTtl(Class<T> modelClass) {
        CachableModel ann = modelClass.getAnnotation(CachableModel.class);
        if (ann != null && ann.ttl() >= 0) {
            return ann.ttl();
        }
        return properties.getDefaultTtl();
    }

    /**
     * 判断模型类是否可缓存。
     * <p>
     * 需同时满足：全局开关开启 + 类上标注 {@link CachableModel}。
     *
     * @param modelClass 模型类
     * @return 是否可缓存
     */
    public boolean isCachable(Class<?> modelClass) {
        if (!properties.isEnabled()) {
            return false;
        }
        return modelClass.isAnnotationPresent(CachableModel.class);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建缓存键：{@code {keyPrefix}{modelPrefix}:v{version}:{suffix}}
     */
    private String buildKey(Class<?> modelClass, String suffix) {
        long version = getVersion(modelClass);
        return properties.getKeyPrefix() + resolveModelPrefix(modelClass)
                + ":v" + version + ":" + suffix;
    }

    /**
     * 版本号键：{@code {keyPrefix}{modelPrefix}:version}
     */
    private String versionKey(Class<?> modelClass) {
        return properties.getKeyPrefix() + resolveModelPrefix(modelClass) + ":version";
    }

    /**
     * 解析模型前缀：{@link CachableModel#prefix()} 非空时用之，否则用类名。
     */
    private String resolveModelPrefix(Class<?> modelClass) {
        CachableModel ann = modelClass.getAnnotation(CachableModel.class);
        if (ann != null && !ann.prefix().isEmpty()) {
            return ann.prefix();
        }
        return modelClass.getSimpleName();
    }

    /**
     * 解析配置的缓存 store。
     */
    private CacheStore resolveStore() {
        return cacheManager.store(properties.getStore());
    }

    /**
     * remember 语义（命中返回、未命中加载并回填），但 loader 返回 null 时不回填，
     * 避免缓存未命中结果（如 find 未找到记录）。对齐 Laravel {@code Cache::remember} 但更安全。
     */
    @SuppressWarnings("unchecked")
    private <R> R rememberSkipNull(CacheStore store, String key, long ttl, Supplier<R> loader) {
        Object cached = store.get(key);
        if (cached != null) {
            return (R) cached;
        }
        R value = loader.get();
        if (value != null) {
            store.put(key, value, ttl);
        }
        return value;
    }

    /**
     * 将缓存中的版本号值转为 long。
     */
    private long toLong(Object val) {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}
