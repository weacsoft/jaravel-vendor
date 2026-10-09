package com.weacsoft.jaravel.vendor.cache;

import java.util.OptionalLong;

/**
 * 可选能力接口：驱动能报告键的剩余 TTL。
 * <p>
 * <b>为什么需要它</b>：{@code CacheStore.increment/decrement} 是「读改写」语义
 * （读取当前值 → 加/减 → 写回）。若写回时不带 TTL，键就从「有期限」变成「永不过期」——
 * 限流计数会永久生效、缓存版本键（如 model-cache）会无限堆积。
 * <p>
 * <b>为什么是独立接口而不是往 {@link CacheDriver} 加 {@code default} 方法</b>：
 * 「能否报告剩余 TTL」是<b>驱动能力差异</b>（Redis 原生 TTL、内存驱动读 expiryAt、
 * 数据库读 expires_at，而外部自定义驱动可能完全没有过期概念），把它做成显式能力，
 * {@code CacheDriver} 就能保持冻结，第三方实现不会被强制承担这一语义。
 * <p>
 * <b>实现约定</b>（务必三态分明，不要用单一哨兵值混淆）：
 * <ul>
 *   <li>{@link OptionalLong#empty()}：键<b>不存在</b>，或该驱动无法确定剩余 TTL；</li>
 *   <li>{@code OptionalLong.of(0)}：键存在且<b>永不过期</b>；</li>
 *   <li>{@code OptionalLong.of(n)}（{@code n > 0}）：键存在的剩余秒数。</li>
 * </ul>
 * 内置驱动（内存 / Redis / 文件 / 数据库）对存在的键都<b>不应</b>返回 empty。
 */
public interface TtlAwareCacheDriver extends CacheDriver {

    /**
     * 查询键的剩余 TTL。
     *
     * @param key 物理键（已含调用方前缀）
     * @return empty = 键不存在或无法确定；0 = 永不过期；正数 = 剩余秒数
     */
    OptionalLong remainingTtlSeconds(String key);
}