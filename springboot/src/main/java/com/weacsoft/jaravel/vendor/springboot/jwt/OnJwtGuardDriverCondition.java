package com.weacsoft.jaravel.vendor.springboot.jwt;

import com.weacsoft.jaravel.vendor.springboot.condition.OnDriverInUseCondition;

/**
 * 仅当<b>显式选用</b> JWT 作为守卫驱动时才装配 {@code JwtGuardDriver}。
 *
 * <h3>命中条件</h3>
 * 以下任一情况装配：
 * <ul>
 *   <li>{@code jaravel.auth.guards.*.driver} 取值为 {@code jwt}（YAML/属性显式配置）；</li>
 *   <li>应用中存在被 {@code @RegisterGuard} 标注、且方法体返回 {@code GuardDefinition.of("jwt", ...)}
 *       的声明式守卫注册（驱动名写在方法返回值里，属性检查读不到，故通过注解存在性宽松判定）。</li>
 * </ul>
 * <p>
 * 注意：{@code @RegisterGuard} 注解本身<b>没有</b> {@code driver} 属性，驱动名只存在于
 * 返回值 {@code GuardDefinition.of("jwt", provider)} 中，因此本条件不能靠读取注解属性判定，
 * 只能靠「存在被 {@code @RegisterGuard} 标注的方法」这一事实来装配（详见
 * {@link OnDriverInUseCondition#matchIfDeclaredBy(String...)}）。
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
        matchIfDeclaredBy("com.weacsoft.jaravel.vendor.auth.RegisterGuard");
    }
}
