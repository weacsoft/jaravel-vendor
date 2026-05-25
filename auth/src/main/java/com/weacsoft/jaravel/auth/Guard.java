package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.auth.AuthDriver;
import com.weacsoft.jaravel.contract.auth.AuthGuard;
import com.weacsoft.jaravel.contract.auth.AuthProvider;
import com.weacsoft.jaravel.contract.auth.Authenticatable;

/**
 * 认证守卫实现类。
 *
 * <p>Guard 为单例，通过 {@link #initialize} 将请求上下文存入 ThreadLocal，
 * 后续所有方法调用自动使用该上下文，无需显式传递。
 * {@link #destroy} 清理 ThreadLocal 中的上下文和用户缓存。</p>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #initialize()} - 请求开始前调用，初始化驱动，恢复用户</li>
 *   <li>{@link #user()} / {@link #login} / {@link #logout} - 请求处理中调用，自动使用 ThreadLocal context</li>
 *   <li>{@link #destroy()} - 请求结束后调用，清理 ThreadLocal</li>
 * </ol>
 */
public class Guard<T extends Authenticatable> implements AuthGuard<T> {

    private final String name;
    private final AuthDriver driver;
    private final AuthProvider<T, ?> provider;

    /**
     * ThreadLocal 缓存当前请求的用户实体。
     * initialize 时从驱动恢复，login 时设置，logout 时清除，destroy 时清除。
     */
    private final ThreadLocal<T> currentUser = new ThreadLocal<>();

    public Guard(String name, AuthDriver driver, AuthProvider<T, ?> provider) {
        this.name = name;
        this.driver = driver;
        this.provider = provider;
    }


    public String getName() {
        return name;
    }

    @Override
    public AuthDriver getDriver() {
        return driver;
    }

    @Override
    public AuthProvider<T, ?> getProvider() {
        return provider;
    }

    @Override
    public void init() {
        AuthGuard.super.init();
        String id = getDriver().getId();
        if (id != null) {
            T user = getProvider().authById(id);
            currentUser.set(user);
        }
    }

    @Override
    public void destroy() {
        //移除记录的用户
        currentUser.remove();
        AuthGuard.super.destroy();
    }

    @Override
    public boolean login(T user) {
        if (user == null) {
            return false;
        }
        //获得id
        String id = user.getAuthIdentifier();
        //id设置到驱动里
        getDriver().setId(id);
        //设置用户到缓存
        currentUser.set(user);
        return true;
    }

    @Override
    public void logout() {
        //移除掉用户的记录
        getDriver().removeId();
        //移除当前的缓存
        currentUser.remove();
    }

    @Override
    public T user() {
        //获得当前缓存的用户
        return currentUser.get();
    }
}
