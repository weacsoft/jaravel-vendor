package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuardDriver;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;

import java.util.Map;

/**
 * 空守卫驱动：auth 标准自带的<b>兜底驱动</b>，创建 {@link NullGuard}（恒视为未登录）。
 * <p>
 * <b>为什么需要它</b>：auth 是「认证标准」，不应因为应用没有引入任何具体守卫实现
 * （如 {@code auth-session} / {@code jwt}）就抛 {@code IllegalStateException} 而无法装配。
 * 有了它，「只依赖 auth」的组合语义是：认证能力已就绪，但没有可用登录态（{@code check()} 恒 false）。
 * <p>
 * <b>为什么 {@link #support(String)} 刻意返回 false</b>：若返回 true，它会与 session/jwt 等真实驱动
 * 争抢匹配（AuthManager 取第一个 support 的驱动），可能把守卫创建成空实现。
 * 因此它只在「遍历完所有驱动都没匹配」时由 AuthManager 显式兜底使用。
 */
public class NullGuardDriver implements AuthGuardDriver {

    @Override
    public boolean support(String driver) {
        return false;
    }

    @Override
    public AuthGuard create(String name, UserProvider provider, Map<String, Object> config) {
        return new NullGuard(name);
    }
}