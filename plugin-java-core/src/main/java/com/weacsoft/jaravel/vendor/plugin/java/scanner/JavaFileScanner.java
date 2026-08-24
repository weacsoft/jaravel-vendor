package com.weacsoft.jaravel.vendor.plugin.java.scanner;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginComponent;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginMapping;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.PluginRoute;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java 文件插件扫描器。
 * <p>
 * 由于 .java 文件已被编译为字节码，本扫描器直接使用反射（而非 ASM）扫描编译后的类，
 * 查找 {@link PluginComponent} 和 {@link PluginMapping} 注解。
 * <p>
 * 扫描流程：
 * <ol>
 *   <li>使用 {@code Class.forName(className, false, classLoader)} 加载类（不初始化）。</li>
 *   <li>检查类是否标注 {@link PluginComponent}，若是则加入组件类集合。</li>
 *   <li>对每个组件类，遍历方法查找 {@link PluginMapping} 注解，提取路由信息（自动注册）。</li>
 *   <li>同时查找 {@link PluginRoute} 注解，提取可注册路由信息（手动注册）。</li>
 * </ol>
 *
 * @see PluginComponent
 * @see PluginMapping
 * @see PluginRoute
 */
public class JavaFileScanner {

    /**
     * 扫描编译后的类，查找插件组件和路由映射。
     *
     * @param classLoader 动态类加载器（包含编译后的字节码）
     * @param classNames  待扫描的类全限定名集合
     * @return 扫描结果
     */
    public ScanResult scan(ClassLoader classLoader, Set<String> classNames) {
        ScanResult result = new ScanResult();

        if (classNames == null || classNames.isEmpty()) {
            return result;
        }

        for (String className : classNames) {
            try {
                // 加载类但不初始化（false 参数避免触发静态代码块）
                Class<?> clazz = Class.forName(className, false, classLoader);

                // 检查 @PluginComponent 注解
                if (clazz.isAnnotationPresent(PluginComponent.class)) {
                    result.getComponentClasses().add(className);

                    // 扫描方法上的 @PluginMapping 注解
                    scanMethods(clazz, className, result);
                }
            } catch (ClassNotFoundException e) {
                // 类未找到，跳过（可能是由其他类加载器加载的依赖类）
                continue;
            } catch (NoClassDefFoundError e) {
                // 依赖类未找到，跳过
                continue;
            }
        }

        return result;
    }

    /**
     * 扫描类中所有方法上的 @PluginMapping 和 @PluginRoute 注解。
     * <p>
     * {@code @PluginMapping} 方法加入 {@code routeMappings}（自动注册），
     * {@code @PluginRoute} 方法加入 {@code availableRouteMappings}（手动注册）。
     *
     * @param clazz     目标类
     * @param className 类全限定名
     * @param result    扫描结果（用于添加路由信息）
     */
    private void scanMethods(Class<?> clazz, String className, ScanResult result) {
        for (Method method : clazz.getDeclaredMethods()) {
            PluginMapping mapping = method.getAnnotation(PluginMapping.class);
            if (mapping != null) {
                RouteScanInfo routeInfo = new RouteScanInfo(
                        className,
                        method.getName(),
                        mapping.path(),
                        mapping.method(),
                        mapping.produces()
                );
                result.getRouteMappings().add(routeInfo);
            }

            PluginRoute pluginRoute = method.getAnnotation(PluginRoute.class);
            if (pluginRoute != null) {
                RouteScanInfo routeInfo = new RouteScanInfo(
                        className,
                        method.getName(),
                        pluginRoute.path(),
                        pluginRoute.method(),
                        pluginRoute.produces()
                );
                result.getAvailableRouteMappings().add(routeInfo);
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 扫描结果。
     */
    public static class ScanResult {

        /** 带 @PluginComponent 注解的类全限定名集合 */
        private final Set<String> componentClasses = new HashSet<>();

        /** 路由映射信息列表（来自 @PluginMapping，自动注册） */
        private final List<RouteScanInfo> routeMappings = new ArrayList<>();

        /** 可注册路由映射信息列表（来自 @PluginRoute，手动注册） */
        private final List<RouteScanInfo> availableRouteMappings = new ArrayList<>();

        public Set<String> getComponentClasses() {
            return componentClasses;
        }

        public List<RouteScanInfo> getRouteMappings() {
            return routeMappings;
        }

        public List<RouteScanInfo> getAvailableRouteMappings() {
            return availableRouteMappings;
        }
    }

    /**
     * 路由扫描信息。
     * <p>
     * 从 {@link PluginMapping} 或 {@link PluginRoute} 注解中提取的路由元数据。
     *
     * @param className  控制器类全限定名
     * @param methodName 处理方法名
     * @param path       路由路径
     * @param httpMethod HTTP 方法
     * @param produces   响应内容类型
     */
        public record RouteScanInfo(String className, String methodName, String path, HttpMethod httpMethod,
                                    String produces) {

        @Override
            public String toString() {
                return "RouteScanInfo{" +
                        "className='" + className + '\'' +
                        ", methodName='" + methodName + '\'' +
                        ", path='" + path + '\'' +
                        ", httpMethod=" + httpMethod +
                        ", produces='" + produces + '\'' +
                        '}';
            }
        }
}
