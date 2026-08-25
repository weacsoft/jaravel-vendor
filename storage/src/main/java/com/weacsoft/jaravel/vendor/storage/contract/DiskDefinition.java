package com.weacsoft.jaravel.vendor.storage.contract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 磁盘定义，用于通过 {@link com.weacsoft.jaravel.vendor.storage.RegisterDisk @RegisterDisk}
 * 注解声明式注册磁盘。
 * <p>
 * 对齐 Laravel {@code config/filesystems.php} 的 disks 数组。
 * 标注在 {@code @Configuration} 类的方法上，方法返回本记录，
 * {@code com.weacsoft.jaravel.vendor.springboot.storage.StorageRegistrar} 扫描注解并注册到
 * {@link com.weacsoft.jaravel.vendor.storage.StorageManager}。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class StorageConfig {
 *
 *     &#64;RegisterDisk("local")
 *     public DiskDefinition localDisk() {
 *         return DiskDefinition.local("storage/app");
 *     }
 *
 *     &#64;RegisterDisk(value = "public", defaultDisk = true)
 *     public DiskDefinition publicDisk() {
 *         return DiskDefinition.local("storage/app/public")
 *                 .url("/storage")
 *                 .visibility(Visibility.PUBLIC);
 *     }
 *
 *     &#64;RegisterDisk("s3")
 *     public DiskDefinition s3Disk(AwsProperties aws) {
 *         return DiskDefinition.of("s3")
 *                 .with("bucket", aws.getBucket())
 *                 .with("region", aws.getRegion());
 *     }
 * }
 * </pre>
 *
 * <p>
 * 也可通过配置文件注册磁盘（{@code jaravel.storage.disks}），两种方式可共存，
 * 注解声明式注册优先于配置式注册（同名时覆盖）。
 *
 * <p>
 * 本记录是<b>不可变</b>的，所有 {@code with*} 风格方法均返回新实例，可安全链式调用。
 *
 * @param driver 驱动名称（如 {@code "local"}、{@code "s3"}）
 * @param config 驱动配置（由具体驱动解释，常见键 {@code root}/{@code url}/{@code visibility}）
 */
public record DiskDefinition(String driver, Map<String, Object> config) {

    /** 配置键：本地根目录 */
    public static final String ROOT = "root";
    /** 配置键：公开访问 URL 前缀 */
    public static final String URL = "url";
    /** 配置键：默认可见性 */
    public static final String VISIBILITY = "visibility";

    /**
     * 紧凑构造器：对配置 Map 做防御性拷贝，保证不可变。
     */
    public DiskDefinition {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    /**
     * 创建指定驱动的磁盘定义（无额外配置）。
     *
     * @param driver 驱动名称
     * @return 磁盘定义
     */
    public static DiskDefinition of(String driver) {
        return new DiskDefinition(driver, Map.of());
    }

    /**
     * 创建指定驱动的磁盘定义（带配置）。
     *
     * @param driver 驱动名称
     * @param config 驱动配置
     * @return 磁盘定义
     */
    public static DiskDefinition of(String driver, Map<String, Object> config) {
        return new DiskDefinition(driver, config);
    }

    /**
     * 创建 {@code local} 驱动的磁盘定义。
     *
     * @param root 根目录（相对运行目录或绝对路径）
     * @return 磁盘定义
     */
    public static DiskDefinition local(String root) {
        return new DiskDefinition("local", Map.of(ROOT, root));
    }

    /**
     * 追加/覆盖一项配置，返回新实例。
     *
     * @param key   配置键
     * @param value 配置值
     * @return 新的磁盘定义
     */
    public DiskDefinition with(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(config);
        merged.put(key, value);
        return new DiskDefinition(driver, merged);
    }

    /**
     * 设置根目录，返回新实例。
     *
     * @param root 根目录
     * @return 新的磁盘定义
     */
    public DiskDefinition root(String root) {
        return with(ROOT, root);
    }

    /**
     * 设置公开访问 URL 前缀，返回新实例。
     *
     * @param url URL 前缀（如 {@code "/storage"} 或 {@code "https://cdn.example.com"}）
     * @return 新的磁盘定义
     */
    public DiskDefinition url(String url) {
        return with(URL, url);
    }

    /**
     * 设置默认可见性，返回新实例。
     *
     * @param visibility 可见性
     * @return 新的磁盘定义
     */
    public DiskDefinition visibility(Visibility visibility) {
        return with(VISIBILITY, visibility.value());
    }
}
