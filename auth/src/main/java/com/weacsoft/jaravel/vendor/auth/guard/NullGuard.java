package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;

/**
 * 空守卫：auth 标准自带的兜底实现，<b>永远视为未登录</b>。
 * <p>
 * <b>为什么要它</b>：auth 是「认证标准」，不应绑定任何具体存储实现（session / jwt / token…）。
 * 当应用只引入 auth、没有引入任何守卫实现（如 {@code auth-session}）时，
 * 若仍按「找不到驱动就抛异常」处理，auth 就无法独立装配。空守卫让这种组合的语义变成
 * 「认证能力就绪，但没有任何可用登录态」：{@code check()} 恒为 false，登录/登出为空操作。
 * <p>
 * 需要真实登录态时引入具体实现模块（如 {@code auth-session}），其驱动会覆盖本兜底。
 */
public class NullGuard implements AuthGuard {

    /** 守卫名（仅用于日志/诊断；AuthGuard 契约本身不含 name） */
    private final String name;

    /**
     * @param name 守卫名
     */
    public NullGuard(String name) {
        this.name = name;
    }

    /**
     * @return 守卫名
     */
    public String name() {
        return name;
    }

    @Override
    public boolean check() {
        return false;
    }

    @Override
    public boolean guest() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Authenticatable> T user() {
        return null;
    }

    @Override
    public void login(Authenticatable user) {
        // 空守卫不保存任何登录态：请引入 auth-session（或自定义守卫驱动）后使用
    }

    @Override
    public void logout() {
        // 无登录态可清理
    }
}