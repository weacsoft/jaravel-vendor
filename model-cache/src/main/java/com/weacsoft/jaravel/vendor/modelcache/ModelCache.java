package com.weacsoft.jaravel.vendor.modelcache;

import com.weacsoft.jaravel.vendor.core.Facade;

import java.util.List;
import java.util.function.Supplier;

/**
 * ModelCache 门面，对齐 Laravel {@code laravel-model-caching} 的静态调用风格。
 * <p>
 * 静态代理 {@link ModelCacheService}（通过 {@link Facade#resolve(Class)} 从 Spring 容器解析），
 * 便于业务代码以 {@code ModelCache::find(...)} 风格调用。
 * <pre>
 * User u = ModelCache.find(User.class, 1L, () -&gt; User.find(1L));
 * List&lt;User&gt; all = ModelCache.findAll(User.class, "all", () -&gt; User.all());
 * ModelCache.invalidate(User.class);
 * </pre>
 */
public final class ModelCache {

    private ModelCache() {
    }

    /** 从容器解析 ModelCacheService */
    private static ModelCacheService svc() {
        return Facade.resolve(ModelCacheService.class);
    }

    /**
     * 缓存按主键查询。
     *
     * @param modelClass 模型类
     * @param id         主键值
     * @param loader     回源加载器
     * @return 实体，未找到返回 {@code null}
     * @see ModelCacheService#find(Class, Object, Supplier)
     */
    public static <T, K> T find(Class<T> modelClass, K id, Supplier<T> loader) {
        return svc().find(modelClass, id, loader);
    }

    /**
     * 缓存查询（返回列表）。
     *
     * @param modelClass 模型类
     * @param queryKey   查询标识
     * @param loader     回源加载器
     * @return 实体列表
     * @see ModelCacheService#findAll(Class, String, Supplier)
     */
    public static <T> List<T> findAll(Class<T> modelClass, String queryKey, Supplier<List<T>> loader) {
        return svc().findAll(modelClass, queryKey, loader);
    }

    /**
     * 缓存任意查询（返回 Object）。
     *
     * @param modelClass 模型类
     * @param queryKey   查询标识
     * @param loader     回源加载器
     * @return 查询结果
     * @see ModelCacheService#query(Class, String, Supplier)
     */
    public static <T> Object query(Class<T> modelClass, String queryKey, Supplier<Object> loader) {
        return svc().query(modelClass, queryKey, loader);
    }

    /**
     * 失效模型类的所有缓存（递增版本号）。
     *
     * @param modelClass 模型类
     * @see ModelCacheService#invalidate(Class)
     */
    public static <T> void invalidate(Class<T> modelClass) {
        svc().invalidate(modelClass);
    }

    /**
     * 失效单条记录的主键查询缓存。
     *
     * @param modelClass 模型类
     * @param id         主键值
     * @see ModelCacheService#invalidate(Class, Object)
     */
    public static <T, K> void invalidate(Class<T> modelClass, K id) {
        svc().invalidate(modelClass, id);
    }

    /**
     * 获取模型类的当前缓存版本号。
     *
     * @param modelClass 模型类
     * @return 当前版本号，初始为 1
     * @see ModelCacheService#getVersion(Class)
     */
    public static <T> long getVersion(Class<T> modelClass) {
        return svc().getVersion(modelClass);
    }

    /**
     * 获取模型的缓存 TTL（秒）。
     *
     * @param modelClass 模型类
     * @return TTL 秒数
     * @see ModelCacheService#getTtl(Class)
     */
    public static <T> long getTtl(Class<T> modelClass) {
        return svc().getTtl(modelClass);
    }

    /**
     * 判断模型类是否可缓存。
     *
     * @param modelClass 模型类
     * @return 是否可缓存
     * @see ModelCacheService#isCachable(Class)
     */
    public static boolean isCachable(Class<?> modelClass) {
        return svc().isCachable(modelClass);
    }
}
