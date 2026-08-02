package com.weacsoft.jaravel.vendor.jblade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 @component / @slot / @foreach 指令的完整行为。
 */
class ComponentSlotTest {

    private String render(String name, Map<String, Object> data) throws Exception {
        return new BladeEngine("templates").render(name, data);
    }

    private Map<String, Object> sampleData() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> users = new ArrayList<>();
        Map<String, Object> u1 = new LinkedHashMap<>();
        u1.put("name", "Alice");
        u1.put("age", 30);
        users.add(u1);
        Map<String, Object> u2 = new LinkedHashMap<>();
        u2.put("name", "Bob");
        u2.put("age", 25);
        users.add(u2);
        data.put("users", users);
        return data;
    }

    /** @component 应能接收数据并把主体内容注入 $slot。 */
    @Test
    void componentReceivesDataAndDefaultSlot() throws Exception {
        String out = render("comp-usage", sampleData());
        assertTrue(out.contains("alert alert-warning"), "组件应收到 type 变量: " + out);
        assertTrue(out.contains("注意"), "组件应收到 title 变量: " + out);
        assertTrue(out.contains("这是提示内容"), "组件主体应注入 $slot: " + out);
    }

    /** @slot 具名插槽应分别注入对应变量。 */
    @Test
    void namedSlotsAreInjected() throws Exception {
        String out = render("comp-usage", sampleData());
        assertTrue(out.contains("卡片标题"), "具名 slot header 应生效: " + out);
        assertTrue(out.contains("卡片脚注"), "具名 slot footer 应生效: " + out);
        assertTrue(out.contains("卡片主体"), "默认 slot 应生效: " + out);
    }

    /** @foreach 应能遍历 List<Map> 并支持下标取值。 */
    @Test
    void foreachIteratesListOfMaps() throws Exception {
        String out = render("comp-usage", sampleData());
        assertTrue(out.contains("USER:Alice-30;"), "@foreach 第一条: " + out);
        assertTrue(out.contains("USER:Bob-25;"), "@foreach 第二条: " + out);
    }

    /** @foreach 遍历空集合时不应输出内容，也不应报错。 */
    @Test
    void foreachHandlesEmptyCollection() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("users", new ArrayList<Map<String, Object>>());
        String out = render("comp-usage", data);
        assertFalse(out.contains("USER:"), "空集合不应产生行: " + out);
    }

    /** @foreach 应支持普通 List。 */
    @Test
    void foreachIteratesSimpleList() throws Exception {
        Map<String, Object> data = sampleData();
        data.put("nums", Arrays.asList(1, 2, 3));
        String out = render("comp-usage", data);
        assertTrue(out.contains("USER:Alice-30;"));
    }
}
