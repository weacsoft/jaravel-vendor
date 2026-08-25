package com.weacsoft.jaravel.vendor.core.lookup;

/**
 * 全局查找的安装点（零 Spring）。
 * <p>
 * <h3>生命周期</h3>
 * <ol>
 *   <li>宿主启动时 {@link #install(GlobalBeanProvider)} 安装一次
 *       （Spring 应用由 springboot 模块的 {@code CoreSpringConfiguration} 完成；
 *       非 Spring 宿主手动安装一个 Map 版实现即可）；</li>
 *   <li>core 静态门面（{@code SpringContext} / {@code Facade} / {@code Application.make}）
 *       与注册器扫描在执行时读取此处；</li>
 *   <li>{@link #uninstall()} 复位为未安装状态（测试与进程内切换宿主使用）。</li>
 * </ol>
 * <p>
 * <h3>未安装时的行为</h3>
 * 抛带引导信息的 {@link IllegalStateException}（与既有「SpringContext 尚未初始化」语义一致），
 * 空安全路径（{@code beanOrNull} / {@code make(String)}）则返回 {@code null} 让调用方降级。
 */
public final class GlobalLookup {

    private static volatile GlobalBeanProvider provider;

    private GlobalLookup() {
    }

    /**
     * 安装全局 Bean 提供者（覆盖既有安装）。
     *
     * @param next 提供者实现；不允许 {@code null}
     */
    public static void install(GlobalBeanProvider next) {
        if (next == null) {
            throw new IllegalArgumentException("GlobalBeanProvider 不能为 null，请使用 uninstall() 复位");
        }
        provider = next;
    }

    /** 复位为未安装状态。 */
    public static void uninstall() {
        provider = null;
    }

    /**
     * 取当前安装的提供者，未安装返回 {@code null}（空安全路径使用）。
     *
     * @return 已安装提供者，或 {@code null}
     */
    public static GlobalBeanProvider getIfInstalled() {
        return provider;
    }

    /**
     * 取当前安装的提供者，未安装时抛出带引导信息的异常（强依赖路径使用）。
     *
     * @return 已安装提供者
     * @throws IllegalStateException 未安装时
     */
    public static GlobalBeanProvider require() {
        GlobalBeanProvider p = provider;
        if (p == null) {
            throw new IllegalStateException("SpringContext 尚未初始化：GlobalBeanProvider 未安装。"
                    + "Spring 应用请确认 jaravel 核心自动装配（jaravel-springboot / jaravel-starter）已生效；"
                    + "非 Spring 宿主请先调用 GlobalLookup.install(...) 安装一个 Bean 提供者。");
        }
        return p;
    }
}
