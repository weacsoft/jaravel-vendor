package com.weacsoft.jaravel.vendor.cache;

import java.util.Map;

/**
 * 缓存驱动工厂契约，采用工厂模式 + support 方法匹配，对齐 Auth 模块的
 * {@code UserProviderDriver} / {@code AuthGuardDriver} 设计。
 * <p>
 * 每个工厂实现自行声明 {@link #support(String)} 方法，当传入的 driver 名称匹配时返回 {@code true}，
 * 由 {@link CacheManager} 在创建 store 时遍历所有已注册的工厂，找到第一个匹配的工厂并调用
 * {@link #create} 创建 {@link CacheDriver} 实例。
 *
 * <h3>内置驱动</h3>
 * <ul>
 *   <li>{@code array} — 内存缓存（cache 模块内置）</li>
 *   <li>{@code file} — 文件缓存（cache 模块内置）</li>
 *   <li>{@code database} — 数据库缓存（cache 模块内置，需 DataSource）</li>
 *   <li>{@code redis} — Redis 缓存（redis-cache 模块提供）</li>
 * </ul>
 *
 * <h3>扩展驱动</h3>
 * 第三方模块只需实现本接口并注册为 Spring Bean，{@code CacheAutoConfiguration} 会自动收集所有
 * {@code CacheDriverFactory} Bean 并注册到 {@link CacheManager}，无需手动调用注册方法。
 *
 * <pre>
 * &#64;Component
 * public class MyCacheDriverFactory implements CacheDriverFactory {
 *     &#64;Override
 *     public boolean support(String driver) {
 *         return "my-driver".equalsIgnoreCase(driver);
 *     }
 *
 *     &#64;Override
 *     public CacheDriver create(Map&lt;String, Object&gt; config) {
 *         return new MyCacheDriver(config.get("host"));
 *     }
 * }
 * </pre>
 *
 * <h3>配置式注册（对齐 Laravel {@code config/cache.php} 的 stores 数组）</h3>
 * <pre>
 * jaravel:
 *   cache:
 *     default-store: array
 *     stores:
 *       array:
 *         driver: array
 *       file:
 *         driver: file
 *         dir: /tmp/cache
 *       redis:
 *         driver: redis
 *         connection: cache
 * </pre>
 * CacheManager 启动时读取 stores 配置，按需创建 driver 和 store。
 * 未配置 stores 时只创建 default-store 对应的默认 store。
 */
public interface CacheDriverFactory {

    /**
     * 判断本工厂是否支持指定的 driver 名称。
     *
     * @param driver 驱动名称（如 {@code "array"}、{@code "file"}、{@code "redis"}），不区分大小写
     * @return 支持返回 {@code true}，不支持返回 {@code false}
     */
    boolean support(String driver);

    /**
     * 创建缓存驱动实例。
     *
     * @param config 配置参数（来自 {@code CacheProperties} 的 stores 配置段），
     *               含 {@code dir}（file 驱动目录）、{@code table}（database 驱动表名）、
     *               {@code connection}（redis 连接名）等，由具体驱动解释
     * @return 驱动实例
     */
    CacheDriver create(Map<String, Object> config);
}
