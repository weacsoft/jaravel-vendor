package com.weacsoft.jaravel.vendor.cache;

/**
 * 可选能力接口：驱动提供<b>原子</b>的写入/取走/自增原语。
 * <p>
 * <b>为什么需要它</b>：{@code CacheStore.add/pull} 的默认实现是「先检查后写入 / 先读后删」两步，
 * {@code increment} 是「读改写」。并发下会出现：
 * <ul>
 *   <li>{@code add} 两个线程都返回 true → 「单次执行 / 互斥门闩」语义失效；</li>
 *   <li>{@code pull} 两个线程都拿到同一个一次性令牌；</li>
 *   <li>{@code increment} 丢失更新（计数偏小，限流/配额判断失真）。</li>
 * </ul>
 * 实现本接口的驱动由 {@code DefaultCacheStore} 优先走原子路径；未实现的驱动回退到两步实现，
 * 并在接口文档中明确标注「原子性为尽力而为」，绝不假装原子。
 * <p>
 * <b>内置实现</b>：内存驱动（{@code ConcurrentHashMap.compute}/{@code remove}）、
 * Redis 驱动（{@code SET NX} / Lua {@code GET+DEL} / {@code INCRBY}）。
 * 数据库驱动需要事务支持（依赖 {@code JdbcExecutor.inTransaction}），尚在待办。
 */
public interface AtomicCacheDriver extends CacheDriver {

    /**
     * 仅在键不存在（或已过期）时写入，原子。
     *
     * @param key        物理键
     * @param value      值
     * @param ttlSeconds 过期秒数，{@code <= 0} 表示永不过期
     * @return 实际写入返回 true；键已存在返回 false
     */
    boolean addIfAbsent(String key, Object value, long ttlSeconds);

    /**
     * 原子地取出并删除（一次性令牌语义）。
     *
     * @param key 物理键
     * @return 值；键不存在或已过期返回 {@code null}
     */
    Object pullValue(String key);

    /**
     * 原子自增并返回新值。
     * <p>
     * 必须<b>保留原键的 TTL</b>（Redis 的 {@code INCRBY} 原生保留；内存实现用 {@code compute} 保留 expiryAt）。
     *
     * @param key    物理键
     * @param amount 步长（可为负）
     * @param ttlIfAbsentSeconds 键不存在时的 TTL（{@code <= 0} = 永不过期）
     * @return 自增后的值
     */
    long incrementAndGet(String key, long amount, long ttlIfAbsentSeconds);
}