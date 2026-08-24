package com.weacsoft.jaravel.vendor.wechat.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义菜单（{@code menu/create} 的 {@code {button: [...]}} 结构）。
 * <p>
 * <pre>
 * Menu menu = new Menu(List.of(
 *     new MenuItem().name("首页").view("https://example.com"),
 *     new MenuItem().name("服务").sub(List.of(
 *         new MenuItem().name("扫码").scancodeWaitmsg("SCAN"),
 *         new MenuItem().name("位置").locationSelect("LOCATION"))),
 *     new MenuItem().name("小程序").miniprogram("wx123", "pages/index/index", null)
 * ));
 * oaService.setMenu(menu);
 * </pre>
 *
 * 官方约束：顶层按钮 ≤3 个；最多两级；子级 ≤5 个（由 {@link MenuItem#sub} 校验）。
 *
 * @author weacsoft
 */
public final class Menu {

    private static final int MAX_TOP_BUTTONS = 3;

    private final List<MenuItem> buttons;

    /**
     * @param buttons 顶层按钮（必填，1~3 个）
     * @throws IllegalArgumentException 按钮为空或超过 3 个时
     */
    public Menu(List<MenuItem> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            throw new IllegalArgumentException("菜单按钮不能为空（1~3 个）");
        }
        if (buttons.size() > MAX_TOP_BUTTONS) {
            throw new IllegalArgumentException("顶层菜单按钮不能超过 " + MAX_TOP_BUTTONS + " 个（当前 " + buttons.size() + "）");
        }
        this.buttons = List.copyOf(buttons);
    }

    /**
     * 便捷构造：两个顶层按钮。
     */
    public Menu(MenuItem first, MenuItem second) {
        this(List.of(first, second));
    }

    /**
     * 便捷构造：三个顶层按钮。
     */
    public Menu(MenuItem first, MenuItem second, MenuItem third) {
        this(List.of(first, second, third));
    }

    /**
     * @return 顶层按钮列表（只读）
     */
    public List<MenuItem> getButtons() {
        return buttons;
    }

    /**
     * 序列化为 {@code menu/create} 请求体（{@code {"button": [...]}}）。
     *
     * @return 请求体 Map
     */
    public Map<String, Object> toJson() {
        List<Map<String, Object>> list = new ArrayList<>(buttons.size());
        for (MenuItem button : buttons) {
            list.add(button.toJson());
        }
        return Map.of("button", list);
    }

    /**
     * 从官方 {@code menu/get} 响应还原（{@code button} 节点缺失时返回空菜单异常）。
     *
     * @param menuNode 响应中的 {@code menu} 节点（含 {@code button} 键）
     * @return 菜单
     * @throws IllegalStateException 缺少 button 数组时
     */
    @SuppressWarnings("unchecked")
    public static Menu fromJsonMap(Map<String, Object> menuNode) {
        Object rawButtons = menuNode == null ? null : menuNode.get("button");
        if (!(rawButtons instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("menu 节点缺少 button 数组");
        }
        List<MenuItem> buttons = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> om) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) om;
                buttons.add(MenuItem.fromJsonMap(m));
            }
        }
        return new Menu(buttons);
    }

    @Override
    public String toString() {
        return "Menu{buttons=" + buttons.size() + "}";
    }
}
