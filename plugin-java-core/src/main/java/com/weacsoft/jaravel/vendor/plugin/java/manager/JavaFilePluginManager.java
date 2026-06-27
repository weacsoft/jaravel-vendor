package com.weacsoft.jaravel.vendor.plugin.java.manager;

import com.weacsoft.jaravel.vendor.plugin.java.classloader.DynamicClassLoader;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler;
import com.weacsoft.jaravel.vendor.plugin.java.compiler.DynamicJavaCompiler.JavaSourceFile;
import com.weacsoft.jaravel.vendor.plugin.java.model.JavaFilePluginInfo;
import com.weacsoft.jaravel.vendor.plugin.java.scanner.JavaFileScanner;
import com.weacsoft.jaravel.vendor.plugin.java.scanner.JavaFileScanner.RouteScanInfo;
import com.weacsoft.jaravel.vendor.plugin.java.scanner.JavaFileScanner.ScanResult;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java 文件插件管理器。
 * <p>
 * 核心管理器，负责 Java 源码插件的完整生命周期：注册、启用、禁用、热重载。
 * <p>
 * 支持两种源码输入方式：
 * <ul>
 *   <li><b>文件模式</b>：{@link #registerPlugin(Path)} 扫描目录中的 .java 文件，适合本地开发。</li>
 *   <li><b>字符串模式</b>：{@link #registerPluginFromSource(String, Map)} 直接传入源码字符串，
 *       适合从数据库、网络、配置中心等任意来源获取源码后编译热加载，无需文件系统。</li>
 * </ul>
 * <p>
 * 工作流程：
 * <ol>
 *   <li>注册（文件或字符串）：解析包名/类名，编译为内存字节码，
 *       创建 {@link DynamicClassLoader}，反射扫描 {@link PluginComponent} 注解，存储插件信息。</li>
 *   <li>{@link #enablePlugin(String)}：加载组件类，通过 {@link PluginBeanRegistrar} 注册 Bean，
 *       根据 autoRegister 模式处理路由：
 *       <ul>
 *         <li>autoRegister=true：自动注册 {@code @PluginMapping} 路由，{@code @PluginRoute} 路由保留为可注册。</li>
 *         <li>autoRegister=false：所有路由保留为可注册，不自动注册。</li>
 *       </ul>
 *   </li>
 *   <li>{@link #disablePlugin(String)}：注销路由和 Bean，关闭 ClassLoader。</li>
 *   <li>热重载：{@link #reloadPlugin(String)}（文件模式）或 {@link #reloadPluginFromSource(String, Map)}（字符串模式），
 *       先禁用再重新编译启用。</li>
 * </ol>
 * <p>
 * 线程安全：使用 {@link java.util.concurrent.locks.ReadWriteLock} 保护所有状态变更操作。
 */
public class JavaFilePluginManager {

    private static final Logger log = LoggerFactory.getLogger(JavaFilePluginManager.class);

    /** 匹配 package 声明 */
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "package\\s+([\\w.]+)\\s*;");

    /** 匹配 class/interface/enum/record 声明 */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:class|interface|enum|record)\\s+(\\w+)");

    /** 源目录根路径 */
    private final Path sourceDir;

    /** Bean 注册器（来自 plugin-jar-core） */
    private final PluginBeanRegistrar beanRegistrar;

    /** 路由注册器（来自 plugin-jar-core） */
    private final PluginRouteRegistrar routeRegistrar;

    /** 是否自动注册插件路由（true=自动注册@PluginMapping，false=手动注册） */
    private final boolean autoRegister;

    /** 读写锁，保护状态变更 */
    private final java.util.concurrent.locks.ReadWriteLock rwLock =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    /** 插件信息表：pluginId -> JavaFilePluginInfo */
    private final Map<String, JavaFilePluginInfo> plugins = new ConcurrentHashMap<>();

    /** 类加载器表：pluginId -> DynamicClassLoader */
    private final Map<String, DynamicClassLoader> classLoaders = new ConcurrentHashMap<>();

    /** 动态编译器 */
    private final DynamicJavaCompiler compiler = new DynamicJavaCompiler();

    /** 反射扫描器 */
    private final JavaFileScanner scanner = new JavaFileScanner();

    /**
     * 构造 Java 文件插件管理器。
     *
     * @param sourceDir      源目录根路径
     * @param beanRegistrar  Bean 注册器
     * @param routeRegistrar 路由注册器
     * @param autoRegister   是否自动注册插件路由（true=自动注册@PluginMapping，false=手动注册）
     */
    public JavaFilePluginManager(Path sourceDir, PluginBeanRegistrar beanRegistrar,
                                 PluginRouteRegistrar routeRegistrar, boolean autoRegister) {
        this.sourceDir = sourceDir;
        this.beanRegistrar = beanRegistrar;
        this.routeRegistrar = routeRegistrar;
        this.autoRegister = autoRegister;
    }

    /**
     * 注册一个 .java 文件插件。
     * <p>
     * 扫描指定目录中的所有 .java 文件，解析包名和类名，编译为内存字节码，
     * 创建 ClassLoader，扫描注解，存储插件信息。
     *
     * @param pluginDir 插件目录路径
     * @return 插件 ID（目录名）
     * @throws RuntimeException 编译或扫描失败时抛出
     */
    public String registerPlugin(Path pluginDir) {
        rwLock.writeLock().lock();
        try {
            String pluginId = pluginDir.getFileName().toString();
            log.info("注册 Java 文件插件: {} ({})", pluginId, pluginDir);

            JavaFilePluginInfo info = new JavaFilePluginInfo(pluginId, pluginDir.toString());
            info.setSourceMode(JavaFilePluginInfo.SourceMode.FILE);

            try {
                // 1. 读取所有 .java 文件
                List<JavaSourceFile> sourceFiles = readJavaFiles(pluginDir, info);

                if (sourceFiles.isEmpty()) {
                    throw new RuntimeException("插件目录中没有 .java 文件: " + pluginDir);
                }

                // 2. 编译、扫描、存储（公共逻辑）
                compileScanAndStore(pluginId, info, sourceFiles);

            } catch (Exception e) {
                log.error("插件 {} 注册失败", pluginId, e);
                info.setErrorMessage(e.getMessage());
                info.setState(JavaFilePluginInfo.State.DISABLED);
                plugins.put(pluginId, info);
                throw new RuntimeException("插件注册失败: " + pluginId, e);
            }

            return pluginId;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 从源码字符串注册插件（字符串模式）。
     * <p>
     * 直接传入 Java 源码字符串，无需文件系统。适合从数据库、网络、配置中心等
     * 任意来源获取源码后编译热加载。
     * <p>
     * 方法内部自动解析每个源码字符串的 package 声明和类声明，推导出类全限定名，
     * 然后编译为内存字节码，创建 ClassLoader，扫描注解，存储插件信息。
     *
     * @param pluginId 插件 ID（由调用方指定，如 "my-plugin" 或数据库 ID）
     * @param sources  源码映射：key 为文件名（如 "Hello.java"，仅用于日志），value 为源码字符串
     * @return 插件 ID
     * @throws RuntimeException 编译或扫描失败时抛出
     */
    public String registerPluginFromSource(String pluginId, Map<String, String> sources) {
        rwLock.writeLock().lock();
        try {
            log.info("注册字符串源码插件: {} ({} 个源文件)", pluginId, sources != null ? sources.size() : 0);

            JavaFilePluginInfo info = new JavaFilePluginInfo(pluginId, null);
            info.setSourceMode(JavaFilePluginInfo.SourceMode.STRING);

            try {
                List<JavaSourceFile> sourceFiles = parseSourceStrings(sources);

                if (sourceFiles.isEmpty()) {
                    throw new RuntimeException("没有有效的源码: " + pluginId);
                }

                // 编译、扫描、存储（公共逻辑）
                compileScanAndStore(pluginId, info, sourceFiles);

            } catch (Exception e) {
                log.error("插件 {} 注册失败", pluginId, e);
                info.setErrorMessage(e.getMessage());
                info.setState(JavaFilePluginInfo.State.DISABLED);
                plugins.put(pluginId, info);
                throw new RuntimeException("插件注册失败: " + pluginId, e);
            }

            return pluginId;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 从单个源码字符串注册插件（便捷方法）。
     * <p>
     * 等价于 {@code registerPluginFromSource(pluginId, Map.of(fileName, sourceCode))}。
     *
     * @param pluginId   插件 ID
     * @param sourceCode 源码字符串
     * @return 插件 ID
     */
    public String registerPluginFromSource(String pluginId, String sourceCode) {
        return registerPluginFromSource(pluginId, Map.of("Source.java", sourceCode));
    }

    /**
     * 从源码字符串热重载插件（字符串模式）。
     * <p>
     * 先禁用旧版本（若已启用），然后用新源码重新编译并启用。
     * 适合运行时动态更新插件代码：从数据库/网络获取新版本源码后调用此方法。
     *
     * @param pluginId 插件 ID
     * @param sources  新源码映射
     * @return 重载成功返回 true
     */
    public boolean reloadPluginFromSource(String pluginId, Map<String, String> sources) {
        rwLock.writeLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null) {
                log.warn("插件不存在: {}", pluginId);
                return false;
            }

            log.info("热重载字符串源码插件: {} ({} 个源文件)", pluginId, sources != null ? sources.size() : 0);

            boolean wasEnabled = info.getState() == JavaFilePluginInfo.State.ENABLED;
            if (wasEnabled) {
                disablePlugin(pluginId);
            }

            try {
                List<JavaSourceFile> sourceFiles = parseSourceStrings(sources);
                if (sourceFiles.isEmpty()) {
                    throw new RuntimeException("没有有效的源码: " + pluginId);
                }

                // 编译、扫描、更新 info
                compileScanAndStore(pluginId, info, sourceFiles);

            } catch (Exception e) {
                log.error("插件 {} 从源码重载失败", pluginId, e);
                info.setErrorMessage(e.getMessage());
                info.setState(JavaFilePluginInfo.State.DISABLED);
                return false;
            }

            if (wasEnabled) {
                return enablePlugin(pluginId);
            }
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 从单个源码字符串热重载插件（便捷方法）。
     *
     * @param pluginId   插件 ID
     * @param sourceCode 新源码字符串
     * @return 重载成功返回 true
     */
    public boolean reloadPluginFromSource(String pluginId, String sourceCode) {
        return reloadPluginFromSource(pluginId, Map.of("Source.java", sourceCode));
    }

    /**
     * 启用插件：注册 Bean 和路由。
     *
     * @param pluginId 插件 ID
     * @return 启用成功返回 true
     */
    public boolean enablePlugin(String pluginId) {
        rwLock.writeLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null) {
                log.warn("插件不存在: {}", pluginId);
                return false;
            }

            if (info.getState() == JavaFilePluginInfo.State.ENABLED) {
                log.warn("插件已启用: {}", pluginId);
                return true;
            }

            DynamicClassLoader classLoader = classLoaders.get(pluginId);
            if (classLoader == null) {
                log.error("插件 ClassLoader 不存在: {}", pluginId);
                return false;
            }

            log.info("启用插件: {}", pluginId);

            try {
                Set<String> registeredBeans = new HashSet<>();

                // 注册组件 Bean
                for (String className : info.getComponentClasses()) {
                    Class<?> beanClass = classLoader.loadClass(className);
                    String beanName = deriveBeanName(beanClass, className);

                    if (beanRegistrar.registerBean(beanName, beanClass)) {
                        registeredBeans.add(beanName);
                    }
                }
                info.setRegisteredBeanNames(registeredBeans);

                // 根据自动注册模式处理路由
                if (autoRegister) {
                    // Auto-register 模式：自动注册 @PluginMapping 路由
                    routeRegistrar.registerRoutes(pluginId, info.getRouteMappings());
                    // @PluginRoute 路由保留在 availableRoutes 供手动注册
                } else {
                    // Manual 模式：不自动注册任何路由，将所有路由放入 availableRoutes
                    Set<RouteInfo> all = new HashSet<>(info.getRouteMappings());
                    all.addAll(info.getAvailableRoutes());
                    info.setAvailableRoutes(all);
                    info.setRouteMappings(new HashSet<>());
                }

                info.setState(JavaFilePluginInfo.State.ENABLED);
                log.info("插件 {} 启用成功：{} 个 Bean，{} 条路由，{} 条可注册路由",
                        pluginId, registeredBeans.size(),
                        info.getRouteMappings().size(), info.getAvailableRoutes().size());
                return true;

            } catch (Exception e) {
                log.error("插件 {} 启用失败", pluginId, e);
                info.setErrorMessage(e.getMessage());
                return false;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 禁用插件：注销路由和 Bean，关闭 ClassLoader。
     *
     * @param pluginId 插件 ID
     * @return 禁用成功返回 true
     */
    public boolean disablePlugin(String pluginId) {
        rwLock.writeLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null) {
                log.warn("插件不存在: {}", pluginId);
                return false;
            }

            log.info("禁用插件: {}", pluginId);

            // 注销路由
            try {
                routeRegistrar.unregisterRoutes(pluginId);
            } catch (Exception e) {
                log.warn("注销路由失败: {}", pluginId, e);
            }

            // 注销 Bean
            try {
                beanRegistrar.unregisterBeans(info.getRegisteredBeanNames());
            } catch (Exception e) {
                log.warn("注销 Bean 失败: {}", pluginId, e);
            }
            info.getRegisteredBeanNames().clear();

            // 关闭 ClassLoader
            DynamicClassLoader classLoader = classLoaders.remove(pluginId);
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException e) {
                    log.warn("关闭 ClassLoader 失败: {}", pluginId, e);
                }
            }

            info.setState(JavaFilePluginInfo.State.DISABLED);
            log.info("插件 {} 禁用完成", pluginId);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 热重载插件：禁用 -> 重新编译 -> 启用。
     *
     * @param pluginId 插件 ID
     * @return 重载成功返回 true
     */
    public boolean reloadPlugin(String pluginId) {
        rwLock.writeLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null) {
                log.warn("插件不存在: {}", pluginId);
                return false;
            }

            log.info("热重载插件: {}", pluginId);

            // 1. 禁用
            boolean wasEnabled = info.getState() == JavaFilePluginInfo.State.ENABLED;
            if (wasEnabled) {
                disablePlugin(pluginId);
            }

            // 2. 重新编译和扫描（文件模式）
            try {
                Path pluginDir = Paths.get(info.getSourceDir());
                List<JavaSourceFile> sourceFiles = readJavaFiles(pluginDir, info);

                if (sourceFiles.isEmpty()) {
                    throw new RuntimeException("插件目录中没有 .java 文件: " + pluginDir);
                }

                compileScanAndStore(pluginId, info, sourceFiles);

            } catch (Exception e) {
                log.error("插件 {} 重新编译失败", pluginId, e);
                info.setErrorMessage(e.getMessage());
                info.setState(JavaFilePluginInfo.State.DISABLED);
                return false;
            }

            // 3. 重新启用
            if (wasEnabled) {
                return enablePlugin(pluginId);
            }

            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取所有插件信息。
     *
     * @return 插件信息列表
     */
    public List<JavaFilePluginInfo> getAllPlugins() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(plugins.values());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 获取指定插件信息。
     *
     * @param pluginId 插件 ID
     * @return 插件信息，不存在返回 null
     */
    public JavaFilePluginInfo getPlugin(String pluginId) {
        rwLock.readLock().lock();
        try {
            return plugins.get(pluginId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 返回指定插件的可注册路由（未自动注册的路由）。
     *
     * @param pluginId 插件 ID
     * @return 可注册路由集合的副本
     */
    public Set<RouteInfo> getAvailableRoutes(String pluginId) {
        rwLock.readLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            return info != null ? new HashSet<>(info.getAvailableRoutes()) : new HashSet<>();
        } finally {
            rwLock.readLock().unlock();
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
        rwLock.writeLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null || info.getState() != JavaFilePluginInfo.State.ENABLED) {
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
            }
            return ok;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 检查插件是否有文件变更。
     *
     * @param pluginId 插件 ID
     * @return 有变更返回 true
     */
    public boolean hasChanges(String pluginId) {
        rwLock.readLock().lock();
        try {
            JavaFilePluginInfo info = plugins.get(pluginId);
            if (info == null) {
                return false;
            }

            long currentLastModified = computeLastModified(info.getSourceFiles());
            return currentLastModified > info.getLastModified();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 重载所有有变更的插件（供 Watch Service 调用）。
     */
    public void reloadAllChanged() {
        rwLock.readLock().lock();
        List<String> changedPluginIds;
        try {
            changedPluginIds = plugins.keySet().stream()
                    .filter(this::hasChanges)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }

        for (String pluginId : changedPluginIds) {
            log.info("检测到插件 {} 有变更，执行热重载", pluginId);
            reloadPlugin(pluginId);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 读取目录中的所有 .java 文件，解析包名和类名。
     */
    private List<JavaSourceFile> readJavaFiles(Path pluginDir, JavaFilePluginInfo info) throws IOException {
        List<JavaSourceFile> sourceFiles = new ArrayList<>();
        Set<String> sourceFilePaths = new HashSet<>();

        try (Stream<Path> paths = Files.walk(pluginDir)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);
                String className = parseClassName(content);
                String fileName = javaFile.getFileName().toString();

                if (className != null) {
                    sourceFiles.add(new JavaSourceFile(className, content, fileName));
                    sourceFilePaths.add(javaFile.toAbsolutePath().toString());
                } else {
                    log.warn("无法解析类名: {}", javaFile);
                }
            }
        }

        info.setSourceFiles(sourceFilePaths);
        return sourceFiles;
    }

    /**
     * 从源码字符串映射解析出 JavaSourceFile 列表（字符串模式）。
     * <p>
     * 遍历 sources 中的每个源码字符串，自动解析包名和类名，生成 JavaSourceFile。
     *
     * @param sources 源码映射：key 为文件名（仅用于日志），value 为源码字符串
     * @return 解析出的源文件列表
     */
    private List<JavaSourceFile> parseSourceStrings(Map<String, String> sources) {
        List<JavaSourceFile> sourceFiles = new ArrayList<>();

        if (sources == null || sources.isEmpty()) {
            return sourceFiles;
        }

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();

            if (content == null || content.isBlank()) {
                log.warn("源码为空: {}", fileName);
                continue;
            }

            String className = parseClassName(content);
            if (className != null) {
                sourceFiles.add(new JavaSourceFile(className, content, fileName));
            } else {
                log.warn("无法从源码解析类名: {}", fileName);
            }
        }

        return sourceFiles;
    }

    /**
     * 公共编译+扫描+存储逻辑（文件模式和字符串模式共用）。
     * <p>
     * 编译源文件 → 创建 ClassLoader → 扫描注解 → 更新插件信息。
     * 若 info 是已有插件（热重载场景），先关闭旧 ClassLoader。
     *
     * @param pluginId    插件 ID
     * @param info        插件信息对象
     * @param sourceFiles 源文件列表
     * @throws Exception 编译或扫描失败
     */
    private void compileScanAndStore(String pluginId, JavaFilePluginInfo info,
                                      List<JavaSourceFile> sourceFiles) throws Exception {
        // 热重载场景：先关闭旧 ClassLoader
        DynamicClassLoader oldClassLoader = classLoaders.remove(pluginId);
        if (oldClassLoader != null) {
            try {
                oldClassLoader.close();
            } catch (Exception e) {
                log.debug("关闭旧 ClassLoader 失败: {}", pluginId, e);
            }
        }

        // 编译
        Map<String, byte[]> compiledClasses = compiler.compile(sourceFiles, getClass().getClassLoader());
        log.info("插件 {} 编译完成，共 {} 个类", pluginId, compiledClasses.size());

        // 创建 ClassLoader
        DynamicClassLoader classLoader = new DynamicClassLoader(
                getClass().getClassLoader(), compiledClasses);
        classLoaders.put(pluginId, classLoader);

        // 扫描注解
        ScanResult scanResult = scanner.scan(classLoader, compiledClasses.keySet());

        info.setComponentClasses(scanResult.getComponentClasses());
        info.setRouteMappings(convertToRouteInfos(scanResult.getRouteMappings(), classLoader));
        info.setAvailableRoutes(convertToRouteInfos(scanResult.getAvailableRouteMappings(), classLoader));
        info.setLastModified(System.currentTimeMillis());
        info.setState(JavaFilePluginInfo.State.LOADED);
        info.setErrorMessage(null);

        plugins.put(pluginId, info);

        log.info("插件 {} 注册成功：{} 个组件，{} 条路由，{} 条可注册路由",
                pluginId, info.getComponentClasses().size(),
                info.getRouteMappings().size(), info.getAvailableRoutes().size());
    }

    /**
     * 从源代码中解析类全限定名。
     * <p>
     * 使用正则表达式匹配 package 声明和 class/interface/enum/record 声明。
     */
    private String parseClassName(String sourceCode) {
        String packageName = "";
        String simpleClassName = null;

        Matcher packageMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(sourceCode);
        if (classMatcher.find()) {
            simpleClassName = classMatcher.group(1);
        }

        if (simpleClassName == null) {
            return null;
        }

        return packageName.isEmpty() ? simpleClassName : packageName + "." + simpleClassName;
    }

    /**
     * 将扫描结果中的 RouteScanInfo 转换为 RouteInfo。
     * <p>
     * beanName 从类名派生（首字母小写），或从 @PluginComponent 注解的 value 获取。
     */
    private Set<RouteInfo> convertToRouteInfos(List<RouteScanInfo> routeScanInfos, ClassLoader classLoader) {
        Set<RouteInfo> routeInfos = new HashSet<>();
        for (RouteScanInfo scanInfo : routeScanInfos) {
            String beanName = deriveBeanName(scanInfo.className(), classLoader);
            routeInfos.add(new RouteInfo(
                    scanInfo.path(),
                    scanInfo.httpMethod(),
                    beanName,
                    scanInfo.methodName(),
                    scanInfo.produces()
            ));
        }
        return routeInfos;
    }

    /**
     * 从类全限定名派生 Bean 名称（首字母小写）。
     */
    private String deriveBeanName(String className, ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            PluginComponent annotation = clazz.getAnnotation(PluginComponent.class);
            if (annotation != null && !annotation.value().isEmpty()) {
                return annotation.value();
            }
        } catch (ClassNotFoundException e) {
            // 忽略，使用默认派生逻辑
        }

        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 从 @PluginComponent 注解派生 Bean 名称。
     */
    private String deriveBeanName(Class<?> beanClass, String className) {
        PluginComponent annotation = beanClass.getAnnotation(PluginComponent.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }

        String simpleName = beanClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 计算源文件集合的最大最后修改时间。
     */
    private long computeLastModified(Set<String> sourceFilePaths) {
        long maxLastModified = 0;
        for (String filePath : sourceFilePaths) {
            try {
                long lastModified = Files.getLastModifiedTime(Paths.get(filePath)).toMillis();
                if (lastModified > maxLastModified) {
                    maxLastModified = lastModified;
                }
            } catch (IOException e) {
                // 忽略无法读取的文件
            }
        }
        return maxLastModified;
    }
}
