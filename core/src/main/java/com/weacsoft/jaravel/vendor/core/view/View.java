package com.weacsoft.jaravel.vendor.core.view;

import java.util.Map;

/**
 * 视图渲染标准接口（core 标准层）。
 * <p>
 * 框架（如 database 模块的 {@code Paginator.links()}）只依赖本接口渲染视图，
 * 而不依赖具体模板引擎（jblade 的 {@code BladeView} 是本接口的一种实现）。
 * 这样数据库/分页等模块无需硬依赖 jblade。
 * </p>
 *
 * @see ViewManager
 */
public interface View {

    /**
     * 渲染指定模板。
     *
     * @param templateName 模板名（点号命名空间，如 {@code layouts.mdui.pageinator}）
     * @param data         渲染数据
     * @return 渲染后的字符串
     * @throws Exception 渲染失败
     */
    String render(String templateName, Map<String, Object> data) throws Exception;

    /**
     * 判断模板是否存在。
     *
     * @param templateName 模板名
     * @return 是否存在
     */
    default boolean exists(String templateName) {
        return false;
    }

    /**
     * 视图实现名（唯一标识，如 {@code blade}）。
     *
     * @return 实现名
     */
    String name();
}
