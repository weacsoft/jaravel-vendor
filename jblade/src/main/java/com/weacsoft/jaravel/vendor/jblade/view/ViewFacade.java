package com.weacsoft.jaravel.vendor.jblade.view;

import java.util.Optional;

/**
 * 视图渲染静态门面（对齐 {@code ResponseBuilder} / {@code Auth} 等门面设计）。
 * <p>
 * 业务侧不再手动 {@code setBladeEngine}，而是直接 {@code ViewFacade.getView().render(...)}。
 * 当前激活的 {@link View} 由 {@link ViewManager} 解析（声明 → 配置 → 默认）。
 * </p>
 */
public final class ViewFacade {

    private static ViewManager manager;

    private ViewFacade() {
    }

    public static void bind(ViewManager manager) {
        ViewFacade.manager = manager;
    }

    /**
     * 取得当前激活的 View 实现。
     *
     * @return View 实现；无任何实现时抛错（应保证兜底已注册）
     * @throws IllegalStateException 若未注册任何 View 实现
     */
    public static View getView() {
        if (manager == null) {
            throw new IllegalStateException("[view] ViewManager 尚未初始化，无法获取 View 实现");
        }
        Optional<View> view = manager.defaultView();
        if (!view.isPresent()) {
            throw new IllegalStateException(
                    "[view] 未注册任何 View 实现（应至少有 Blade 兜底），请检查 @RegisterView 或 jblade 依赖");
        }
        return view.get();
    }

    /**
     * 按名字取得指定 View 实现。
     *
     * @param name 实现名
     * @return View 实现，可能为空
     */
    public static Optional<View> getView(String name) {
        if (manager == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(manager.get(name));
    }

    public static ViewManager manager() {
        return manager;
    }
}
