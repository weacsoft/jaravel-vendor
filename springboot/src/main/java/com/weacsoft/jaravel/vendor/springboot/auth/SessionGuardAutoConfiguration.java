package com.weacsoft.jaravel.vendor.springboot.auth;

import com.weacsoft.jaravel.vendor.auth.session.SessionGuardDriver;
import com.weacsoft.jaravel.vendor.session.SessionStoreHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Session 守卫装配：把「用 Session 保存登录态」这一种实现接到 auth 标准上。
 * <p>
 * <b>单独成类且条件写在类上的原因</b>：{@code auth-session} 与 {@code session} 都是<b>可选</b>模块
 * （只依赖 auth 的应用不应被迫带上 session 能力）。方法级条件保护不了「声明类自身的类加载」——
 * 方法签名引用缺模块类型时 Spring 内省即抛 {@code NoClassDefFoundError}，
 * 所以条件与类型引用都必须收在这个类里。
 * <p>
 * 缺失这两个模块时：auth 仍可正常装配（登录态不具备存储后端，由 auth 内置的空守卫回退），
 * 只是不会注册 {@code session} 驱动。
 * <p>
 * <b>本类只消费、不定义 {@link SessionStoreHolder}</b>：holder 的唯一声明点是
 * {@code springboot.session.HttpSessionAutoConfiguration}——同一 bean 只留一个声明点，
 * 否则同名 bean 重复装配会抛 {@code BeanDefinitionOverrideException} 导致启动失败；
 * 这里以 {@code @Bean} 参数注入方式消费。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "com.weacsoft.jaravel.vendor.session.SessionStoreHolder",
        "com.weacsoft.jaravel.vendor.auth.session.SessionGuardDriver"
})
public class SessionGuardAutoConfiguration {

    /**
     * Session 守卫驱动（工厂模式）：支持 {@code session} 驱动，实际存储由 {@code @RegisterSessionStore}
     * 或 {@code SessionStore} Bean 决定，都没有时回退到 {@code CookieSessionStore}（Servlet HttpSession）。
     * <p>
     * {@code holder} 由 {@code HttpSessionAutoConfiguration} 提供（若该配置因缺 {@code session}
     * 模块未装配，本类也因类级 {@code @ConditionalOnClass} 不加载，不会出现注入失败）。
     *
     * @param holder Session 存储持有者
     * @return Session 守卫驱动
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnSessionGuardDriverCondition.class)
    public SessionGuardDriver sessionGuardDriver(SessionStoreHolder holder) {
        return new SessionGuardDriver(holder);
    }
}