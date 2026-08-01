package com.weacsoft.jaravel.vendor.jwt.autoconfigure;

import com.weacsoft.jaravel.vendor.core.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> JWT 作为守卫驱动时才装配 {@code JwtGuardDriver}。
 *
 * <h3>命中条件</h3>
 * 以下任一情况装配：
 * <ul>
 *   <li>{@code jaravel.auth.guards.*.driver} 取值为 {@code jwt}；</li>
 *   <li>存在 {@code @RegisterGuard(driver = "jwt")} 注解声明。</li>
 * </ul>
 *
 * <h3>为什么严格按需（不认缺省）</h3>
 * JWT 是<b>额外实现</b>，不在任何兜底列表中。只有用户显式写 {@code driver: jwt} 才装配，
 * 缺省（未写 driver）应回退到 {@code session} 而非 JWT。因此本条件<b>不认缺省</b>。
 *
 * @see OnDriverInUseCondition
 */
public class OnJwtGuardDriverCondition extends OnDriverInUseCondition {

    public OnJwtGuardDriverCondition() {
        super("jwt", "jaravel.auth.guards.", ".driver");
    }
}
