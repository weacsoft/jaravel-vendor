package com.weacsoft.jaravel.vendor.jblade.view;

import com.weacsoft.jaravel.vendor.core.pagination.Paginator;
import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.core.view.ViewManager;
import com.weacsoft.jaravel.vendor.core.view.ViewProvider;

/**
 * 视图渲染静态门面（对齐 {@code ResponseBuilder} / {@code Auth} 等门面设计）。
 * <p>
 * 业务侧不再手动 {@code setBladeEngine}，而是直接 {@code ViewFacade.getView().render(...)}。
 * 当前激活的 {@link View} 由 {@link ViewManager} 解析（声明 → 配置 → 默认）。
 * 门面在 {@link #bind(ViewManager)} 时，会同时把默认视图注入到 core 标准层的
 * {@link Paginator}，使非模板模块（database）也能借助 core 的 {@link Paginator}
 * 渲染分页，而无须直接依赖 jblade。
 * </p>
 */
public final class ViewFacade {

    private static ViewManager manager;

    private ViewFacade() {
    }

    public static void bind(ViewManager manager) {
        ViewFacade.manager = manager;
        // 把默认视图注入 core 标准层的 Paginator，实现解耦的分页渲染。
        Paginator.setDefaultViewProvider(() -> manager.defaultView());
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
        View view = manager.defaultView();
        if (view == null) {
            throw new IllegalStateException(
                    "[view] 未注册任何 View 实现（应至少有 Blade 兜底），请检查 @RegisterView 或 jblade 依赖");
        }
        return view;
    }

    /**
     * 按名字取得指定 View 实现。
     *
     * @param name 实现名
     * @return View 实现，可能为空
     */
    public static View getView(String name) {
        if (manager == null) {
            return null;
        }
        return manager.get(name);
    }

    public static ViewManager manager() {
        return manager;
    }

    /**
     * 预热全部模板:扫描并编译缓存所有模板类,避免首次访问某页面时现场编译造成卡顿。
     * <p>
     * 由调用方主动触发(启动完成后在 Java 代码里调用,或在模板里用
     * {@code {{ View::preheat() }}} 输出调用),框架不会自动执行。
     *
     * @return 编译成功的模板数量
     */
    public static int preheat() {
        View view = getView();
        if (view instanceof BladeView) {
            return ((BladeView) view).getEngine().preheatTemplates();
        }
        // 其他 View 实现:反射调用 preheatTemplates(存在时),不存在则返回 0
        try {
            java.lang.reflect.Method m = view.getClass().getMethod("preheatTemplates");
            return (Integer) m.invoke(view);
        } catch (Exception e) {
            return 0;
        }
    }
}
