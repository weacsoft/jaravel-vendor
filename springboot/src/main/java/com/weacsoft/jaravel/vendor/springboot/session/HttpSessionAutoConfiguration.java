package com.weacsoft.jaravel.vendor.springboot.session;

import com.weacsoft.jaravel.vendor.session.RegisterSessionStore;
import com.weacsoft.jaravel.vendor.session.SessionStoreHolder;
import com.weacsoft.jaravel.vendor.session.SessionStoreRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * http 模块的 Session 功能自动配置。
 * <p>
 * 负责注册全局 {@link SessionStoreHolder}（默认回退到 {@link com.weacsoft.jaravel.vendor.session.CookieSessionStore}）
 * 与扫描 {@link RegisterSessionStore} 注解的 {@link SessionStoreRegistrar}。
 * <p>
 * 该配置仅在 Servlet Web 应用下生效（Session 基于 {@code HttpSession}）。
 * auth 模块直接复用本配置提供的 {@link SessionStoreHolder}，不强引用具体 Session 实现——
 * 当项目仅引入 core + auth（无 http 的 Session 功能）时，auth 退化为使用原生 {@code HttpSession}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HttpSessionAutoConfiguration {

    /**
     * 全局 Session 存储持有者（默认回退 {@code CookieSessionStore}）。
     * <p>
     * <b>必须 {@code @ConditionalOnMissingBean}</b>：同为可选模块的
     * {@code auth.SessionGuardAutoConfiguration} 也会注册同名 {@code sessionStoreHolder}
     * （当 auth-session / session 都在 classpath 且使用 session 守卫时）。两处都无条件注册会触发
     * {@code BeanDefinitionOverrideException: Invalid bean definition with name 'sessionStoreHolder'}
     * 直接启动失败。加此条件后由先装配者胜出，语义仍是「没有就回退默认」。
     *
     * @return Session 存储持有者
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionStoreHolder sessionStoreHolder() {
        return new SessionStoreHolder();
    }

    /**
     * P3：{@link SessionStoreRegistrar} 为 core 纯扫描器（零 Spring），
     * 扫描时机由下方 SmartInitializingSingleton 触发（保持原「所有单例就绪后扫描」时序）。
     */
    @Bean
    public SessionStoreRegistrar sessionStoreRegistrar(SessionStoreHolder holder) {
        return new SessionStoreRegistrar(holder);
    }

    /**
     * Session 存储注册器扫描触发。
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton sessionStoreRegistrarScanner(
            SessionStoreRegistrar registrar) {
        return registrar::scan;
    }
}
