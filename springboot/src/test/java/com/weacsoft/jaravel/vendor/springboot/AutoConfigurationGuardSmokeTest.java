package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.springboot.database.DatabaseAutoConfiguration;
import com.weacsoft.jaravel.vendor.springboot.jblade.ViewAutoConfiguration;
import com.weacsoft.jaravel.vendor.springboot.migration.MigrationPublishAutoConfiguration;
import com.weacsoft.jaravel.vendor.springboot.schedule.SchedulePublishAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动装配「启动冒烟」测试：守卫必须让缺 optional 模块的应用<b>照常启动</b>。
 * <p>
 * 背景（实测过的事故）：{@code springboot} 模块里有若干自动配置类直接引用了
 * optional 依赖（auth / database / jblade / migration / schedule）里的类型。
 * 由于 {@code @ConditionalOnClass} 挂在<b>方法</b>上保护不了「声明类自身的类加载」，
 * 一旦这些模块不在 classpath，Spring 内省配置类时就会抛
 * {@code NoClassDefFoundError} / {@code ClassNotFoundException}，应用直接启动失败。
 * <p>
 * 本测试用自定义 {@link ClassLoader} 把对应包「藏起来」，模拟模块缺失的真实场景；
 * 另用反射守住一个结构性约束：外层自动配置类的<b>方法签名</b>不得引用 optional 模块类型
 * （必须把这类 bean 放进带类级条件的内部配置类里）。
 */
class AutoConfigurationGuardSmokeTest {

    /**
     * 构造一个把指定包前缀隐藏掉的类加载器，用于模拟「该 optional 模块不在 classpath」。
     *
     * @param hiddenPrefixes 要隐藏的包前缀（如 {@code com.weacsoft.jaravel.vendor.database.}）
     * @return 过滤后的类加载器
     */
    private static ClassLoader hiding(String... hiddenPrefixes) {
        return new ClassLoader(AutoConfigurationGuardSmokeTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                for (String prefix : hiddenPrefixes) {
                    if (name.startsWith(prefix)) {
                        throw new ClassNotFoundException(name);
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    /**
     * 在「模块缺失」的类加载器下注册该自动配置类：应当被条件跳过而不是炸掉。
     *
     * @param autoConfiguration 自动配置类
     * @param hiddenPrefix      被隐藏的模块包前缀
     */
    private static void assertSkippedWhenModuleAbsent(Class<?> autoConfiguration, String hiddenPrefix) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setClassLoader(hiding(hiddenPrefix));
        context.register(autoConfiguration);
        assertDoesNotThrow(context::refresh,
                "缺 " + hiddenPrefix + " 时 " + autoConfiguration.getSimpleName()
                        + " 必须被类级 @ConditionalOnClass 跳过，而不是抛 NoClassDefFoundError");
        context.close();
    }

    @Test
    void databaseAutoConfigurationIsSkippedWithoutDatabaseModule() {
        assertSkippedWhenModuleAbsent(DatabaseAutoConfiguration.class,
                "com.weacsoft.jaravel.vendor.database.");
    }

    @Test
    void viewAutoConfigurationIsSkippedWithoutJbladeModule() {
        assertSkippedWhenModuleAbsent(ViewAutoConfiguration.class,
                "com.weacsoft.jaravel.vendor.jblade.");
    }

    @Test
    void migrationPublishAutoConfigurationIsSkippedWithoutMigrationModule() {
        assertSkippedWhenModuleAbsent(MigrationPublishAutoConfiguration.class,
                "com.weacsoft.jaravel.vendor.migration.");
    }

    @Test
    void schedulePublishAutoConfigurationIsSkippedWithoutScheduleModule() {
        assertSkippedWhenModuleAbsent(SchedulePublishAutoConfiguration.class,
                "com.weacsoft.jaravel.vendor.schedule.");
    }

    /**
     * 结构性约束：主路由自动配置类的方法签名不得出现 auth 模块类型 ——
     * 一旦出现，缺 auth 时又会退回「整类加载失败」。带条件的内部类是允许的。
     */
    @Test
    void routeAutoConfigurationOuterClassDoesNotReferenceOptionalAuthTypes() {
        for (Method method : SpringBootRouteAutoConfiguration.class.getDeclaredMethods()) {
            assertNoOptionalTypes(method);
        }
        for (java.lang.reflect.Constructor<?> constructor
                : SpringBootRouteAutoConfiguration.class.getDeclaredConstructors()) {
            for (Class<?> type : constructor.getParameterTypes()) {
                assertFalse(type.getName().startsWith("com.weacsoft.jaravel.vendor.auth."),
                        "外层配置类不得引用 auth 类型：" + type.getName());
            }
        }
    }

    private static void assertNoOptionalTypes(Method method) {
        for (Class<?> type : method.getParameterTypes()) {
            assertFalse(type.getName().startsWith("com.weacsoft.jaravel.vendor.auth."),
                    "方法 " + method.getName() + " 的形参引用了 auth 类型 " + type.getName()
                            + "；应移入带类级 @ConditionalOnClass 的内部配置类");
        }
        assertFalse(method.getReturnType().getName().startsWith("com.weacsoft.jaravel.vendor.auth."),
                "方法 " + method.getName() + " 的返回类型引用了 auth 类型");
    }

    /**
     * 内部配置类必须同时具备类级守卫，否则它仍会在缺 auth 时被加载。
     */
    @Test
    void innerAuthConfigurationIsClassLevelGuarded() {
        Class<?> inner = null;
        for (Class<?> candidate : SpringBootRouteAutoConfiguration.class.getDeclaredClasses()) {
            if (candidate.getSimpleName().contains("AuthRouteAuthHandler")) {
                inner = candidate;
                break;
            }
        }
        assertTrue(inner != null, "应存在承载 authRouteAuthHandler 的内部配置类");
        boolean guarded = false;
        for (java.lang.annotation.Annotation annotation : inner.getAnnotations()) {
            if (annotation.annotationType().getName()
                    .equals("org.springframework.boot.autoconfigure.condition.ConditionalOnClass")) {
                guarded = true;
            }
        }
        assertTrue(guarded, "内部配置类必须带类级 @ConditionalOnClass");
    }
}