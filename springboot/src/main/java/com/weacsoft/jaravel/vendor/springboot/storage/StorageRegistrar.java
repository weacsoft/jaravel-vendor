package com.weacsoft.jaravel.vendor.springboot.storage;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import com.weacsoft.jaravel.vendor.storage.RegisterDisk;
import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 存储注册器：在所有单例 Bean 初始化完成后，扫描容器中标注了
 * {@link RegisterDisk @RegisterDisk} 的方法，调用并将返回的磁盘注册到
 * {@link StorageManager}。
 * <p>
 * 设计与 auth 模块的 {@code AuthRegistrar} 完全一致：
 * 实现 {@code SmartInitializingSingleton}，在 {@code afterSingletonsInstantiated()}
 * 阶段执行扫描，此时所有 Bean 已就绪，可安全地作为方法参数注入。
 *
 * <h3>注册顺序（后者覆盖前者）</h3>
 * <ol>
 *   <li>收集所有 {@link FilesystemDriver} Bean 并注册为驱动</li>
 *   <li>按 {@code jaravel.storage.disks} 配置注册磁盘定义</li>
 *   <li>扫描 {@link RegisterDisk @RegisterDisk} 注解注册磁盘（<b>覆盖</b>同名配置式磁盘）</li>
 *   <li>若最终一个磁盘都没有，注册兜底的 {@code local} 磁盘（{@code storage/app}）</li>
 * </ol>
 *
 * <h3>方法返回值支持</h3>
 * <ul>
 *   <li>{@link DiskDefinition} — 交由驱动工厂延迟创建（推荐）</li>
 *   <li>{@link Filesystem} — 直接注册实例，完全自定义</li>
 * </ul>
 */
public class StorageRegistrar extends AnnotationDrivenRegistrar<RegisterDisk> {

    private final StorageManager manager;
    private final StorageProperties properties;

    public StorageRegistrar(StorageManager manager, StorageProperties properties) {
        super(RegisterDisk.class);
        this.manager = manager;
        this.properties = properties;
    }

    /**
     * 扫描前：注册驱动与配置式磁盘，使注解式注册可覆盖同名配置。
     */
    @Override
    protected void beforeScan() {
        registerDrivers();
        registerConfiguredDisks();
    }

    /**
     * 扫描后：兜底注册 local 磁盘，并输出就绪日志。
     */
    @Override
    protected void afterScan() {
        registerFallbackDisk();

        if (log.isInfoEnabled()) {
            log.info("Jaravel Storage 就绪：默认磁盘=[{}]，已注册磁盘={}",
                    manager.getDefaultDisk(), manager.diskNames());
        }
    }

    /**
     * 收集容器中所有 {@link FilesystemDriver} Bean 并注册。
     */
    private void registerDrivers() {
        Map<String, FilesystemDriver> beans = lookup().beansOfType(FilesystemDriver.class);
        for (Map.Entry<String, FilesystemDriver> entry : beans.entrySet()) {
            manager.registerDriver(entry.getValue());
            log.debug("注册 filesystem driver: {}", entry.getKey());
        }
    }

    /**
     * 按配置文件注册磁盘定义。
     */
    private void registerConfiguredDisks() {
        properties.getDisks().forEach((name, config) -> {
            // 兜底：写了 disks 但没写 driver，使用最基础的 local 磁盘保证功能可用
            String driver = (config.getDriver() == null || config.getDriver().isBlank())
                    ? "local" : config.getDriver();
            manager.registerDisk(name, DiskDefinition.of(driver, config.toConfig()));
            log.debug("按配置注册磁盘: {} (driver={})", name, driver);
        });
        if (properties.getDefaultDisk() != null && !properties.getDefaultDisk().isBlank()) {
            manager.setDefaultDisk(properties.getDefaultDisk());
        }
    }

    /**
     * 登记 {@link RegisterDisk @RegisterDisk} 方法返回的磁盘。
     * <p>
     * 支持两种返回类型：{@link DiskDefinition}（交由驱动工厂延迟创建）
     * 与 {@link Filesystem}（直接注册实例）。
     */
    @Override
    protected void register(Object result, Method method, RegisterDisk annotation) {
        String diskName = annotation.value();
        if (result instanceof Filesystem filesystem) {
            manager.registerDisk(diskName, filesystem);
        } else if (result instanceof DiskDefinition definition) {
            manager.registerDisk(diskName, definition);
        } else {
            throw new StorageException("@RegisterDisk 方法 " + describe(method)
                    + " 的返回类型必须是 DiskDefinition 或 Filesystem，实际为 "
                    + result.getClass().getName());
        }
        if (annotation.defaultDisk()) {
            manager.setDefaultDisk(diskName);
        }
        log.debug("按 @RegisterDisk 注册磁盘: {}{}", diskName,
                annotation.defaultDisk() ? "（默认磁盘）" : "");
    }

    /**
     * 若一个磁盘都没注册，兜底注册默认 local 磁盘，保证开箱即用。
     */
    private void registerFallbackDisk() {
        if (manager.hasDisks()) {
            return;
        }
        String name = manager.getDefaultDisk() == null || manager.getDefaultDisk().isBlank()
                ? "local" : manager.getDefaultDisk();
        manager.registerDisk(name, DiskDefinition.local(
                com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver.DEFAULT_ROOT));
        manager.setDefaultDisk(name);
        log.debug("未配置任何磁盘，已注册兜底 local 磁盘 [{}]", name);
    }
}
