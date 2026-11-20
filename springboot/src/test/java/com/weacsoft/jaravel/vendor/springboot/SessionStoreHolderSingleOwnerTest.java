package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.springboot.auth.SessionGuardAutoConfiguration;
import com.weacsoft.jaravel.vendor.springboot.session.HttpSessionAutoConfiguration;
import com.weacsoft.jaravel.vendor.session.SessionStoreHolder;
import gaarason.database.util.ObjectUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sessionStoreHolder} 唯一声明点的回归测试。
 * <p>
 * 背景（实测过的事故）：{@code session} 拆分为可选模块后，
 * {@code session.HttpSessionAutoConfiguration} 与 {@code auth.SessionGuardAutoConfiguration}
 * <b>各自无条件定义</b>了同名 bean {@code sessionStoreHolder}。两者条件范围相同
 * （都是 Servlet Web），只要 {@code session} + {@code auth-session} 同时在 classpath，
 * Spring Boot 3.x（默认 {@code allow-bean-definition-overriding=false}）就抛：
 * <pre>
 * BeanDefinitionOverrideException: Invalid bean definition with name 'sessionStoreHolder'
 *   ... since there is already [factoryBeanName=...SessionGuardAutoConfiguration] bound.
 * </pre>
 * 应用<b>一律无法启动</b>，与是否真的使用 session 守卫无关。
 * <p>
 * 修复原则是「同一个 bean 只留一个声明点」：{@code HttpSessionAutoConfiguration} 独占定义，
 * {@code SessionGuardAutoConfiguration} 只做 {@code @Bean} 参数注入消费。
 * <p>
 * <b>测试策略</b>：springboot 模块的测试依赖里没有 {@code spring-boot-test}
 * （不为一个测试去动模块依赖），也不该用裸 {@code AnnotationConfigApplicationContext}——
 * 那不是 Web 上下文，{@code @ConditionalOnWebApplication(SERVLET)} 不成立，两个配置根本不装配，
 * 撞名测不出来；且缺 jaravel 的 {@code GlobalBeanProvider} 桥接时
 * {@code SessionStoreRegistrar} 会直接抛 {@code RegistrarException}。
 * 因此改为直接对<b>类结构</b>与<b>bean 定义语料</b>做断言：
 * 声明点数量、是否是 {@code @Bean} 方法、类级条件是否存在——这三条正是撞名的充分条件。
 */
class SessionStoreHolderSingleOwnerTest {

    /**
     * 统计一个配置类里「返回 {@link SessionStoreHolder} 的 {@code @Bean} 方法」的数量。
     * <p>
     * 用 {@link AnnotatedElementUtils} 而非 {@code getAnnotation}：与 Spring 自己的合并注解语义一致。
     *
     * @param configuration 自动配置类
     * @return 声明 holder 的 {@code @Bean} 方法名列表
     */
    private static List<String> holderBeanMethods(Class<?> configuration) {
        List<String> found = new ArrayList<>();
        for (Method method : configuration.getDeclaredMethods()) {
            if (!AnnotatedElementUtils.hasAnnotation(method, org.springframework.context.annotation.Bean.class)) {
                continue;
            }
            if (SessionStoreHolder.class.isAssignableFrom(method.getReturnType())) {
                found.add(method.getName());
            }
        }
        return found;
    }

    /** 判断类上是否带类级 {@code @ConditionalOnClass}。 */
    private static boolean hasClassLevelConditionalOnClass(Class<?> type) {
        return AnnotatedElementUtils.hasAnnotation(type,
                org.springframework.boot.autoconfigure.condition.ConditionalOnClass.class);
    }

    /**
     * 核心断言：{}@code sessionStoreHolder} 在整个 springboot 模块里<b>只有一个</b>声明点，
     * 且那个声明点是 {@code HttpSessionAutoConfiguration}。
     * <p>
     * 这条覆盖了撞名的充分条件——修复前这里会数到 2。
     */
    @Test
    void holderHasExactlyOneDeclarationPoint() {
        List<String> httpSession = holderBeanMethods(HttpSessionAutoConfiguration.class);
        List<String> sessionGuard = holderBeanMethods(SessionGuardAutoConfiguration.class);

        assertEquals(1, httpSession.size(),
                "HttpSessionAutoConfiguration 必须且只能声明一个 SessionStoreHolder，实际：" + httpSession);
        assertTrue(sessionGuard.isEmpty(),
                "SessionGuardAutoConfiguration 不得再声明 SessionStoreHolder（只应通过参数注入消费），实际："
                        + sessionGuard);

        assertEquals(1, httpSession.size() + sessionGuard.size(),
                "sessionStoreHolder 全模块应只有一个声明点；出现两个就是 BeanDefinitionOverrideException 的成因");
    }

    /**
     * 消费方仍然保留 {@code SessionStoreHolder} 形参——「删重复」不等于「删依赖」。
     * <p>
     * {@code sessionGuardDriver(SessionStoreHolder)} 的形参注入必须存在，否则守卫拿不到存储后端。
     */
    @Test
    void sessionGuardStillConsumesHolderViaParameter() {
        boolean consumes = false;
        for (Method method : SessionGuardAutoConfiguration.class.getDeclaredMethods()) {
            if (!AnnotatedElementUtils.hasAnnotation(method,
                    org.springframework.context.annotation.Bean.class)) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (SessionStoreHolder.class.isAssignableFrom(parameterType)) {
                    consumes = true;
                    break;
                }
            }
        }
        assertTrue(consumes,
                "SessionGuardAutoConfiguration 必须通过 @Bean 方法的 SessionStoreHolder 形参消费 holder");
    }

    /**
     * 两个配置都必须带类级 {@code @ConditionalOnClass}。
     * <p>
     * {@code session} 在 springboot 中是 {@code <optional>true</optional>}，两个类的方法签名都直接
     * 引用 {@link SessionStoreHolder}；条件若只挂在方法上，缺模块时内省配置类自身就会先抛
     * {@code NoClassDefFoundError}——缺 session 模块的应用将无法启动。
     */
    @Test
    void bothConfigurationsAreClassLevelGuarded() {
        assertTrue(hasClassLevelConditionalOnClass(HttpSessionAutoConfiguration.class),
                "HttpSessionAutoConfiguration 必须带类级 @ConditionalOnClass（session 是可选模块）");
        assertTrue(hasClassLevelConditionalOnClass(SessionGuardAutoConfiguration.class),
                "SessionGuardAutoConfiguration 必须带类级 @ConditionalOnClass（session 是可选模块）");
    }

    /**
     * 两个配置的类级条件都必须同时限定 {@code ConditionalOnWebApplication(SERVLET)}。
     * <p>
     * holder 基于 {@code HttpSession}，在非 Web 上下文里装配没有意义且容易引发歧义。
     */
    @Test
    void bothConfigurationsRequireServletWebApplication() {
        for (Class<?> type : List.of(HttpSessionAutoConfiguration.class, SessionGuardAutoConfiguration.class)) {
            var annotation = AnnotatedElementUtils.findMergedAnnotation(type,
                    org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.class);
            assertTrue(annotation != null,
                    type.getSimpleName() + " 必须带 @ConditionalOnWebApplication");
            assertEquals(org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET,
                    annotation.type(),
                    type.getSimpleName() + " 的 Web 类型必须是 SERVLET");
        }
    }
}
