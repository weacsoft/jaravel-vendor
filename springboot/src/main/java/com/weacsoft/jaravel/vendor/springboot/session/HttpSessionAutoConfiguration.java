package com.weacsoft.jaravel.vendor.springboot.session;

import com.weacsoft.jaravel.vendor.session.RegisterSessionStore;
import com.weacsoft.jaravel.vendor.session.SessionStoreHolder;
import com.weacsoft.jaravel.vendor.session.SessionStoreRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Session 功能自动配置（<b>Session 存储的唯一装配点</b>）。
 * <p>
 * 负责注册全局 {@link SessionStoreHolder}（默认回退到 {@link com.weacsoft.jaravel.vendor.session.CookieSessionStore}）
 * 与扫描 {@link RegisterSessionStore} 注解的 {@link SessionStoreRegistrar}。
 * <p>
 * 该配置仅在 Servlet Web 应用下生效（Session 基于 {@code HttpSession}）。
 * <p>
 * <b>为何 {@code sessionStoreHolder} 只在这里定义</b>：{@code session} 是可选模块
 * （springboot 对它是 {@code <optional>true</optional>}），本类的方法签名直接引用了
 * {@link SessionStoreHolder} 等类型，因此必须用<b>类级</b>
 * {@code @ConditionalOnClass} 守住——方法级条件保护不了「声明类自身的类加载」，
 * 缺模块时内省会先抛 {@code NoClassDefFoundError}。
 * <p>
 * 历史上 {@code auth.SessionGuardAutoConfiguration} 曾重复定义同名 {@code sessionStoreHolder}，
 * 两处同时装配会抛 {@code BeanDefinitionOverrideException} 直接启动失败。
 * 职责重复的根因是「同一个 bean 有两个声明点」，已改为：<b>本类独占定义</b>，
 * {@code SessionGuardAutoConfiguration} 只做 {@code @Bean} 参数注入消费。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "com.weacsoft.jaravel.vendor.session.SessionStoreHolder",
        "com.weacsoft.jaravel.vendor.session.SessionStoreRegistrar"
})
public class HttpSessionAutoConfiguration {

    /**
     * 全局 Session 存储持有者（默认回退 {@code CookieSessionStore}）。
     * <p>
     * 保留 {@code @ConditionalOnMissingBean} 是为了让业务方用 {@code @Bean}
     * 自行提供 holder 时能覆盖默认实现（对齐「有则使用，无则回退默认」总原则）。
     *
     * @return Session 存储持有者
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionStoreHolder sessionStoreHolder() {
        return new SessionStoreHolder();
    }

    /**
     * {@link SessionStoreRegistrar} 为 core 纯扫描器（零 Spring），
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
