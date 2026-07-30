package com.weacsoft.jaravel.vendor.jblade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * jblade 功能测试：
 * 1. 多重（不限层级）模板继承 + @parent / @show
 * 2. 内置指令全集（条件、循环、$loop、@php、@include、@verbatim 等）
 * 3. 动态函数（BladeFunctions）与动态指令（BladeDirectives）加载
 */
class BladeFeatureTest {

    @AfterEach
    void tearDown() {
        BladeFunctions.clear();
        BladeDirectives.clear();
    }

    /**
     * 三层继承：child -> middle -> base。
     * 验证：
     * - title 由最底层子模板覆盖（子级优先）
     * - sidebar 经 @parent 合并，输出顺序 BASE -> MIDDLE -> CHILD
     * - content 定义在 child，正常注入 base 的 @yield
     */
    @Test
    void testMultiLevelInheritance() throws Exception {
        BladeEngine engine = new BladeEngine("templates");

        String html = engine.render("inherit.child", Map.of("name", "World"));

        assertTrue(html.contains("ChildTitle"), "title 应被子模板覆盖为 ChildTitle");
        assertFalse(html.contains("MiddleTitle"), "中间层 title 不应生效");

        int base = html.indexOf("BASE");
        int middle = html.indexOf("MIDDLE");
        int child = html.indexOf("CHILD");
        assertTrue(base >= 0 && middle >= 0 && child >= 0, "sidebar 三层内容都应存在: " + html);
        assertTrue(base < middle && middle < child, "@parent 合并顺序应为 BASE < MIDDLE < CHILD: " + html);

        assertTrue(html.contains("Hello World"), "content 应注入 base 布局");
        assertFalse(html.contains("@__jblade_parent__@"), "不应残留 @parent 占位符");
    }

    /**
     * 两层继承（原有用例回归）：page -> layout。
     */
    @Test
    void testSingleLevelInheritanceRegression() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        String html = engine.render("page", Map.of("name", "Charlie"));
        assertTrue(html.contains("My Page"));
        assertTrue(html.contains("Welcome, Charlie!"));
    }

    /**
     * 内置指令全集测试。
     */
    @Test
    void testDirectives() throws Exception {
        BladeEngine engine = new BladeEngine("templates");

        Map<String, Object> vars = new HashMap<>();
        vars.put("score", 75);
        vars.put("isAdmin", false);
        vars.put("definedVar", "yes");
        vars.put("emptyList", List.of());
        vars.put("items", List.of("a", "b", "c"));
        vars.put("none", List.of());
        vars.put("rawHtml", "<b>bold</b>");

        String html = engine.render("directives", vars);

        assertFalse(html.contains("SECRET_COMMENT"), "Blade 注释不应输出");
        assertTrue(html.contains("GRADE:B"), "@if/@elseif 应输出 B: " + html);
        assertTrue(html.contains("NOT_ADMIN"), "@unless 应生效");
        assertTrue(html.contains("ISSET_OK"), "@isset 应生效");
        assertTrue(html.contains("EMPTY_OK"), "@empty(expr) 应生效");
        assertTrue(html.contains("LOOP:1=a,2=b,3=c"), "$loop->iteration/$loop->last 应正确: " + html);
        assertTrue(html.contains("FORELSE:NO_ITEMS"), "@forelse 空集合应走 @empty 分支");
        assertTrue(html.contains("FOR:[0][1][2]"), "@for 循环应输出 [0][1][2]");
        assertTrue(html.contains("X=15"), "@php 块内变量运算应生效: " + html);
        assertTrue(html.contains("VERB:{{ raw }}"), "@verbatim 应原样输出");
        assertTrue(html.contains("AT:@literal"), "@@ 应转义为 @");
        assertTrue(html.contains("RAW:<b>bold</b>"), "{!! !!} 不应转义");
        assertTrue(html.contains("ESC:&lt;b&gt;bold&lt;/b&gt;"), "{{ }} 应 HTML 转义");
        assertTrue(html.contains("INC:P[FROM_PARENT]"), "@include 应传递变量");
    }

    /**
     * 动态函数与动态指令测试：
     * - BladeFunctions.register / merge：模板中未知函数调用运行时查找
     * - BladeDirectives.directive：输出型自定义指令 @datetime
     * - BladeDirectives.condition：条件型自定义指令 @admin / @else / @endadmin
     * - @route 通过 BladeFunctions 的 route 函数解析（http 模块路由别名对接点）
     */
    @Test
    void testDynamicFunctionsAndDirectives() throws Exception {
        BladeFunctions.register("my_upper", args ->
                String.valueOf(args[0]).toUpperCase());
        Map<String, BladeFunctions.BladeFunction> fns = new HashMap<>();
        fns.put("route", args -> "/users/profile#" + args[0]);
        BladeFunctions.merge(fns);
        BladeDirectives.directive("datetime", args -> "TS(" + args[0] + ")");
        BladeDirectives.condition("admin", args -> "admin".equals(args[0]));

        BladeEngine engine = new BladeEngine("templates");

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "tom");
        vars.put("ts", 12345);
        vars.put("role", "admin");

        String html = engine.render("dynamic", vars);

        assertTrue(html.contains("FN:TOM"), "动态注册函数 my_upper 应生效: " + html);
        assertTrue(html.contains("DIR:TS(12345)"), "自定义输出指令 @datetime 应生效: " + html);
        assertTrue(html.contains("COND:IS_ADMIN"), "自定义条件指令 @admin 应生效: " + html);
        assertTrue(html.contains("ROUTE:/users/profile#user.profile"),
                "@route 应通过 BladeFunctions 的 route 函数解析: " + html);

        // 条件为假时走 @else 分支（模板重新渲染，编译类已缓存但条件运行时求值）
        vars.put("role", "guest");
        String html2 = engine.render("dynamic", vars);
        assertTrue(html2.contains("COND:NOT_ADMIN"), "@admin 条件为假应走 @else: " + html2);
    }

    /**
     * Wire 场景回归：renderSection 局部渲染需初始化完整继承链，
     * 且多次调用（含整页渲染后）结果一致、不受 isInitialized 状态影响。
     */
    @Test
    void testRenderSectionForWire() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        Map<String, Object> vars = Map.of("name", "World");

        // 先整页渲染一次（使模板处于 initialized 状态）
        String full = engine.render("inherit.child", vars);
        assertTrue(full.contains("Hello World"));

        // 再局部渲染 content section（Wire 局部更新路径）
        String section = engine.renderSection("inherit.child", "content", new HashMap<>(vars));
        assertTrue(section.contains("Hello World"), "renderSection 应能渲染子模板 section: " + section);

        // sidebar 定义跨越三层继承，局部渲染也应完成 @parent 合并
        String sidebar = engine.renderSection("inherit.child", "sidebar", new HashMap<>(vars));
        assertTrue(sidebar.contains("BASE") && sidebar.contains("MIDDLE") && sidebar.contains("CHILD"),
                "renderSection 应初始化完整继承链并合并 @parent: " + sidebar);
    }
}
