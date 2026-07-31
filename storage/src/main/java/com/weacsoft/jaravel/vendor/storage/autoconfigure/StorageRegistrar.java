package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import com.weacsoft.jaravel.vendor.storage.RegisterDisk;
import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 存储注册器：在所有单例 Bean 初始化完成后，扫描容器中标注了
 * {@link RegisterDisk @RegisterDisk} 的方法，调用并将返回的磁盘注册到
 * {@link StorageManager}。
 * <p>
 * 设计与 auth 模块的 {@code AuthRegistrar} 完全一致：
 * 实现 {@link SmartInitializingSingleton}，在 {@code afterSingletonsInstantiated()}
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
public class StorageRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(StorageRegistrar.class);

    private final ApplicationContext context;
    private final StorageManager manager;
    private final StorageProperties properties;

    public StorageRegistrar(ApplicationContext context, StorageManager manager, StorageProperties properties) {
        this.context = context;
        this.manager = manager;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        registerDrivers();
        registerConfiguredDisks();
        scanAnnotatedDisks();
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
        Map<String, FilesystemDriver> beans = context.getBeansOfType(FilesystemDriver.class);
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
            manager.registerDisk(name, DiskDefinition.of(config.getDriver(), config.toConfig()));
            log.debug("按配置注册磁盘: {} (driver={})", name, config.getDriver());
        });
        if (properties.getDefaultDisk() != null && !properties.getDefaultDisk().isBlank()) {
            manager.setDefaultDisk(properties.getDefaultDisk());
        }
    }

    /**
     * 扫描 {@link RegisterDisk @RegisterDisk} 注解方法并注册磁盘。
     */
    private void scanAnnotatedDisks() {
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = resolveBeanQuietly(beanName);
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                RegisterDisk annotation =
                        AnnotatedElementUtils.findMergedAnnotation(method, RegisterDisk.class);
                if (annotation != null) {
                    invokeAndRegister(bean, method, annotation);
                }
            }
        }
    }

    /**
     * 安全获取 Bean，忽略懒加载失败/作用域不匹配等异常，避免影响启动。
     */
    private Object resolveBeanQuietly(String beanName) {
        try {
            return context.getBean(beanName);
        } catch (Exception e) {
            log.trace("跳过无法解析的 Bean: {}", beanName);
            return null;
        }
    }

    /**
     * 调用注解方法（自动注入参数）并注册返回的磁盘。
     */
    private void invokeAndRegister(Object bean, Method method, RegisterDisk annotation) {
        String diskName = annotation.value();
        try {
            method.setAccessible(true);
            Object result = method.invoke(bean, resolveArguments(method));
            if (result == null) {
                log.warn("@RegisterDisk 方法 {}#{} 返回 null，已跳过磁盘 [{}]",
                        method.getDeclaringClass().getSimpleName(), method.getName(), diskName);
                return;
            }
            if (result instanceof Filesystem filesystem) {
                manager.registerDisk(diskName, filesystem);
            } else if (result instanceof DiskDefinition definition) {
                manager.registerDisk(diskName, definition);
            } else {
                throw new StorageException("@RegisterDisk 方法 "
                        + method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                        + " 的返回类型必须是 DiskDefinition 或 Filesystem，实际为 "
                        + result.getClass().getName());
            }
            if (annotation.defaultDisk()) {
                manager.setDefaultDisk(diskName);
            }
            log.debug("按 @RegisterDisk 注册磁盘: {}{}", diskName,
                    annotation.defaultDisk() ? "（默认磁盘）" : "");
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("调用 @RegisterDisk 方法失败: "
                    + method.getDeclaringClass().getSimpleName() + "#" + method.getName(), e);
        }
    }

    /**
     * 按类型从容器解析方法参数，行为与 {@code @Bean} 方法参数注入一致。
     */
    private Object[] resolveArguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = context.getBean(types[i]);
        }
        return args;
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
