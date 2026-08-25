package com.weacsoft.jaravel.vendor.http.session;

import com.weacsoft.jaravel.vendor.core.registrar.SingletonRegistrar;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * 扫描 {@link RegisterSessionStore @RegisterSessionStore} 注解方法，
 * 注册全局唯一的 {@link SessionStore}。
 * <p>
 * 继承 {@link SingletonRegistrar}，天然具备<b>唯一性约束</b>：
 * 存在多个注册项时启动报错，除非其中一个显式声明 {@code override = true}。
 *
 * <h3>解析优先级（从高到低）</h3>
 * <ol>
 *   <li>{@code @RegisterSessionStore(override = true)} 注解方法</li>
 *   <li>{@code @RegisterSessionStore} 注解方法</li>
 *   <li>容器中已有的 {@link SessionStore} Bean（兼容旧的 {@code @Bean} 写法）</li>
 *   <li>回退到 {@link CookieSessionStore}（Servlet HttpSession）</li>
 * </ol>
 */
public class SessionStoreRegistrar extends SingletonRegistrar<RegisterSessionStore, SessionStore> {

    private final SessionStoreHolder holder;

    public SessionStoreRegistrar(SessionStoreHolder holder) {
        super(RegisterSessionStore.class, SessionStore.class);
        this.holder = holder;
    }

    @Override
    protected boolean isOverride(RegisterSessionStore annotation) {
        return annotation.override();
    }

    /**
     * 应用注解扫描到的唯一 Session 存储。
     */
    @Override
    protected void apply(SessionStore instance) {
        holder.set(instance);
        log.info("[http] Session 存储: {}（@RegisterSessionStore）",
                instance.getClass().getSimpleName());
    }

    /**
     * 未使用注解时的回退：优先复用容器中已有的 {@link SessionStore} Bean，
     * 否则使用 {@link CookieSessionStore}。
     */
    @Override
    protected void applyFallback() {
        SessionStore fromBean = resolveFromBeans();
        if (fromBean != null) {
            holder.set(fromBean);
            log.info("[http] Session 存储: {}（Spring Bean）", fromBean.getClass().getSimpleName());
            return;
        }
        holder.set(new CookieSessionStore());
        log.info("[http] Session 存储: CookieSessionStore（默认回退）");
    }

    /**
     * 查找容器中的 {@link SessionStore} Bean，排除 holder 自身以避免自引用。
     */
    private SessionStore resolveFromBeans() {
        Map<String, SessionStore> beans = lookup().beansOfType(SessionStore.class);
        for (SessionStore candidate : beans.values()) {
            if (candidate != holder && !(candidate instanceof SessionStoreHolder)) {
                return candidate;
            }
        }
        return null;
    }
}
