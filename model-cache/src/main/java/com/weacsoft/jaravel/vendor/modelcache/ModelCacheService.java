package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * 便捷构造器：properties 使用默认值。
     *
     * @param cacheManager 缓存管理器
     */
    public ModelCacheService(CacheManager cacheManager) {
        this(cacheManager, new ModelCacheProperties());
    }

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
        // 命中值可能是反序列化后的 LinkedHashMap（非内存 store）→ 按实体类型还原；
        // 不可还原则驱逐并回源，绝不把错类型交给调用方（审计 M16）
        return cachedOrReload(store, key, ttl, modelClass, loader);
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
        return cachedOrReloadList(store, key, ttl, modelClass, loader);
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
        // query 的契约是「任意 Object」（count / 标量 / 投影），**不做类型还原**（审计 M16）
        return rememberSkipNull(store, key, ttl, loader);
    }

    /**
     * 读缓存命中并还原类型；<b>不可还原时驱逐该键并回源</b>（审计 M16）。
     * <p>
     * 为什么不「返回原值 + WARN」：那会把类型契约变成「有时实体、有时 LinkedHashMap」——
     * 调用点仍会 CCE（只是延后），且按类型分派的逻辑（instanceof、序列化、权限判断）可能走错分支。
     * 正确做法是驱逐该键 + 回源一次（退化为一次 DB 命中），保证返回的始终是声明类型。
     * <p>
     * 注意：array（内存）store 命中的是 gaarason <b>托管实体</b>，{@code isInstance} 短路会原样返回
     * 同一实例，不会被 JSON 转换成游离 POJO；只有元素是 {@code Map} 时才转换（按元素嗅探，
     * 不按 store 名判定）。转换后是游离对象，对象身份/懒加载语义与托管实体不同。
     *
     * @param store      store
     * @param key        缓存键
     * @param ttl        TTL
     * @param modelClass 实体类型
     * @param collection 期望是否为集合
     * @param loader     回源加载器
     * @param <T>        目标类型
     * @return 声明类型的值（命中可还原 / 驱逐后回源）
     */
    @SuppressWarnings("unchecked")
    private <T> T cachedOrReload(CacheStore store, String key, long ttl, Class<T> modelClass,
                                 Supplier<T> loader) {
        Object cached = store.get(key);
        if (cached != null) {
            T coerced = coerceOrNull(cached, modelClass, false);
            if (coerced != null) {
                return coerced;
            }
            store.forget(key);
            warnCoerceFailureOnce(modelClass, null);
        }
        T value = loader.get();
        if (value != null) {
            store.put(key, value, ttl);
        }
        return value;
    }

    /**
     * 集合版本：命中可还原返回还原后的列表，否则驱逐 + 回源。
     *
     * @param store       store
     * @param key         缓存键
     * @param ttl         TTL
     * @param elementType 元素实体类型
     * @param loader      回源加载器
     * @param <T>         元素类型
     * @return 声明元素类型的列表
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> cachedOrReloadList(CacheStore store, String key, long ttl, Class<T> elementType,
                                           Supplier<List<T>> loader) {
        Object cached = store.get(key);
        if (cached != null) {
            List<T> coerced = (List<T>) coerceOrNull(cached, elementType, true);
            if (coerced != null) {
                return coerced;
            }
            store.forget(key);
            warnCoerceFailureOnce(elementType, null);
        }
        List<T> value = loader.get();
        if (value != null) {
            store.put(key, value, ttl);
        }
        return value;
    }

    /**
     * 尝试把缓存值还原为声明类型。
     *
     * @param value      缓存值（可能是实体、Map、List&lt;Map&gt;）
     * @param modelClass 实体类型
     * @param collection 期望是否为集合
     * @param <T>        目标类型
     * @return 还原成功返回目标类型实例；<b>无法还原返回 {@code null}</b>（调用方据此驱逐 + 回源）
     */
    @SuppressWarnings("unchecked")
    private <T> T coerceOrNull(Object value, Class<T> modelClass, boolean collection) {
        if (value == null || modelClass == null) {
            return null;
        }
        if (collection) {
            if (!(value instanceof List<?> list)) {
                return null;
            }
            List<Object> converted = new ArrayList<>(list.size());
            boolean convertedAny = false;
            for (Object element : list) {
                if (element == null || modelClass.isInstance(element)) {
                    converted.add(element);
                    continue;
                }
                if (element instanceof Map<?, ?> map) {
                    try {
                        converted.add(com.weacsoft.jaravel.vendor.json.Json.convert(map, modelClass));
                        convertedAny = true;
                        continue;
                    } catch (Exception e) {
                        return null;   // 整份不可还原 → 交由调用方驱逐 + 回源（绝不半转换）
                    }
                }
                return null;           // 含未知类型元素 → 不可还原
            }
            return convertedAny ? (T) converted : (T) value;
        }
        if (modelClass.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return com.weacsoft.jaravel.vendor.json.Json.convert(map, modelClass);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** 已告警过的模型类（**按模型记忆**：进程级单例会让第二个坏模型永久静默，架构评审 R5） */
    private static final java.util.Set<Class<?>> COERCE_FAILURE_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void warnCoerceFailureOnce(Class<?> modelClass, Exception cause) {
        if (COERCE_FAILURE_WARNED.add(modelClass)) {
            log.warn("[model-cache] 缓存命中值的类型还原失败（{}）：{}。"
                    + "已驱逐该键并回源 loader（保证返回声明类型），请确认该 store 是否需要类型化序列化。",
                    modelClass.getSimpleName(), cause == null ? "结构不匹配" : cause.getMessage());
        }
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
     * 解析配置的缓存 store：为空时使用 cache 模块的默认 store，显式指定时按名解析，
     * 未注册时回退到默认 store。与 jwt / wechat-sdk 保持一致。
     */
    private CacheStore resolveStore() {
        String storeName = properties.getStore();
        CacheStore store;
        if (storeName == null || storeName.isEmpty()) {
            store = cacheManager.store();
        } else {
            try {
                store = cacheManager.store(storeName);
            } catch (IllegalStateException e) {
                log.debug("[model-cache] 缓存 store '{}' 未注册，回退到默认 store: {}", storeName, e.getMessage());
                store = cacheManager.store();
            }
        }
        warnIfNonInMemoryStore(store);
        return store;
    }

    /** 「非内存 store」告警只打一次（进程级） */
    private static final java.util.concurrent.atomic.AtomicBoolean NON_MEMORY_STORE_WARNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 非内存（序列化）store 的两条已知限制告警（审计 M16/N7）。
     * <p>
     * 1. <b>类型保真度</b>：{@code find}/{@code findAll} 命中后会做元素级还原（Map → 实体），
     *    但 JSON 往返<b>会丢失</b>懒加载/派生字段/被 {@code @JsonIgnore} 的字段 —— 还原成功
     *    也可能是有损的（比 CCE 更隐蔽：字段不全的实体）。含派生/关联字段的实体不建议放进
     *    多机序列化 store。<br>
     * 2. <b>租户维度</b>：缓存键为 {@code keyPrefix+modelPrefix+:v{版本}:{suffix}}，<b>不含租户/用户维度</b>，
     *    而 {@code queryKey} 由调用方拼 —— 多租户共享同一 store 时会互相命中对方的行数据。
     * <p>
     * 另注：{@code query} 的返回值是「任意 Object」（count / 标量 / 投影），<b>不做类型还原</b>。
     *
     * @param store 已解析的 store
     */
    private void warnIfNonInMemoryStore(CacheStore store) {
        if (store == null || NON_MEMORY_STORE_WARNED.get()) {
            return;
        }
        String name = store.getClass().getSimpleName().toLowerCase();
        boolean inMemory = name.contains("array") || name.contains("memory") || name.contains("simple");
        if (inMemory) {
            return;
        }
        if (NON_MEMORY_STORE_WARNED.compareAndSet(false, true)) {
            log.warn("[model-cache] 当前缓存 store 为 {}（非内存）：①命中后经 JSON 还原的实体可能"
                    + "有损（丢失懒加载/派生字段）；②缓存键不含租户维度，多租户共用一个 store 时会"
                    + "互相命中对方数据 —— 请让 queryKey 自含租户标识（如 \"tenant:{id}:{条件}\"）"
                    + "或为每个租户使用独立 store；③query 的返回值不做类型还原。", store.getClass().getSimpleName());
        }
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
