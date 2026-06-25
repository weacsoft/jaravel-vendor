package com.weacsoft.jaravel.vendor.plugin.jar.scanner;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * 共享依赖扫描器（基于 ASM 字节码扫描）。
 * <p>
 * 扫描插件 JAR，识别：
 * <ul>
 *   <li>标注 {@code @PluginComponent} 的类 -> 组件类。</li>
 *   <li>实现 {@code SharedService} 接口的类 -> 共享类依赖（需放入共享 ClassLoader）。</li>
 *   <li>标注 {@code @PluginMapping} 的方法 -> 路由映射。</li>
 * </ul>
 * <p>
 * 使用 ASM 直接读取字节码，无需加载类，避免污染 ClassLoader。
 * <p>
 * 共享包前缀用于判断类是否属于共享 API：匹配前缀的类不作为插件组件扫描。
 */
public class SharedDependencyScanner {

    private static final Logger log = LoggerFactory.getLogger(SharedDependencyScanner.class);

    /** PluginComponent 注解的 ASM 描述符 */
    public static final String PLUGIN_COMPONENT_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginComponent;";

    /** SharedService 接口的 ASM 描述符 */
    public static final String SHARED_SERVICE_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/SharedService;";

    /** PluginMapping 注解的 ASM 描述符 */
    public static final String PLUGIN_MAPPING_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginMapping;";

    /** PluginRoute 注解的 ASM 描述符 */
    public static final String PLUGIN_ROUTE_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginRoute;";

    /** PluginAlias 注解的 ASM 描述符 */
    public static final String PLUGIN_ALIAS_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginAlias;";

    /** PluginAliases 容器注解的 ASM 描述符（@Repeatable 生成的容器） */
    public static final String PLUGIN_ALIASES_DESC =
            "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginAliases;";

    /**
     * 扫描 JAR 文件。
     *
     * @param jarPath               JAR 路径
     * @param sharedPackagePrefixes 共享包前缀集合（匹配前缀的类跳过扫描）
     * @return 扫描结果
     */
    public static ScanResult scan(Path jarPath, Set<String> sharedPackagePrefixes) {
        ScanResult result = new ScanResult();
        Set<String> prefixes = sharedPackagePrefixes == null ? Collections.emptySet() : sharedPackagePrefixes;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName()
                        .replace('/', '.')
                        .replace(".class", "");
                // 跳过共享包前缀的类
                if (matchesPrefix(className, prefixes)) {
                    continue;
                }
                try (InputStream is = jarFile.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(is);
                    ClassScannerVisitor visitor = new ClassScannerVisitor(className, result);
                    reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (IOException e) {
                    log.warn("扫描类失败: {}", className, e);
                }
            }
        } catch (IOException e) {
            log.error("扫描 JAR 失败: {}", jarPath, e);
        }
        return result;
    }

    private static boolean matchesPrefix(String className, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (prefix != null && className.startsWith(prefix.replace('/', '.'))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描结果。
     */
    public static class ScanResult {
        /** 组件类名集合（标注 @PluginComponent） */
        private final Set<String> componentClasses = new HashSet<>();
        /** 共享类依赖名集合（实现 SharedService） */
        private final Set<String> sharedClassDependencies = new HashSet<>();
        /** 路由扫描信息集合（标注 @PluginMapping，自动注册） */
        private final List<RouteScanInfo> routeScanInfos = new ArrayList<>();
        /** 可注册路由扫描信息集合（标注 @PluginRoute，手动注册） */
        private final List<RouteScanInfo> availableRouteScanInfos = new ArrayList<>();

        public Set<String> getComponentClasses() {
            return componentClasses;
        }

        public Set<String> getSharedClassDependencies() {
            return sharedClassDependencies;
        }

        public List<RouteScanInfo> getRouteScanInfos() {
            return routeScanInfos;
        }

        public List<RouteScanInfo> getAvailableRouteScanInfos() {
            return availableRouteScanInfos;
        }

        /**
         * 将路由扫描信息转换为 RouteInfo 集合。
         *
         * @return RouteInfo 集合
         */
        public Set<RouteInfo> toRouteInfos() {
            Set<RouteInfo> routeInfos = new HashSet<>();
            for (RouteScanInfo info : routeScanInfos) {
                routeInfos.add(new RouteInfo(
                        info.getPath(),
                        info.getMethod(),
                        info.getBeanName(),
                        info.getMethodName(),
                        info.getProduces()
                ));
            }
            return routeInfos;
        }

        /**
         * 将可注册路由扫描信息转换为 RouteInfo 集合。
         *
         * @return RouteInfo 集合
         */
        public Set<RouteInfo> toAvailableRouteInfos() {
            Set<RouteInfo> routeInfos = new HashSet<>();
            for (RouteScanInfo info : availableRouteScanInfos) {
                routeInfos.add(new RouteInfo(
                        info.getPath(),
                        info.getMethod(),
                        info.getBeanName(),
                        info.getMethodName(),
                        info.getProduces()
                ));
            }
            return routeInfos;
        }
    }

    /**
     * 路由扫描信息。
     */
    public static class RouteScanInfo {
        private String path;
        private HttpMethod method = HttpMethod.GET;
        private String beanName;
        private String methodName;
        private String produces = "application/json";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public HttpMethod getMethod() {
            return method;
        }

        public void setMethod(HttpMethod method) {
            this.method = method;
        }

        public String getBeanName() {
            return beanName;
        }

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getProduces() {
            return produces;
        }

        public void setProduces(String produces) {
            this.produces = produces;
        }
    }

    /**
     * 类扫描访问器。
     */
    static class ClassScannerVisitor extends ClassVisitor {

        private final String className;
        private final ScanResult result;
        private boolean isComponent;
        private String componentBeanName;
        private boolean isSharedService;

        ClassScannerVisitor(String className, ScanResult result) {
            super(Opcodes.ASM9);
            this.className = className;
            this.result = result;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // 检查是否实现 SharedService 接口
            if (interfaces != null) {
                String sharedServiceInternal = SHARED_SERVICE_DESC.substring(1, SHARED_SERVICE_DESC.length() - 1);
                for (String iface : interfaces) {
                    if (sharedServiceInternal.equals(iface)) {
                        isSharedService = true;
                        break;
                    }
                }
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (PLUGIN_COMPONENT_DESC.equals(descriptor)) {
                isComponent = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name) && value instanceof String s) {
                            componentBeanName = s;
                        }
                        super.visit(name, value);
                    }
                };
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodScannerVisitor(className, name, result, this);
        }

        @Override
        public void visitEnd() {
            if (isComponent) {
                result.getComponentClasses().add(className);
                // 记录 Bean 名称到当前类的映射（通过 MethodScannerVisitor 使用）
                currentBeanName = (componentBeanName != null && !componentBeanName.isEmpty())
                        ? componentBeanName
                        : decapitalize(simpleName(className));
            }
            if (isSharedService) {
                result.getSharedClassDependencies().add(className);
            }
            super.visitEnd();
        }

        /** 当前类的 Bean 名称（供方法扫描器使用） */
        String currentBeanName;

        private String simpleName(String className) {
            int idx = className.lastIndexOf('.');
            return idx >= 0 ? className.substring(idx + 1) : className;
        }

        private String decapitalize(String name) {
            if (name == null || name.isEmpty()) {
                return name;
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
    }

    /**
     * 方法扫描访问器。
     * <p>
     * 扫描方法上的注解：
     * <ul>
     *   <li>{@code @PluginMapping}：创建主路由（自动注册）</li>
     *   <li>{@code @PluginRoute}：创建可注册路由（手动注册）</li>
     *   <li>{@code @PluginAlias}：为当前方法创建别名路由（继承主路由的 beanName/methodName）</li>
     *   <li>{@code @PluginAliases}：{@code @PluginAlias} 的容器（{@code @Repeatable} 生成）</li>
     * </ul>
     * {@code @PluginAlias} 的别名路由会跟随主路由的分类：
     * 若主路由是 {@code @PluginMapping}（自动注册），别名也是自动注册；
     * 若主路由是 {@code @PluginRoute}（手动注册），别名也是手动注册。
     */
    static class MethodScannerVisitor extends MethodVisitor {

        private final String className;
        private final String methodName;
        private final ScanResult result;
        private final ClassScannerVisitor classVisitor;

        /** 当前方法的主路由是否为 available（@PluginRoute） */
        private boolean currentMethodAvailable = false;
        /** 当前方法是否已有主路由 */
        private boolean hasPrimaryRoute = false;

        MethodScannerVisitor(String className, String methodName, ScanResult result,
                             ClassScannerVisitor classVisitor) {
            super(Opcodes.ASM9);
            this.className = className;
            this.methodName = methodName;
            this.result = result;
            this.classVisitor = classVisitor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (PLUGIN_MAPPING_DESC.equals(descriptor)) {
                RouteScanInfo info = new RouteScanInfo();
                info.setMethodName(methodName);
                // Bean 名称由类扫描器在 visitEnd 时设置；此处先取当前值
                info.setBeanName(classVisitor.currentBeanName);
                currentMethodAvailable = false;
                hasPrimaryRoute = true;
                return new PluginMappingAnnotationVisitor(info, result, classVisitor, false);
            }
            if (PLUGIN_ROUTE_DESC.equals(descriptor)) {
                RouteScanInfo info = new RouteScanInfo();
                info.setMethodName(methodName);
                info.setBeanName(classVisitor.currentBeanName);
                currentMethodAvailable = true;
                hasPrimaryRoute = true;
                return new PluginMappingAnnotationVisitor(info, result, classVisitor, true);
            }
            if (PLUGIN_ALIAS_DESC.equals(descriptor)) {
                // 单个 @PluginAlias
                RouteScanInfo aliasInfo = new RouteScanInfo();
                aliasInfo.setMethodName(methodName);
                aliasInfo.setBeanName(classVisitor.currentBeanName);
                return new PluginAliasAnnotationVisitor(aliasInfo, result, classVisitor,
                        () -> currentMethodAvailable);
            }
            if (PLUGIN_ALIASES_DESC.equals(descriptor)) {
                // @PluginAliases 容器（多个 @PluginAlias 被 @Repeatable 包装时）
                return new PluginAliasesContainerVisitor(result, classVisitor, methodName,
                        () -> currentMethodAvailable);
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

    /**
     * PluginMapping/PluginRoute 注解访问器。
     * <p>
     * 由于 {@code @PluginMapping} 与 {@code @PluginRoute} 具有相同的属性结构
     * （path/method/produces），复用此访问器解析两者。
     * {@code available=false} 时添加到 {@code routeScanInfos}（自动注册），
     * {@code available=true} 时添加到 {@code availableRouteScanInfos}（手动注册）。
     */
    static class PluginMappingAnnotationVisitor extends AnnotationVisitor {

        private final RouteScanInfo info;
        private final ScanResult result;
        private final ClassScannerVisitor classVisitor;
        private final boolean available;

        PluginMappingAnnotationVisitor(RouteScanInfo info, ScanResult result,
                                       ClassScannerVisitor classVisitor, boolean available) {
            super(Opcodes.ASM9);
            this.info = info;
            this.result = result;
            this.classVisitor = classVisitor;
            this.available = available;
        }

        @Override
        public void visit(String name, Object value) {
            if ("value".equals(name) && value instanceof String s) {
                info.setPath(s);
            } else if ("path".equals(name) && value instanceof String s) {
                info.setPath(s);
            } else if ("produces".equals(name) && value instanceof String s) {
                info.setProduces(s);
            }
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("method".equals(name)) {
                try {
                    info.setMethod(HttpMethod.valueOf(value));
                } catch (IllegalArgumentException e) {
                    info.setMethod(HttpMethod.GET);
                }
            }
            super.visitEnum(name, descriptor, value);
        }

        @Override
        public void visitEnd() {
            // 确保使用类扫描器最终的 Bean 名称
            info.setBeanName(classVisitor.currentBeanName);
            if (info.getPath() != null) {
                if (available) {
                    result.getAvailableRouteScanInfos().add(info);
                } else {
                    result.getRouteScanInfos().add(info);
                }
            }
            super.visitEnd();
        }
    }

    /**
     * PluginAlias 注解访问器。
     * <p>
     * 解析单个 {@code @PluginAlias} 注解，生成一条别名路由。
     * 别名路由的 {@code available} 标志由主路由决定（通过 {@code availableSupplier} 获取）。
     */
    static class PluginAliasAnnotationVisitor extends AnnotationVisitor {

        private final RouteScanInfo info;
        private final ScanResult result;
        private final ClassScannerVisitor classVisitor;
        private final java.util.function.BooleanSupplier availableSupplier;

        PluginAliasAnnotationVisitor(RouteScanInfo info, ScanResult result,
                                     ClassScannerVisitor classVisitor,
                                     java.util.function.BooleanSupplier availableSupplier) {
            super(Opcodes.ASM9);
            this.info = info;
            this.result = result;
            this.classVisitor = classVisitor;
            this.availableSupplier = availableSupplier;
        }

        @Override
        public void visit(String name, Object value) {
            if ("value".equals(name) && value instanceof String s) {
                info.setPath(s);
            } else if ("path".equals(name) && value instanceof String s) {
                info.setPath(s);
            } else if ("produces".equals(name) && value instanceof String s) {
                info.setProduces(s);
            }
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("method".equals(name)) {
                try {
                    info.setMethod(HttpMethod.valueOf(value));
                } catch (IllegalArgumentException e) {
                    info.setMethod(HttpMethod.GET);
                }
            }
            super.visitEnum(name, descriptor, value);
        }

        @Override
        public void visitEnd() {
            info.setBeanName(classVisitor.currentBeanName);
            if (info.getPath() != null) {
                boolean available = availableSupplier.getAsBoolean();
                if (available) {
                    result.getAvailableRouteScanInfos().add(info);
                } else {
                    result.getRouteScanInfos().add(info);
                }
            }
            super.visitEnd();
        }
    }

    /**
     * PluginAliases 容器注解访问器。
     * <p>
     * 当方法上有多个 {@code @PluginAlias} 时，编译器自动包装为 {@code @PluginAliases}。
     * 本访问器遍历容器中的每个 {@code @PluginAlias}，为每个生成一条别名路由。
     */
    static class PluginAliasesContainerVisitor extends AnnotationVisitor {

        private final ScanResult result;
        private final ClassScannerVisitor classVisitor;
        private final String methodName;
        private final java.util.function.BooleanSupplier availableSupplier;

        PluginAliasesContainerVisitor(ScanResult result, ClassScannerVisitor classVisitor,
                                     String methodName,
                                     java.util.function.BooleanSupplier availableSupplier) {
            super(Opcodes.ASM9);
            this.result = result;
            this.classVisitor = classVisitor;
            this.methodName = methodName;
            this.availableSupplier = availableSupplier;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("value".equals(name)) {
                // 遍历 @PluginAlias[] 数组，每个元素是一个 @PluginAlias 注解
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        if (PLUGIN_ALIAS_DESC.equals(descriptor)) {
                            RouteScanInfo aliasInfo = new RouteScanInfo();
                            aliasInfo.setMethodName(methodName);
                            aliasInfo.setBeanName(classVisitor.currentBeanName);
                            return new PluginAliasAnnotationVisitor(aliasInfo, result, classVisitor,
                                    availableSupplier);
                        }
                        return super.visitAnnotation(name, descriptor);
                    }
                };
            }
            return super.visitArray(name);
        }
    }
}
