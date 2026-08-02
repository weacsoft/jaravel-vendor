package com.weacsoft.jaravel.vendor.core.view;

/**
 * 默认视图提供者（core 标准层的函数式接口）。
 * <p>
 * 用于解耦 {@link Paginator} 与具体模板引擎：Paginator 不直接依赖任何 {@code ViewManager} 实现，
 * 而由模板引擎在启动时通过 {@link Paginator#setDefaultViewProvider(ViewProvider)} 注入默认 {@link View}。
 * 未注入时 {@code links()} 降级为空串（与「无分页视图则等同于未执行」的语义一致）。
 * </p>
 */
@FunctionalInterface
public interface ViewProvider {

    /**
     * 返回当前默认视图实现；无可用视图时返回 null。
     *
     * @return 默认视图或 null
     */
    View getDefaultView();
}
