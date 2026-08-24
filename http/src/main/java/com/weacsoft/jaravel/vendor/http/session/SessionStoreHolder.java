package com.weacsoft.jaravel.vendor.http.session;

/**
 * 全局 Session 存储持有者，解决「注册时机」与「使用时机」的先后问题。
 * <p>
 * 认证驱动在 Bean 创建阶段就需要 {@link SessionStore}，
 * 而 {@link RegisterSessionStore} 注解要等到所有单例初始化完成后才扫描得到。
 * 因此注入本持有者而非具体实现：驱动持有 holder 的引用，
 * 实际调用时才通过 {@link #get()} 取出真正的存储实例。
 *
 * <h3>回退策略</h3>
 * 若扫描结束后仍未设置任何实现，{@link #get()} 会惰性回退到
 * {@link CookieSessionStore}（基于 Servlet HttpSession），保证开箱即用。
 */
public class SessionStoreHolder implements SessionStore {

    private volatile SessionStore delegate;

    /**
     * 设置实际使用的 Session 存储实现。
     *
     * @param delegate 存储实现，不可为 null
     */
    public void set(SessionStore delegate) {
        this.delegate = delegate;
    }

    /**
     * 是否已设置具体实现。
     */
    public boolean isPresent() {
        return delegate != null;
    }

    /**
     * 获取实际的 Session 存储；未设置时惰性回退到 {@link CookieSessionStore}。
     */
    public SessionStore get() {
        SessionStore current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = new CookieSessionStore();
                    delegate = current;
                }
            }
        }
        return current;
    }

    @Override
    public Object get(String key) {
        return get().get(key);
    }

    @Override
    public void put(String key, Object value) {
        get().put(key, value);
    }

    @Override
    public void remove(String key) {
        get().remove(key);
    }

    @Override
    public void destroy() {
        get().destroy();
    }
}
