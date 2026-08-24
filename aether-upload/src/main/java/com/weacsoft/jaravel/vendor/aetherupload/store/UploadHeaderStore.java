package com.weacsoft.jaravel.vendor.aetherupload.store;

/**
 * 上传记录头存储契约。
 * <p>
 * 存储上传任务的记录头 JSON 与 identifier → resourceId 的续传映射。
 * 实现：
 * <ul>
 *   <li>{@link MemoryUploadHeaderStore}：进程内存（默认）</li>
 *   <li>{@link CacheUploadHeaderStore}：委托 cache 模块 {@code CacheStore}（配置 redis store 即为 redis 记录头，多实例共享）</li>
 * </ul>
 */
public interface UploadHeaderStore {

    /**
     * 写入值。
     *
     * @param key        键
     * @param value      值（JSON 字符串或 resourceId）
     * @param ttlSeconds 过期秒数，{@code <= 0} 表示永不过期
     */
    void put(String key, String value, long ttlSeconds);

    /**
     * 读取值，不存在或已过期返回 {@code null}。
     */
    String get(String key);

    /**
     * 删除键。
     */
    void remove(String key);
}
