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

    /**
     * 回归：插槽内容是“已经渲染好的 HTML 片段”，组件内 {{ $slot }} 不应再次 HTML 转义（双重编码）。
     * 同时验证：用户通过 @component 显式传入的数据（如 $data）仍是普通值，{{ $data }} 必须被正常转义。
     * 这与 PHP Blade 语义一致：插槽为 HtmlString（Htmlable），数据为非 Htmlable 原值。
     */
    @Test
    void testSlotIsNotDoubleEscapedButDataIsEscaped() throws Exception {
        String html = render("comp-raw-usage", Map.of());

        assertTrue(html.contains("<p>slot html</p>"),
                "插槽内 HTML 应原样输出，不得二次编码: " + html);
        assertFalse(html.contains("&lt;p&gt;slot html&lt;/p&gt;"),
                "插槽内 HTML 不应被双重转义: " + html);

        assertTrue(html.contains("DATA:[&lt;b&gt;data&lt;/b&gt;]"),
                "用户显式传入的数据应被正常 HTML 转义: " + html);
    }

    /** @slot 支持第二个参数：标量表达式直接赋值，不含多余空白字符 */
    @Test
    void slotWithExpressionValue() throws Exception {
        Map<String, Object> data = Map.of("searchValue", "测试");
        String out = render("comp-slot-expr-usage", data);
        assertTrue(out.contains("测试"), "标量表达式应被正确传递: " + out);
        assertFalse(out.contains(" |"), "value 不应包含多余空白: " + out);
    }

    /** @slot 第二个参数为空表达式时输出空字符串 */
    @Test
    void slotWithEmptyExpression() throws Exception {
        String out = render("comp-slot-expr-usage", Map.of());
        assertTrue(out.contains("<div class=\"slot-val\"></div>"), "空值应输出空字符串: " + out);
    }

    /** @slot 多参数形式与块形式可共存 */
    @Test
    void mixedSlotSyntax() throws Exception {
        Map<String, Object> data = Map.of("title", "标题值", "content", "内容值");
        String out = render("comp-slot-mixed-usage", data);
        assertTrue(out.contains("标题值"), "标量 slot 应输出: " + out);
        assertTrue(out.contains("内容值"), "标量 slot 应输出: " + out);
    }
}
