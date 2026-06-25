package com.weacsoft.jaravel.vendor.plugin.jar.manager;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.classloader.PluginClassLoader;
import com.weacsoft.jaravel.vendor.plugin.jar.classloader.SharedClassLoader;
import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.scanner.SharedDependencyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 热插件管理器（核心）。
 * <p>
 * 负责插件的完整生命周期管理：注册、启用、禁用、卸载、共享 JAR 热更新、
 * 路由动态注册/注销、跨插件服务获取、持久化恢复。
 * <p>
 * <h3>三级 ClassLoader 隔离</h3>
 * <ul>
 *   <li><b>主程序 ClassLoader</b>：加载 plugin-jar-core 自身（含 Application 代理类）。</li>
 *   <li><b>共享 ClassLoader</b>（{@link SharedClassLoader}）：加载共享 JAR（共享 API、
 *       jaravel vendor JAR、core JAR）与插件中的 {@code @SharedService} 类。</li>
 *   <li><b>插件 ClassLoader</b>（{@link PluginClassLoader}）：每插件一个，加载插件私有类。</li>
 * </ul>
 * <p>
 * <h3>线程安全</h3>
 * 使用 {@link ReadWriteLock} 串行化插件状态变更（注册/启用/禁用/卸载），
 * 读操作（getPlugin/getAllPlugins/getServiceFromPlugin）使用读锁并发。
 * 插件注册表使用 {@link ConcurrentHashMap}。
 * <p>
 * <h3>持久化</h3>
 * 通过 {@link MetadataPersistence} 抽象持久化，默认 JSON 文件实现。
 * 仅磁盘持久化的插件（{@code persisted=true}）会被持久化与自动恢复；
 * 内存插件（{@code persisted=false}）不参与持久化，重启后丢失。
 * <p>
 * <h3>Jaravel 集成</h3>
 * 通过 {@link PluginIntegration} 抽象 jaravel 集成，默认空操作实现。
 * jaravel starter 可提供具体实现，向插件注入 jaravel 服务。
 * <p>
 * 实现 {@link Application.HotPluginManagerRef}，通过 {@link Application#setManagerRef}
 * 注入自身，使插件可通过 {@link Application#getService} 跨插件获取服务。
 */
public class HotPluginManager implements Application.HotPluginManagerRef {

    private static final Logger log = LoggerFactory.getLogger(HotPluginManager.class);

    /** 默认共享包前缀：插件注解包必须由共享 ClassLoader 加载 */
    private static final Set<String> DEFAULT_SHARED_PREFIXES = Set.of(
            "com.weacsoft.jaravel.vendor.plugin.jar.annotation."
    );

    private final Path pluginsDir;
    private final PluginBeanRegistrar beanRegistrar;
    private final PluginRouteRegistrar routeRegistrar;
    private final MetadataPersistence persistence;
    private final PluginIntegration integration;
    private final boolean autoRegister;

    /** 插件注册表：pluginId -> PluginInfo */
    private final Map<String, PluginInfo> plugins = new ConcurrentHashMap<>();
    /** 插件 ClassLoader 注册表：pluginId -> PluginClassLoader */
    private final Map<String, PluginClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    /** 共享 ClassLoader，volatile 保证可见性 */
    private volatile SharedClassLoader sharedClassLoader;
    /** 共享包前缀（含默认 + integration 提供） */
    private final Set<String> sharedPackagePrefixes;
    /** 核心 JAR 路径（用于使注解类对插件可见） */
    private volatile Path coreJarPath;

    /** 读写锁：写操作串行化插件状态变更，读操作并发 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造热插件管理器。
     *
     * @param pluginsDir     插件目录
     * @param beanRegistrar  Bean 注册器
     * @param routeRegistrar 路由注册器
     * @param persistence    元数据持久化
     * @param integration    jaravel 集成
     * @param autoRegister   是否自动注册插件路由（true=自动注册@PluginMapping，false=手动注册）
     */
    public HotPluginManager(Path pluginsDir, PluginBeanRegistrar beanRegistrar,
                            PluginRouteRegistrar routeRegistrar,
                            MetadataPersistence persistence, PluginIntegration integration,
                            boolean autoRegister) {
        this.pluginsDir = pluginsDir;
        this.beanRegistrar = beanRegistrar;
        this.routeRegistrar = routeRegistrar;
        this.persistence = persistence;
        this.integration = integration;
        this.autoRegister = autoRegister;
        // 合并默认共享包前缀与 integration 提供的前缀
        Set<String> prefixes = new HashSet<>(DEFAULT_SHARED_PREFIXES);
        Set<String> integrationPrefixes = integration.getSharedPackagePrefixes();
        if (integrationPrefixes != null) {
            prefixes.addAll(integrationPrefixes);
        }
        this.sharedPackagePrefixes = prefixes;
        // 注入自身到 Application 代理
        Application.setManagerRef(this);
        // 确保插件目录存在
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            log.warn("创建插件目录失败: {}", pluginsDir, e);
        }
    }

    /**
     * 初始化共享 ClassLoader。
     *
     * @param sharedJar 共享 JAR 路径
     * @param version   版本号
     */
    public void initSharedClassLoader(Path sharedJar, String version) {
        lock.writeLock().lock();
        try {
            List<Path> jars = new ArrayList<>();
            if (sharedJar != null) {
                jars.add(sharedJar);
            }
            // 加入 core JAR（使注解类对插件可见）
            if (coreJarPath != null) {
                jars.add(coreJarPath);
            }
            // 加入 integration 提供的额外共享 JAR
            List<Path> additional = integration.getAdditionalSharedJars();
            if (additional != null) {
                jars.addAll(additional);
            }
            SharedClassLoader old = this.sharedClassLoader;
            this.sharedClassLoader = SharedClassLoader.create(jars, version);
            log.info("初始化共享 ClassLoader: version={}, jars={}", version, jars);
            if (old != null) {
                old.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 设置核心 JAR 路径（用于使注解类对插件可见）。
     *
     * @param coreJarPath 核心 JAR 路径
     */
    public void setCoreJarPath(Path coreJarPath) {
        this.coreJarPath = coreJarPath;
    }

    /**
     * 返回插件目录。
     *
     * @return 插件目录
     */
    public Path getPluginsDir() {
        return pluginsDir;
    }

    /**
     * 返回路由注册器。
     *
     * @return 路由注册器
     */
    public PluginRouteRegistrar getRouteRegistrar() {
        return routeRegistrar;
    }

    /**
     * 从磁盘路径注册插件。
     *
     * @param jarFile  JAR 文件路径
     * @param pluginId 插件 ID（为空时使用文件名）
     * @param persist  是否磁盘持久化：true 复制 JAR 到插件目录，可自动恢复；false 仅记录路径
     * @return 插件 ID
     */
    public String registerPluginFromPath(Path jarFile, String pluginId, boolean persist) {
        lock.writeLock().lock();
        try {
            if (pluginId == null || pluginId.isEmpty()) {
                pluginId = derivePluginId(jarFile);
            }
            Path targetJar = jarFile;
            if (persist) {
                // 复制 JAR 到插件目录
                Path pluginDir = pluginsDir.resolve(pluginId);
                Files.createDirectories(pluginDir);
                targetJar = pluginDir.resolve(jarFile.getFileName());
                Files.copy(jarFile, targetJar, StandardCopyOption.REPLACE_EXISTING);
            }
            PluginInfo info = new PluginInfo(pluginId, "0.1.0", targetJar.toString());
            info.setPersisted(persist);
            info.setState(PluginInfo.State.UPLOADED);
            plugins.put(pluginId, info);
            log.info("注册插件: id={}, jar={}, persist={}", pluginId, targetJar, persist);
            return pluginId;
        } catch (IOException e) {
            log.error("注册插件失败: {}", jarFile, e);
            throw new RuntimeException("注册插件失败: " + jarFile, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从字节数组注册插件（内存插件，不持久化）。
     * <p>
     * 将字节数组写入临时文件后加载，{@code persisted=false}，重启后丢失。
     *
     * @param jarBytes JAR 字节数组
     * @param pluginId 插件 ID（为空时自动生成）
     * @return 插件 ID
     */
    public String registerPluginFromBytes(byte[] jarBytes, String pluginId) {
        lock.writeLock().lock();
        try {
            if (pluginId == null || pluginId.isEmpty()) {
                pluginId = "memory-" + System.currentTimeMillis();
            }
            // 写入临时文件
            Path tempFile = Files.createTempFile("plugin-" + pluginId + "-", ".jar");
            Files.write(tempFile, jarBytes);
            tempFile.toFile().deleteOnExit();
            PluginInfo info = new PluginInfo(pluginId, "0.1.0", tempFile.toString());
            info.setPersisted(false);
            info.setState(PluginInfo.State.UPLOADED);
            plugins.put(pluginId, info);
            log.info("注册内存插件: id={}, tempJar={}", pluginId, tempFile);
            return pluginId;
        } catch (IOException e) {
            log.error("注册内存插件失败: {}", pluginId, e);
            throw new RuntimeException("注册内存插件失败: " + pluginId, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 启用插件。
     * <p>
     * 流程：扫描 JAR -> 创建插件 ClassLoader -> 注册 Bean -> 注册路由 -> 持久化。
     *
     * @param pluginId 插件 ID
     * @return 启用成功返回 true
     */
    public boolean enablePlugin(String pluginId) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null) {
                log.warn("插件不存在: {}", pluginId);
                return false;
            }
            if (info.getState() == PluginInfo.State.ENABLED) {
                return true;
            }
            try {
                Path jarPath = Paths.get(info.getJarPath());
                // 扫描 JAR
                SharedDependencyScanner.ScanResult scanResult =
                        SharedDependencyScanner.scan(jarPath, sharedPackagePrefixes);
                info.setComponentClasses(scanResult.getComponentClasses());
                info.setSharedClassDependencies(scanResult.getSharedClassDependencies());
                Set<RouteInfo> autoRoutes = scanResult.toRouteInfos();  // from @PluginMapping
                Set<RouteInfo> availableRoutes = scanResult.toAvailableRouteInfos();  // from @PluginRoute

                // 创建插件 ClassLoader
                SharedClassLoader shared = this.sharedClassLoader;
                if (shared == null) {
                    throw new IllegalStateException("共享 ClassLoader 未初始化，请先调用 initSharedClassLoader");
                }
                PluginClassLoader pluginClassLoader = new PluginClassLoader(
                        pluginId, jarPath, shared, sharedPackagePrefixes);
                pluginClassLoaders.put(pluginId, pluginClassLoader);

                // 注册组件 Bean
                Set<String> registeredBeans = new HashSet<>();
                for (String className : scanResult.getComponentClasses()) {
                    try {
                        Class<?> beanClass = pluginClassLoader.loadClass(className);
                        String beanName = deriveBeanName(beanClass, className);
                        if (beanRegistrar.registerBean(beanName, beanClass)) {
                            registeredBeans.add(beanName);
                        }
                    } catch (Exception e) {
                        log.error("加载插件组件类失败: {}", className, e);
                    }
                }
                info.setRegisteredBeanNames(registeredBeans);

                // 根据自动注册模式处理路由
                if (autoRegister) {
                    // Auto-register 模式：自动注册 @PluginMapping 路由
                    info.setRouteMappings(autoRoutes);
                    routeRegistrar.registerRoutes(pluginId, autoRoutes);
                    // @PluginRoute 路由可供手动注册
                    info.setAvailableRoutes(availableRoutes);
                } else {
                    // Manual 模式：不自动注册任何路由
                    info.setRouteMappings(new HashSet<>());
                    // @PluginMapping 与 @PluginRoute 均放入 availableRoutes
                    Set<RouteInfo> all = new HashSet<>(autoRoutes);
                    all.addAll(availableRoutes);
                    info.setAvailableRoutes(all);
                }

                // 调用 integration 回调
                ConfigurableApplicationContext context = beanRegistrar.getApplicationContext();
                integration.onPluginEnabled(pluginId, context);

                info.setState(PluginInfo.State.ENABLED);
                info.setErrorMessage(null);
                // 持久化（仅磁盘插件）
                persistence.save(info);
                log.info("启用插件成功: {}, beans={}, routes={}, available={}",
                        pluginId, registeredBeans.size(),
                        info.getRouteMappings().size(), info.getAvailableRoutes().size());
                return true;
            } catch (Exception e) {
                log.error("启用插件失败: {}", pluginId, e);
                info.setErrorMessage(e.getMessage());
                info.setState(PluginInfo.State.DISABLED);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 禁用插件。
     * <p>
     * 流程：注销路由 -> 注销 Bean -> 关闭 ClassLoader -> 持久化。
     *
     * @param pluginId 插件 ID
     * @return 禁用成功返回 true
     */
    public boolean disablePlugin(String pluginId) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null) {
                return false;
            }
            if (info.getState() == PluginInfo.State.DISABLED) {
                return true;
            }
            try {
                // 注销路由
                routeRegistrar.unregisterRoutes(pluginId);
                // 注销 Bean
                beanRegistrar.unregisterBeans(info.getRegisteredBeanNames());
                info.getRegisteredBeanNames().clear();
                // 关闭 ClassLoader
                PluginClassLoader cl = pluginClassLoaders.remove(pluginId);
                if (cl != null) {
                    cl.close();
                }
                // 调用 integration 回调
                integration.onPluginDisabled(pluginId);
                info.setState(PluginInfo.State.DISABLED);
                // 持久化（仅磁盘插件）
                persistence.save(info);
                log.info("禁用插件成功: {}", pluginId);
                return true;
            } catch (Exception e) {
                log.error("禁用插件失败: {}", pluginId, e);
                info.setErrorMessage(e.getMessage());
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 卸载插件（禁用 + 删除元数据）。
     *
     * @param pluginId 插件 ID
     * @return 卸载成功返回 true
     */
    public boolean uninstallPlugin(String pluginId) {
        lock.writeLock().lock();
        try {
            disablePlugin(pluginId);
            plugins.remove(pluginId);
            persistence.delete(pluginId);
            log.info("卸载插件: {}", pluginId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 热更新共享 JAR。
     * <p>
     * 创建新的共享 ClassLoader，并切换所有已启用插件的 ClassLoader 到新的共享 ClassLoader。
     *
     * @param newSharedJar 新的共享 JAR 路径
     * @param version      新版本号
     * @return 受影响的插件 ID 列表
     */
    public List<String> updateSharedJar(Path newSharedJar, String version) {
        lock.writeLock().lock();
        try {
            List<Path> jars = new ArrayList<>();
            if (newSharedJar != null) {
                jars.add(newSharedJar);
            }
            if (coreJarPath != null) {
                jars.add(coreJarPath);
            }
            List<Path> additional = integration.getAdditionalSharedJars();
            if (additional != null) {
                jars.addAll(additional);
            }
            SharedClassLoader old = this.sharedClassLoader;
            SharedClassLoader newShared = SharedClassLoader.create(jars, version);
            this.sharedClassLoader = newShared;
            // 切换所有插件 ClassLoader
            List<String> affected = new ArrayList<>();
            for (Map.Entry<String, PluginClassLoader> entry : pluginClassLoaders.entrySet()) {
                entry.getValue().updateSharedClassLoader(newShared);
                affected.add(entry.getKey());
            }
            log.info("热更新共享 JAR: version={}, affected={}", version, affected);
            if (old != null) {
                old.close();
            }
            return affected;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 注册单条路由。
     *
     * @param pluginId 插件 ID
     * @param route    路由信息
     * @return 注册成功返回 true
     */
    public boolean registerRoute(String pluginId, RouteInfo route) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null) {
                return false;
            }
            boolean ok = routeRegistrar.registerRoute(pluginId, route);
            if (ok) {
                info.getRouteMappings().add(route);
                persistence.save(info);
            }
            return ok;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 注销单条路由。
     *
     * @param pluginId   插件 ID
     * @param path       路径
     * @param httpMethod HTTP 方法名
     * @return 注销成功返回 true
     */
    public boolean unregisterRoute(String pluginId, String path, String httpMethod) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null) {
                return false;
            }
            routeRegistrar.getRouteHandler().unregisterRouteInfo(httpMethod, path);
            info.getRouteMappings().removeIf(r ->
                    r.getPath() != null && r.getPath().equals(path)
                            && r.getMethod() != null && r.getMethod().name().equalsIgnoreCase(httpMethod));
            persistence.save(info);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 为已注册的路由注册别名路径。
     * <p>
     * 查找已有路由（通过 existingPath + httpMethod），复制其 beanName/methodName，
     * 以 aliasPath 创建新的路由并注册。实现"一方法多路由"的手动注册。
     * <p>
     * 使用示例：
     * <pre>
     * // 已注册: GET /blog/list -> blogController.list()
     * manager.registerRouteAlias(pluginId, "/blog/list", "/a/list", "GET");
     * // 结果: GET /a/list -> blogController.list()（别名路由）
     * </pre>
     *
     * @param pluginId     插件 ID
     * @param existingPath 已注册的路由路径
     * @param aliasPath    别名路由路径
     * @param httpMethod   HTTP 方法名（如 "GET"、"POST"）
     * @return 注册成功返回 true，原路由不存在返回 false
     */
    public boolean registerRouteAlias(String pluginId, String existingPath,
                                       String aliasPath, String httpMethod) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null || info.getState() != PluginInfo.State.ENABLED) {
                return false;
            }
            boolean ok = routeRegistrar.registerRouteAlias(pluginId, existingPath, aliasPath, httpMethod);
            if (ok) {
                // 记录别名路由到 routeMappings
                HttpMethod method;
                try {
                    method = HttpMethod.valueOf(httpMethod.toUpperCase());
                } catch (IllegalArgumentException e) {
                    method = HttpMethod.GET;
                }
                RouteInfo aliasRoute = new RouteInfo(aliasPath, method, null, null, null);
                info.getRouteMappings().add(aliasRoute);
                persistence.save(info);
            }
            return ok;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 为已注册的路由注册别名路径（自动检测原路由的 HTTP 方法）。
     *
     * @param pluginId     插件 ID
     * @param existingPath 已注册的路由路径
     * @param aliasPath    别名路由路径
     * @return 注册成功返回 true
     */
    public boolean registerRouteAlias(String pluginId, String existingPath, String aliasPath) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null || info.getState() != PluginInfo.State.ENABLED) {
                return false;
            }
            boolean ok = routeRegistrar.registerRouteAlias(pluginId, existingPath, aliasPath);
            if (ok) {
                persistence.save(info);
            }
            return ok;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 返回指定插件的可注册路由（未自动注册的路由）。
     *
     * @param pluginId 插件 ID
     * @return 可注册路由集合的副本
     */
    public Set<RouteInfo> getAvailableRoutes(String pluginId) {
        lock.readLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            return info != null ? new HashSet<>(info.getAvailableRoutes()) : new HashSet<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 注册指定的可注册路由（manual-register 模式下使用）。
     * <p>
     * 从插件的 availableRoutes 中查找匹配的路由并注册，注册成功后从 availableRoutes 移除
     * 并加入 routeMappings。
     *
     * @param pluginId   插件 ID
     * @param path       路由路径
     * @param httpMethod HTTP 方法名
     * @return 注册成功返回 true，未找到匹配路由或插件未启用返回 false
     */
    public boolean registerAvailableRoute(String pluginId, String path, String httpMethod) {
        lock.writeLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null || info.getState() != PluginInfo.State.ENABLED) {
                return false;
            }

            RouteInfo matched = null;
            for (RouteInfo route : info.getAvailableRoutes()) {
                if (route.getPath() != null && route.getPath().equals(path)
                        && route.getMethod() != null
                        && route.getMethod().name().equalsIgnoreCase(httpMethod)) {
                    matched = route;
                    break;
                }
            }
            if (matched == null) {
                return false;
            }

            boolean ok = routeRegistrar.registerRoute(pluginId, matched);
            if (ok) {
                info.getAvailableRoutes().remove(matched);
                info.getRouteMappings().add(matched);
                persistence.save(info);
            }
            return ok;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从插件获取 Bean（实现 {@link Application.HotPluginManagerRef}）。
     *
     * @param pluginId 插件 ID
     * @param beanName Bean 名称
     * @return Bean 实例，不存在返回 {@code null}
     */
    @Override
    public Object getServiceFromPlugin(String pluginId, String beanName) {
        lock.readLock().lock();
        try {
            PluginInfo info = plugins.get(pluginId);
            if (info == null || info.getState() != PluginInfo.State.ENABLED) {
                return null;
            }
            if (!info.getRegisteredBeanNames().contains(beanName)) {
                return null;
            }
            try {
                return beanRegistrar.getApplicationContext().getBean(beanName);
            } catch (Exception e) {
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 返回所有插件信息。
     *
     * @return 插件信息列表
     */
    public List<PluginInfo> getAllPlugins() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(plugins.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 返回指定插件信息。
     *
     * @param pluginId 插件 ID
     * @return 插件信息，不存在返回 {@code null}
     */
    public PluginInfo getPlugin(String pluginId) {
        lock.readLock().lock();
        try {
            return plugins.get(pluginId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 加载已持久化的插件，并自动启用之前处于 ENABLED 状态的插件。
     * <p>
     * 由自动装配在启动时调用（若 {@code autoRestore=true}）。
     */
    public void loadPersistedPlugins() {
        lock.writeLock().lock();
        try {
            List<PluginInfo> persisted = persistence.loadAll();
            log.info("加载已持久化插件: {} 个", persisted.size());
            for (PluginInfo info : persisted) {
                plugins.put(info.getPluginId(), info);
                // 自动启用之前处于 ENABLED 状态的插件
                if (info.getState() == PluginInfo.State.ENABLED) {
                    // 先重置为 UPLOADED，再调用 enablePlugin
                    info.setState(PluginInfo.State.UPLOADED);
                    boolean ok = enablePlugin(info.getPluginId());
                    if (!ok) {
                        log.warn("自动恢复插件失败: {}", info.getPluginId());
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从 JAR 文件名推导插件 ID。
     */
    private String derivePluginId(Path jarFile) {
        String fileName = jarFile.getFileName().toString();
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    /**
     * 推导 Bean 名称。
     * <p>
     * 优先从 {@code @PluginComponent} 注解的 value 取，为空时使用类名首字母小写。
     * 由于此处已加载类，可直接通过反射读取注解。
     */
    private String deriveBeanName(Class<?> beanClass, String className) {
        try {
            com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent annotation =
                    beanClass.getAnnotation(com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent.class);
            if (annotation != null && !annotation.value().isEmpty()) {
                return annotation.value();
            }
        } catch (Exception e) {
            // 忽略，回退到类名
        }
        String simpleName = className;
        int idx = className.lastIndexOf('.');
        if (idx >= 0) {
            simpleName = className.substring(idx + 1);
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
