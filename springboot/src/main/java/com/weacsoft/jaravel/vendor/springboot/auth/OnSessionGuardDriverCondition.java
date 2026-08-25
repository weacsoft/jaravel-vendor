package com.weacsoft.jaravel.vendor.springboot.auth;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式（或缺省）选用</b> Session 作为守卫驱动时才装配 {@code SessionGuardDriver}。
 *
 * <h3>命中条件</h3>
 * 以下任一情况装配：
 * <ul>
 *   <li>{@code jaravel.auth.guards.*.driver} 取值为 {@code session}；</li>
 *   <li>配置了某个 guard 但<b>未写 driver</b>（缺省回退到 session 兜底）。</li>
 * </ul>
 *
 * <h3>为什么 session 认缺省</h3>
 * 遵循 vendor 模块组的统一兜底原则：用户写了 {@code jaravel.auth.guards} 但没写具体 driver 时，
 * 用最基础的 {@code session} 守卫保证功能基本可用。因此本条件对缺省（未配置 driver）视为命中，
 * 使 {@code SessionGuardDriver} 始终被注册；非默认的驱动（如 {@code jwt}）则严格按需、
 * 不认缺省。
 *
 * <h3>别名分组</h3>
 * guard 的 {@code driver} / {@code provider} 传入字符串时，运行时按 support 匹配对应驱动对象；
 * provider 同名冲突时由 {@code @RegisterProvider} 的具名别名解决（见 {@code AuthManager}）。
 *
 * @see OnDriverInUseCondition
 */
public class OnSessionGuardDriverCondition extends OnDriverInUseCondition {

    public OnSessionGuardDriverCondition() {
        super("session", "jaravel.auth.guards.", ".driver");
        matchIfAbsent();
    }
}
