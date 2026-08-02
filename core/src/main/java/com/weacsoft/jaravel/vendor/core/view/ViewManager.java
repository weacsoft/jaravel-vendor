package com.weacsoft.jaravel.vendor.core.view;

import java.util.List;

/**
 * 视图管理者标准接口（core 标准层）。
 * <p>
 * 负责注册多个 {@link View} 实现、按名查找、解析默认实现。
 * jblade 的 {@code com.weacsoft.jaravel.vendor.jblade.view.ViewManager}
 * 实现了本接口；core 的 {@link Paginator} 仅依赖本接口渲染分页模板，不直接耦合模板引擎。
 * </p>
 */
public interface ViewManager {

    /**
     * 注册一个视图实现。
     *
     * @param view 视图实现
     */
    void register(View view);

    /**
     * 按实现名查找视图。
     *
     * @param name 实现名
     * @return 视图，未找到返回 null
     */
    View get(String name);

    /**
     * 返回当前默认视图实现。
     *
     * @return 默认视图，未配置返回 null
     */
    View defaultView();

    /**
     * 设置配置层默认实现名（来自 {@code jaravel.view.default}）。
     *
     * @param name 实现名
     */
    void setConfiguredDefault(String name);

    /**
     * 设置声明层默认实现名（来自 {@code @RegisterView(defaultView = true)}）。
     *
     * @param name 实现名
     */
    void setAnnotatedDefault(String name);

    /**
     * 返回所有已注册的视图实现名。
     *
     * @return 视图名列表
     */
    List<String> names();
}
