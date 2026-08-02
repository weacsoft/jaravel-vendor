package com.weacsoft.jaravel.vendor.jblade.view;

import java.util.Map;

/**
 * 视图渲染抽象（对齐 Laravel 的 View 契约）。
 * <p>
 * 从 Jaravel 框架视角看，模板引擎只是 {@code View} 的一种实现。框架只依赖本接口，
 * 不关心底层是 Blade、Twig 还是纯字符串。业务侧通过 {@link ViewFacade#getView()} 拿到当前激活的
 * 实现即可渲染模板，无需手动装配引擎。
 * </p>
 * <p>
 * 一个实现是否需要被启用，由 {@code @RegisterView} 声明 + 配置选择 + 兜底默认三者决定
 * （见 {@code ViewManager}）：
 * <ol>
 *   <li><b>声明</b>：在任意 {@code @Configuration} 类或 {@code @Component} 上标注 {@link RegisterView}，
 *       提供 {@code View} 实现实例；</li>
 *   <li><b>配置</b>：{@code jaravel.view.default=xxx} 选择默认激活哪一个实现（按声明名）；</li>
 *   <li><b>默认</b>：没有任何声明时，由 {@code ViewAutoConfiguration} 兜底注册 Blade 实现。</li>
 * </ol>
 */
public interface View {

    /**
     * 使用给定数据渲染模板。
     *
     * @param name   模板名（不含后缀）
     * @param data   渲染变量
     * @return 渲染后的 HTML 字符串
     * @throws Exception 渲染失败
     */
    String render(String name, Map<String, Object> data) throws Exception;

    /**
     * 渲染模板（无数据）。
     *
     * @param name 模板名（不含后缀）
     * @return 渲染后的 HTML 字符串
     * @throws Exception 渲染失败
     */
    default String render(String name) throws Exception {
        return render(name, java.util.Collections.emptyMap());
    }

    /**
     * 判断模板是否存在。
     * <p>
     * 用于「有则渲染、无则跳过」的场景（例如分页器在没有自定义模板时优雅降级）。
     * 默认返回 {@code false}，由具体实现按自身查找规则覆写。
     * </p>
     *
     * @param name 模板名（不含后缀，支持点号分隔）
     * @return 存在返回 true
     */
    default boolean exists(String name) {
        return false;
    }

    /**
     * 该实现对外暴露的名字（对应 {@link RegisterView#name()}）。
     *
     * @return 实现名
     */
    String name();
}
