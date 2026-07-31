package com.weacsoft.jaravel.vendor.storage;

import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 存储管理器，对齐 Laravel {@code FilesystemManager}。
 * <p>
 * 维护多个磁盘（disk），按名称解析 {@link Filesystem} 实例。
 * 采用工厂模式 + support 方法匹配（对齐 auth 模块的 {@code AuthManager} 设计）：
 * {@link FilesystemDriver} 驱动自行声明 {@code support(String)}，
 * StorageManager 在创建磁盘时遍历所有已注册驱动，找到第一个匹配的并调用 {@code create}。
 * 第三方模块只需将驱动实现注册为 Spring Bean，{@code StorageAutoConfiguration} 会自动收集并注册。
 *
 * <h3>三种注册方式（可共存，注解声明优先）</h3>
 * <ol>
 *   <li><b>注解声明式</b>（推荐）：在 Config 类中用
 *       {@link RegisterDisk @RegisterDisk} 声明 {@link DiskDefinition} 或 {@link Filesystem}。
 *       {@link com.weacsoft.jaravel.vendor.storage.autoconfigure.StorageRegistrar} 扫描注解并注册</li>
 *   <li><b>配置式</b>：通过 {@code jaravel.storage.disks} 配置，由工厂驱动按配置自动创建</li>
 *   <li><b>手动调用</b>：直接调用 {@link #registerDisk}（向后兼容 / 测试友好）</li>
 * </ol>
 *
 * <h3>磁盘实例缓存</h3>
 * 与 auth 的 guard 不同，{@link Filesystem} 实例<b>无请求级可变状态</b>（不缓存用户、token 等），
 * 因此磁盘实例是<b>进程级共享</b>的（{@link ConcurrentHashMap} 缓存），
 * 不使用 ThreadLocal，也无需请求结束时清理。这要求所有 {@code Filesystem} 实现必须线程安全。
 *
 * <h3>延迟创建</h3>
 * 配置式/注解式声明的磁盘定义在注册时<b>不会</b>立即创建 {@link Filesystem} 实例，
 * 而是在首次 {@link #disk(String)} 解析时通过驱动创建并缓存。
 * 这样可以避免启动时因某个磁盘（如远程对象存储）不可用而导致整个应用启动失败，
 * 同时也保证驱动 Bean 的注册顺序不影响磁盘定义的注册顺序。
 *
 * <h3>线程安全说明</h3>
 * <ul>
 *   <li><b>注册表（definitions / instances / drivers）</b>：使用 {@link ConcurrentHashMap} 和
 *       {@link CopyOnWriteArrayList}，支持并发读写</li>
 *   <li><b>defaultDisk</b>：启动阶段设置后不再变更，使用 {@code volatile} 保证可见性</li>
 * </ul>
 */
public class StorageManager {

    /** 磁盘定义：name -> DiskDefinition，进程级共享，启动后只读 */
    private final Map<String, DiskDefinition> definitions = new ConcurrentHashMap<>();
    /** 已创建的磁盘实例（延迟创建后缓存）：name -> Filesystem */
    private final Map<String, Filesystem> instances = new ConcurrentHashMap<>();
    /** 文件系统驱动列表（工厂模式），进程级共享，启动后只读 */
    private final List<FilesystemDriver> drivers = new CopyOnWriteArrayList<>();

    private volatile String defaultDisk = "local";

    /**
     * 设置默认磁盘名。
     *
     * @param name 磁盘名
     */
    public void setDefaultDisk(String name) {
        this.defaultDisk = name;
    }

    /**
     * 获取默认磁盘名。
     *
     * @return 磁盘名
     */
    public String getDefaultDisk() {
        return defaultDisk;
    }

    // ==================== 注册 ====================

    /**
     * 直接注册已构建好的磁盘实例（注解式返回 {@link Filesystem} 时使用，或测试注入桩实现）。
     * <p>
     * 会同时清除同名的延迟定义，保证本实例优先生效。
     *
     * @param name       磁盘名称
     * @param filesystem 磁盘实例
     */
    public void registerDisk(String name, Filesystem filesystem) {
        definitions.remove(name);
        instances.put(name, filesystem);
    }

    /**
     * 注册磁盘定义（延迟创建），用于注解式返回 {@link DiskDefinition} 的场景。
     *
     * @param name       磁盘名称
     * @param definition 磁盘定义
     */
    public void registerDisk(String name, DiskDefinition definition) {
        definitions.put(name, definition);
        instances.remove(name); // 覆盖旧实例，下次解析时按新定义重建
    }

    /**
     * 注册磁盘定义（延迟创建），用于配置式注册。
     *
     * @param name   磁盘名称
     * @param driver 驱动名称
     * @param config 驱动配置
     */
    public void registerDisk(String name, String driver, Map<String, Object> config) {
        registerDisk(name, DiskDefinition.of(driver, config));
    }

    /**
     * 注册文件系统驱动（工厂模式）。
     * <p>
     * 通常由 {@code StorageAutoConfiguration} 在启动时自动收集所有 {@link FilesystemDriver} Bean
     * 并注册，业务方无需手动调用。
     *
     * @param driver 驱动实例
     */
    public void registerDriver(FilesystemDriver driver) {
        drivers.add(driver);
    }

    // ==================== 解析 ====================

    /**
     * 获取默认磁盘。
     *
     * @return 磁盘实例
     * @throws StorageException 默认磁盘未注册
     */
    public Filesystem disk() {
        return disk(defaultDisk);
    }

    /**
     * 按名称获取磁盘（首次解析时创建并缓存）。
     *
     * @param name 磁盘名称，{@code null} 时使用默认磁盘
     * @return 磁盘实例
     * @throws StorageException 磁盘未注册或驱动未知
     */
    public Filesystem disk(String name) {
        String diskName = (name == null || name.isEmpty()) ? defaultDisk : name;
        Filesystem cached = instances.get(diskName);
        if (cached != null) {
            return cached;
        }
        return instances.computeIfAbsent(diskName, this::createDisk);
    }

    /**
     * 按定义创建磁盘实例，遍历驱动列表找到第一个匹配的驱动。
     */
    private Filesystem createDisk(String name) {
        DiskDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new StorageException("未注册的磁盘: " + name
                    + "，请通过 jaravel.storage.disks 配置或 @RegisterDisk 注解注册");
        }
        for (FilesystemDriver driver : drivers) {
            if (driver.support(definition.driver())) {
                Filesystem fs = driver.create(name, definition.config());
                if (fs == null) {
                    throw new StorageException(
                            "驱动 " + definition.driver() + " 创建磁盘 " + name + " 返回 null");
                }
                return fs;
            }
        }
        throw new StorageException("未知 filesystem driver: " + definition.driver()
                + "，请引入对应插件（如 storage-s3 模块）");
    }

    // ==================== 查询 ====================

    /**
     * 检查是否注册了指定名称的磁盘。
     *
     * @param name 磁盘名称
     * @return 已注册返回 true
     */
    public boolean hasDisk(String name) {
        return name != null && (definitions.containsKey(name) || instances.containsKey(name));
    }

    /**
     * 检查是否注册了任何磁盘。
     *
     * @return 已注册至少一个磁盘返回 true
     */
    public boolean hasDisks() {
        return !definitions.isEmpty() || !instances.isEmpty();
    }

    /**
     * 获取所有已注册的磁盘名称（含尚未实例化的定义）。
     *
     * @return 磁盘名称集合（不可变）
     */
    public Set<String> diskNames() {
        Set<String> names = ConcurrentHashMap.newKeySet();
        names.addAll(definitions.keySet());
        names.addAll(instances.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * 清空所有磁盘实例缓存（配置热更新或测试用），保留定义与驱动。
     */
    public void flushInstances() {
        instances.clear();
    }
}
