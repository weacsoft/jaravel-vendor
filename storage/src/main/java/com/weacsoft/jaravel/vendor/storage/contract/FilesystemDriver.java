package com.weacsoft.jaravel.vendor.storage.contract;

import java.util.Map;

/**
 * 文件系统驱动契约，采用工厂模式 + support 方法匹配，对齐 auth 模块
 * {@link com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver} 的兄弟设计
 * （{@code AuthGuardDriver} / {@code UserProviderDriver}）。
 * <p>
 * 每个驱动实现自行声明 {@link #support(String)} 方法，当传入的 driver 名称匹配时返回 {@code true}，
 * 由 {@link com.weacsoft.jaravel.vendor.storage.StorageManager} 在创建磁盘时遍历所有已注册的驱动，
 * 找到第一个匹配的驱动并调用 {@link #create} 创建 {@link Filesystem} 实例。
 *
 * <h3>内置驱动</h3>
 * <ul>
 *   <li>{@code local} — 本地文件系统，由 storage 模块的 {@code LocalFilesystemDriver} 提供</li>
 * </ul>
 * Laravel 中的 {@code public} 磁盘在本模块中同样使用 {@code local} 驱动，
 * 只需在配置中额外指定 {@code url} 前缀与 {@code visibility: public} 即可。
 *
 * <h3>扩展驱动</h3>
 * 第三方模块只需实现本接口并注册为 Spring Bean，{@code StorageAutoConfiguration} 会自动收集所有
 * {@code FilesystemDriver} Bean 并注册到 {@link com.weacsoft.jaravel.vendor.storage.StorageManager}，
 * 无需手动调用注册方法。
 *
 * <pre>
 * &#64;Component
 * public class S3FilesystemDriver implements FilesystemDriver {
 *     &#64;Override
 *     public boolean support(String driver) {
 *         return "s3".equalsIgnoreCase(driver);
 *     }
 *
 *     &#64;Override
 *     public Filesystem create(String name, Map&lt;String, Object&gt; config) {
 *         return new S3Filesystem(name, (String) config.get("bucket"), (String) config.get("region"));
 *     }
 * }
 * </pre>
 *
 * <h3>配置式注册（对齐 Laravel {@code config/filesystems.php} 的 disks 数组）</h3>
 * <pre>
 * jaravel:
 *   storage:
 *     default-disk: local
 *     disks:
 *       local:
 *         driver: local
 *         root: storage/app
 *       public:
 *         driver: local
 *         root: storage/app/public
 *         url: /storage
 *         visibility: public
 * </pre>
 *
 * <h3>注解声明式注册（推荐）</h3>
 * 若需要完全控制磁盘的创建过程，可直接在 Config 类中用
 * {@link com.weacsoft.jaravel.vendor.storage.RegisterDisk @RegisterDisk} 注解声明：
 * <pre>
 * &#64;RegisterDisk("uploads")
 * public DiskDefinition uploadsDisk() {
 *     return DiskDefinition.local("/data/uploads").url("/files");
 * }
 * </pre>
 * 注解声明优先于配置式注册（同名时覆盖），且不会注册为 Spring Bean，避免 bean name 冲突。
 */
public interface FilesystemDriver {

    /**
     * 判断本驱动是否支持指定的 driver 名称。
     *
     * @param driver 驱动名称（如 {@code "local"}），不区分大小写
     * @return 支持返回 {@code true}，不支持返回 {@code false}
     */
    boolean support(String driver);

    /**
     * 创建文件系统（磁盘）实例。
     *
     * @param name   磁盘名称（用于 {@code Storage.disk(name)} 解析，也会回填到
     *               {@link Filesystem#name()}）
     * @param config 配置参数（来自 {@code StorageProperties} 的 disks 配置段或
     *               {@link DiskDefinition#config()}），
     *               常见键：{@code root}、{@code url}、{@code visibility} 等
     * @return 文件系统实例，必须线程安全
     */
    Filesystem create(String name, Map<String, Object> config);
}
