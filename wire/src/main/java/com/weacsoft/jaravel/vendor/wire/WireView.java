package com.weacsoft.jaravel.vendor.wire;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire 视图配置(链式 API),对应 Laravel Livewire 的 render() 返回值。
 * <p>
 * 用户在 {@code WireController.render()} 中返回此对象,声明要渲染的模板及布局配置。
 * 支持两种布局模式:
 * <ul>
 *   <li>{@code layout()} —— 组件式布局(父模板用 {{ $slot }} 占位,对应 Livewire ->layout())</li>
 *   <li>{@code extends()} + {@code section()} —— 传统 Blade 布局(父模板用 @yield 占位,对应 Livewire ->extends() + ->section())</li>
 * </ul>
 * 两种模式二选一,不可同时使用。未指定任何布局时默认不套布局。
 * <p>
 * 链式 API 示例:
 * <pre>{@code
 * protected WireView render() {
 *     return wireView("mdui.admin.admin.item")
 *             .bladeExtends("layouts.mdui.form")
 *             .section("body")
 *             .with(Map.of("roles", AdminRole.query().get().toObjectList()))
 *             .title("新增管理员");
 * }
 * }</pre>
 */
public class WireView {

    private final String templateName;
    private String layout;              // ->layout() 组件式布局
    private String extendsTemplate;     // ->extends() 传统布局
    private String section = "content"; // ->section() @section 名,默认 content
    private String title;
    private final Map<String, Object> withData;

    public WireView(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            throw new IllegalArgumentException("模板名不能为空");
        }
        this.templateName = templateName;
        this.withData = new LinkedHashMap<>();
    }

    /** 组件式布局 ->layout()。与 extends() 二选一。 */
    public WireView layout(String layout) {
        this.layout = layout;
        return this;
    }

    /** 传统 Blade 布局 ->extends()。与 layout() 二选一。方法名使用 bladeExtends 以避开 Java 关键字。 */
    public WireView bladeExtends(String template) {
        this.extendsTemplate = template;
        return this;
    }

    /** 指定 @section 名 ->section()。与 extends() 配合使用。 */
    public WireView section(String name) {
        this.section = name;
        return this;
    }

    /** 附加数据 ->with()。 */
    public WireView with(Map<String, Object> data) {
        if (data != null) withData.putAll(data);
        return this;
    }

    /** 附加数据 ->with()。 */
    public WireView with(String key, Object value) {
        if (key != null) withData.put(key, value);
        return this;
    }

    /** 页面标题 ->title()。 */
    public WireView title(String title) {
        this.title = title;
        return this;
    }

    public String getTemplateName() { return templateName; }
    public String getLayout() { return layout; }
    public String getExtendsTemplate() { return extendsTemplate; }
    public String getSection() { return section; }
    public String getTitle() { return title; }
    public Map<String, Object> getWithData() { return withData; }

    /**
     * 合并 withData 和 properties 到统一渲染数据。
     *
     * @param properties WireController 的 public 属性集合
     * @return 最终渲染数据(with 优先)
     */
    public Map<String, Object> getMergedData(Map<String, Object> properties) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (properties != null) merged.putAll(properties);
        merged.putAll(withData);
        if (title != null) merged.put("title", title);
        return merged;
    }
}