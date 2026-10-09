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
 * <b>为什么单独成类且条件写在类上</b>：{@code auth-session} 与 {@code session} 都是<b>可选</b>模块
 * （只依赖 auth 的应用不该被迫带上 session 能力）。只要本类的方法签名引用了它们，
 * Spring 内省本类时就会在缺模块的情况下抛 {@code NoClassDefFoundError} ——
 * 方法级条件保护不了「声明类自身的类加载」，所以条件和类型都必须收在这个类里。
 * <p>
 * 缺失这两个模块时：auth 仍可正常装配（登录态不具备存储后端，由 auth 内置的空守卫兜底），
 * 只是不会注册 {@code session} 驱动。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "com.weacsoft.jaravel.vendor.session.SessionStoreHolder",
        "com.weacsoft.jaravel.vendor.auth.session.SessionGuardDriver"
})
public class SessionGuardAutoConfiguration {

    /**
     * 全局 Session 存储持有者（由 {@code session} 模块提供实现，未注册时回退到 CookieSessionStore）。
     *
     * @return Session 存储持有者
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionStoreHolder sessionStoreHolder() {
        return new SessionStoreHolder();
    }

    /**
     * Session 守卫驱动（工厂模式）：支持 {@code session} 驱动，实际存储由 {@code @RegisterSessionStore}
     * 或 {@code SessionStore} Bean 决定，都没有时回退到 {@code CookieSessionStore}（Servlet HttpSession）。
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