package com.weacsoft.jaravel.vendor.plugin.jar.scanner;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：{@link SharedDependencyScanner} 必须在扫描时为路由填充正确的 {@code beanName}。
 * <p>
 * 历史 bug：{@code currentBeanName} 原先在类级 {@code visitEnd()} 中赋值，
 * 而 ASM 的调用顺序是 类注解 → 各方法（visitMethod → 方法注解 visitEnd）→ 类 visitEnd，
 * 因此所有方法注解的 visitEnd 读到的 {@code currentBeanName} 均为 {@code null}，
 * 导致每条插件路由的 {@code beanName = null}，分发时 {@code getBean(null)} 失败 → 500。
 * <p>
 * 修复后 {@code currentBeanName} 在 {@code @PluginComponent} 注解访问器的 {@code visitEnd()}
 * （早于任何 {@code visitMethod}）中赋值，本测试验证三条路径（@PluginMapping / @PluginRoute /
 * @PluginAlias）的 beanName 均非 null 且值正确。
 */
class SharedDependencyScannerBeanNameTest {

    private static final String PKG = "com/example/plugin";
    private static final String CLASS_NAME = PKG + "/TestController";
    private static final String FQCN = "com.example.plugin.TestController";
    private static final String EXPECTED_BEAN = "testController"; // decapitalize("TestController")

    /**
     * 用 ASM 生成一个最小 fixture 类的字节码：
     * <ul>
     *   <li>{@code @PluginComponent}（无 value → 默认 Bean 名 = 首字母小写类名）</li>
     *   <li>方法 {@code hello()}：{@code @PluginMapping(path = "/hello")}（自动注册，默认 GET）</li>
     *   <li>方法 {@code manual()}：{@code @PluginRoute(path = "/manual")}（手动注册，默认 GET）</li>
     *   <li>方法 {@code aliased()}：{@code @PluginMapping(path = "/main")} + {@code @PluginAlias(path = "/alt")}</li>
     * </ul>
     */
    private static byte[] generateFixtureClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CLASS_NAME, null, "java/lang/Object", null);

        // --- @PluginComponent（无 value → 默认空字符串 → 使用首字母小写类名） ---
        AnnotationVisitor compAv = cw.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginComponent;", true);
        compAv.visit("value", "");
        compAv.visitEnd();

        // --- 方法 hello()：@PluginMapping(path = "/hello") ---
        MethodVisitor mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "hello", "()V", null, null);
        AnnotationVisitor mapAv1 = mv1.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginMapping;", true);
        mapAv1.visit("path", "/hello");
        mapAv1.visitEnum("method", "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/HttpMethod;", "GET");
        mapAv1.visitEnd();
        mv1.visitCode();
        mv1.visitInsn(Opcodes.RETURN);
        mv1.visitMaxs(1, 1);
        mv1.visitEnd();

        // --- 方法 manual()：@PluginRoute(path = "/manual") ---
        MethodVisitor mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "manual", "()V", null, null);
        AnnotationVisitor routeAv = mv2.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginRoute;", true);
        routeAv.visit("path", "/manual");
        routeAv.visitEnum("method", "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/HttpMethod;", "GET");
        routeAv.visitEnd();
        mv2.visitCode();
        mv2.visitInsn(Opcodes.RETURN);
        mv2.visitMaxs(1, 1);
        mv2.visitEnd();

        // --- 方法 aliased()：@PluginMapping(path = "/main") + @PluginAlias(path = "/alt") ---
        MethodVisitor mv3 = cw.visitMethod(Opcodes.ACC_PUBLIC, "aliased", "()V", null, null);
        AnnotationVisitor mapAv3 = mv3.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginMapping;", true);
        mapAv3.visit("path", "/main");
        mapAv3.visitEnum("method", "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/HttpMethod;", "GET");
        mapAv3.visitEnd();
        AnnotationVisitor aliasAv = mv3.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginAlias;", true);
        aliasAv.visit("path", "/alt");
        aliasAv.visitEnd();
        mv3.visitCode();
        mv3.visitInsn(Opcodes.RETURN);
        mv3.visitMaxs(1, 1);
        mv3.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** 把单个类字节包装成最小 JAR 字节数组。 */
    private static byte[] wrapInJar(byte[] classBytes) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                zos.putNextEntry(new ZipEntry(CLASS_NAME + ".class"));
                zos.write(classBytes);
                zos.closeEntry();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 核心回归断言：扫描后所有路由（自动/手动/别名）的 {@code beanName} 均等于
     * 首字母小写的类名，而非 {@code null}。
     */
    @Test
    void allRoutesCarryNonNullBeanName() {
        byte[] jarBytes = wrapInJar(generateFixtureClass());
        SharedDependencyScanner.ScanResult result =
                SharedDependencyScanner.scan(jarBytes, Collections.emptySet());

        // 组件类应被识别
        assertTrue(result.getComponentClasses().contains(FQCN),
                "componentClasses should contain " + FQCN + " but was " + result.getComponentClasses());

        // --- @PluginMapping 自动注册路由 ---
        Set<RouteInfo> autoRoutes = result.toRouteInfos();
        assertFalse(autoRoutes.isEmpty(), "should have at least one auto-registered route");
        for (RouteInfo r : autoRoutes) {
            assertNotNull(r.getBeanName(),
                    "auto route " + r.getPath() + " has null beanName (regression)");
            assertEquals(EXPECTED_BEAN, r.getBeanName(),
                    "auto route " + r.getPath() + " beanName should be " + EXPECTED_BEAN);
        }
        // 验证具体路由
        RouteInfo hello = findByPath(autoRoutes, "/hello");
        assertNotNull(hello, "missing /hello in auto routes: " + autoRoutes);
        assertEquals("hello", hello.getMethodName());
        assertEquals(HttpMethod.GET, hello.getMethod());

        // --- @PluginRoute 手动注册路由 ---
        Set<RouteInfo> manualRoutes = result.toAvailableRouteInfos();
        assertFalse(manualRoutes.isEmpty(), "should have at least one manual route");
        for (RouteInfo r : manualRoutes) {
            assertNotNull(r.getBeanName(),
                    "manual route " + r.getPath() + " has null beanName (regression)");
            assertEquals(EXPECTED_BEAN, r.getBeanName(),
                    "manual route " + r.getPath() + " beanName should be " + EXPECTED_BEAN);
        }
        RouteInfo manual = findByPath(manualRoutes, "/manual");
        assertNotNull(manual, "missing /manual in manual routes: " + manualRoutes);
        assertEquals("manual", manual.getMethodName());

        // --- @PluginAlias 别名路由（与主路由共用 beanName） ---
        // @PluginAlias 路由跟随主路由分类：主路由是 @PluginMapping → 自动注册
        // 所以 /alt 和 /main 都在 autoRoutes 中
        RouteInfo main = findByPath(autoRoutes, "/main");
        assertNotNull(main, "missing /main in auto routes: " + autoRoutes);
        assertEquals(EXPECTED_BEAN, main.getBeanName());

        RouteInfo alt = findByPath(autoRoutes, "/alt");
        assertNotNull(alt, "missing /alt (alias) in auto routes: " + autoRoutes);
        assertEquals(EXPECTED_BEAN, alt.getBeanName(), "alias route beanName should match primary");
        assertEquals("aliased", alt.getMethodName(), "alias route should share method name with primary");
    }

    /**
     * 显式指定 Bean 名（{@code @PluginComponent("customBean")}）时，
     * 路由的 beanName 应为自定义值而非类名首字母小写。
     */
    @Test
    void explicitBeanNameIsUsed() {
        byte[] jarBytes = wrapInJar(generateFixtureClassWithBeanName("customBean"));
        SharedDependencyScanner.ScanResult result =
                SharedDependencyScanner.scan(jarBytes, Collections.emptySet());

        for (RouteInfo r : result.toRouteInfos()) {
            assertEquals("customBean", r.getBeanName(),
                    "route " + r.getPath() + " should use explicit beanName");
        }
        for (RouteInfo r : result.toAvailableRouteInfos()) {
            assertEquals("customBean", r.getBeanName(),
                    "route " + r.getPath() + " should use explicit beanName");
        }
    }

    private static byte[] generateFixtureClassWithBeanName(String beanName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CLASS_NAME, null, "java/lang/Object", null);

        AnnotationVisitor compAv = cw.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginComponent;", true);
        compAv.visit("value", beanName);
        compAv.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "hello", "()V", null, null);
        AnnotationVisitor mapAv = mv.visitAnnotation(
                "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/PluginMapping;", true);
        mapAv.visit("path", "/hello");
        mapAv.visitEnum("method", "Lcom/weacsoft/jaravel/vendor/plugin/jar/annotation/HttpMethod;", "GET");
        mapAv.visitEnd();
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static RouteInfo findByPath(Set<RouteInfo> routes, String path) {
        return routes.stream()
                .filter(r -> path.equals(r.getPath()))
                .findFirst()
                .orElse(null);
    }
}
