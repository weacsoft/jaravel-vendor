package com.weacsoft.jaravel.vendor.wechat.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自助菜单 构建 / JSON 序列 / 反序列化 测试。
 */
class MenuTest {

    @Test
    void testClickMenuJson() {
        Menu menu = new Menu(
                new MenuItem().name("首页").click("HOME"),
                new MenuItem().name("关于").click("ABOUT")
        );
        Map<String, Object> json = menu.toJson();
        @SuppressWarnings("unchecked")
        List<?> buttons = (List<?>) json.get("button");
        assertEquals(2, buttons.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) buttons.get(0);
        assertEquals("首页", first.get("name"));
        assertEquals("click", first.get("type"));
        assertEquals("HOME", first.get("key"));
    }

    @Test
    void testViewTypeOmitsKey() {
        Map<String, Object> json = new Menu(List.of(
                new MenuItem().name("资讯").view("https://example.com/news"))).toJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) json.get("button")).get(0);
        assertEquals("view", first.get("type"));
        assertEquals("https://example.com/news", first.get("url"));
        assertFalse(first.containsKey("key"), "view 菜单不含 key 字段");
    }

    @Test
    void testMiniprogramItem() {
        Map<String, Object> json = new Menu(List.of(
                new MenuItem().name("小程序").miniprogram("wxmini123", "pages/a", "https://servicewechat.example")
        )).toJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) json.get("button")).get(0);
        assertEquals("miniprogram", first.get("type"));
        assertEquals("wxmini123", first.get("appid"));
        assertEquals("pages/a", first.get("pagepath"));
        assertEquals("https://servicewechat.example", first.get("url"), "miniprogram 支持降级 url");
    }

    @Test
    void testScancodeAndLocationTypes() {
        Menu menu = new Menu(
                new MenuItem().name("扫码").scancodePush("SCAN_P"),
                new MenuItem().name("位置").locationSelect("LOC")
        );
        Map<String, Object> json = menu.toJson();
        @SuppressWarnings("unchecked")
        List<?> buttons = (List<?>) json.get("button");
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) buttons.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) buttons.get(1);
        assertEquals("scancode_push", a.get("type"));
        assertEquals("SCAN_P", a.get("key"));
        assertEquals("location_select", b.get("type"));
    }

    @Test
    void testSubmenuStructure() {
        Menu menu = new Menu(List.of(new MenuItem().name("服务").click("SERVICES").sub(List.of(
                new MenuItem().name("客服").click("CS"),
                new MenuItem().name("投诉").click("FEEDBACK")
        ))));
        Map<String, Object> json = menu.toJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) json.get("button")).get(0);
        @SuppressWarnings("unchecked")
        List<?> subs = (List<?>) first.get("sub_button");
        assertNotNull(subs, "一级服务菜单应携带 sub_button 列表");
        assertEquals(2, subs.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> sub0 = (Map<String, Object>) subs.get(0);
        assertEquals("CS", sub0.get("key"));
    }

    @Test
    void testMenuItemRequiresName() {
        assertThrows(IllegalStateException.class,
                () -> new Menu(List.of(new MenuItem().click("K"))).toJson(), "未设名称的菜单项序列化必须报错");
    }

    @Test
    void testSubmenuTwoLevelsSerializes() {
        // 两级菜单为合法形态；三级嵌套由微信 menu/create 服务端拒绝（本地不强制，保持构造灵活）
        MenuItem two = new MenuItem().name("服务").click("SVC").sub(List.of(
                new MenuItem().name("客服").click("CS")
        ));
        Menu menu = new Menu(List.of(two));
        Map<String, Object> json = menu.toJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) json.get("button")).get(0);
        assertNotNull(first.get("sub_button"), "两级菜单应正常序列化 sub_button");
    }

    @Test
    void testTopLevelLimitEnforcedByMenu() {
        assertThrows(IllegalArgumentException.class, () -> new Menu(List.of(
                        new MenuItem().name("一").click("1"),
                        new MenuItem().name("二").click("2"),
                        new MenuItem().name("三").click("3"),
                        new MenuItem().name("四").click("4")
                )),
                "顶层菜单按钮超过 3 个应拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> new Menu((List<MenuItem>) null), "空菜单应拒绝");
    }

    @Test
    void testSubLimit() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItem().name("x").sub(List.of(
                        new MenuItem().name("a").click("a"),
                        new MenuItem().name("b").click("b"),
                        new MenuItem().name("c").click("c"),
                        new MenuItem().name("d").click("d"),
                        new MenuItem().name("e").click("e"),
                        new MenuItem().name("f").click("f")
                )),
                "二级菜单超过 5 项应拒绝");
    }

    @Test
    void testRoundtripFromJsonMap() {
        Menu menu = new Menu(
                new MenuItem().name("首页").click("HOME"),
                new MenuItem().name("服务").click("SVC").sub(List.of(new MenuItem().name("子项").click("SUB")))
        );
        Map<String, Object> json = menu.toJson();
        // 反序列化链路（模拟微信 menu/get 返回的 menu 节点 {button:[...]}）
        Menu back = Menu.fromJsonMap(json);
        assertEquals(2, back.getButtons().size());
        assertEquals("首页", back.getButtons().get(0).getName());
        assertEquals("HOME", back.getButtons().get(0).getKey());
        assertEquals(1, back.getButtons().get(1).getSubButtons().size());
        assertEquals("SUB", back.getButtons().get(1).getSubButtons().get(0).getKey());
    }

    @Test
    void testFromJsonMissingButtonThrows() {
        assertThrows(IllegalStateException.class, () -> Menu.fromJsonMap(Map.of()),
                "缺少 button 数组应抛 IllegalStateException");
        assertThrows(IllegalStateException.class, () -> Menu.fromJsonMap(null),
                "null 菜单节点应抛 IllegalStateException");
    }
}
