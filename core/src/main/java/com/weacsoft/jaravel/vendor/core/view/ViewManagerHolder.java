package com.weacsoft.jaravel.vendor.core.view;

/**
 * 全局持有「当前激活的」{@link ViewManager}（core 标准层）。
 * <p>
 * 设计目的：让不依赖任何模板引擎的框架代码（如 {@code http} 模块的
 * {@code ResponseBuilder.view()}）仅通过 core 的 {@link View} 契约即可渲染模板，
 * 从而与具体模板引擎模块（{@code jblade} 等）完全解耦——模板引擎模块在装配期
 * 把自己的 {@link ViewManager} 注册进来（「单独注册」），
 * 使用侧无需知道实现是谁提供的。
 * </p>
 * <p>
 * 典型注册时机：jblade 的 {@code ViewAutoConfiguration} 构建完 ViewManager 后调用
 * {@link #set(ViewManager)}；或业务 / 测试代码手动装配时调用。
 * </p>
 *
 * @see View
 * @see ViewManager
 */
public final class ViewManagerHolder {

    private static ViewManager manager;

    private ViewManagerHolder() {
    }

    /**
     * 注册当前激活的 {@link ViewManager}（后注册者覆盖）。
     *
     * @param manager 视图管理者；传 null 清除
     */
    public static void set(ViewManager manager) {
        ViewManagerHolder.manager = manager;
    }

    /**
     * 返回当前激活的 {@link ViewManager}。
     *
     * @return ViewManager；未注册时返回 null
     */
    public static ViewManager get() {
        return manager;
    }

    /**
     * 返回当前默认 {@link View} 实现。
     *
     * @return 默认视图；未注册 ViewManager 或无默认实现时返回 null
     */
    public static View defaultView() {
        ViewManager m = manager;
        return m != null ? m.defaultView() : null;
    }

    /**
     * 清除注册（主要用于测试隔离）。
     */
    public static void clear() {
        ViewManagerHolder.manager = null;
    }
}
