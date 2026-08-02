package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.core.pagination.Paginator;
import com.weacsoft.jaravel.vendor.core.view.HtmlString;
import com.weacsoft.jaravel.vendor.jblade.view.BladeView;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;
import com.weacsoft.jaravel.vendor.jblade.view.ViewManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页器与 links() 模板渲染测试。
 */
class PaginatorTest {

    @BeforeAll
    static void setUpView() {
        ViewManager manager = new ViewManager();
        manager.register(new BladeView("blade", new BladeEngine("templates")));
        ViewFacade.bind(manager);
    }

    private static List<String> rows(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add("row" + i);
        }
        return list;
    }

    // ==================== 基础计算 ====================

    @Test
    void computesPageMetrics() {
        Paginator<String> p = new Paginator<>(rows(10), 95, 10, 3);
        assertEquals(10, p.getLastPage());
        assertEquals(3, p.currentPage());
        assertEquals(95, p.total());
        assertTrue(p.hasPages());
        assertTrue(p.hasMorePages());
        assertFalse(p.onFirstPage());
        assertFalse(p.onLastPage());
        assertEquals(21, p.firstItem());
        assertEquals(30, p.lastItem());
    }

    @Test
    void singlePageHasNoPages() {
        Paginator<String> p = new Paginator<>(rows(3), 3, 15, 1);
        assertEquals(1, p.getLastPage());
        assertFalse(p.hasPages());
        assertTrue(p.onFirstPage());
        assertTrue(p.onLastPage());
    }

    @Test
    void emptyResultIsSafe() {
        Paginator<String> p = new Paginator<>(new ArrayList<>(), 0, 15, 1);
        assertEquals(1, p.getLastPage());
        assertFalse(p.hasPages());
        assertTrue(p.isEmpty());
        assertEquals(0, p.firstItem());
        assertEquals(0, p.lastItem());
    }

    // ==================== URL 生成 ====================

    @Test
    void buildsUrls() {
        Paginator<String> p = new Paginator<String>(rows(10), 100, 10, 5).setPath("/users");
        assertEquals("/users?page=1", p.firstPageUrl());
        assertEquals("/users?page=4", p.previousPageUrl());
        assertEquals("/users?page=6", p.nextPageUrl());
        assertEquals("/users?page=10", p.lastPageUrl());
    }

    @Test
    void appendsQueryParams() {
        Paginator<String> p = new Paginator<String>(rows(10), 100, 10, 2)
                .setPath("/users")
                .appends("keyword", "a b");
        assertEquals("/users?page=3&keyword=a+b", p.nextPageUrl());
    }

    @Test
    void edgeUrlsAreNullOnBoundaries() {
        Paginator<String> first = new Paginator<String>(rows(10), 100, 10, 1).setPath("/u");
        assertNull(first.previousPageUrl());
        Paginator<String> last = new Paginator<String>(rows(10), 100, 10, 10).setPath("/u");
        assertNull(last.nextPageUrl());
    }

    // ==================== 页码元素 ====================

    @Test
    void elementsContainSeparatorForLargeRange() {
        Paginator<String> p = new Paginator<String>(rows(10), 1000, 10, 50).setPath("/u");
        List<Map<String, Object>> els = p.elements();
        boolean hasSep = els.stream().anyMatch(e -> "separator".equals(e.get("type")));
        assertTrue(hasSep, "大范围分页应含省略号");
        // 首尾页必须存在
        assertEquals(1, els.get(0).get("page"));
        assertEquals(100, els.get(els.size() - 1).get("page"));
        // 当前页被标记
        assertTrue(els.stream().anyMatch(e -> Boolean.TRUE.equals(e.get("active"))
                && Integer.valueOf(50).equals(e.get("page"))));
    }

    @Test
    void elementsEmptyWhenSinglePage() {
        Paginator<String> p = new Paginator<>(rows(3), 3, 15, 1);
        assertTrue(p.elements().isEmpty());
    }

    // ==================== links() 渲染 ====================

    @Test
    void linksRendersTemplate() {
        Paginator<String> p = new Paginator<String>(rows(10), 50, 10, 2).setPath("/u");
        String html = p.links("pagination-demo").toHtml();
        assertTrue(html.contains("<ul class=\"pager\">"), "应渲染分页器容器: " + html);
        assertTrue(html.contains("class=\"active\""), "应标记当前页: " + html);
        assertTrue(html.contains("/u?page=1"), "应包含页码链接: " + html);
    }

    /** 没有分页（仅 1 页）时，links() 等同于「没执行」。 */
    @Test
    void linksIsEmptyWhenNoPages() {
        Paginator<String> p = new Paginator<String>(rows(3), 3, 15, 1).setPath("/u");
        assertEquals("", p.links("pagination-demo").toHtml());
    }

    /** 模板不存在时静默降级为空串，不抛异常。 */
    @Test
    void linksIsEmptyWhenTemplateMissing() {
        Paginator<String> p = new Paginator<String>(rows(10), 50, 10, 2).setPath("/u");
        assertEquals("", p.links("layouts.mdui.not-exists").toHtml());
    }

    /** 在模板中 {{ $list->links(...) }} 输出的 HTML 不应被转义。 */
    @Test
    void linksNotEscapedInTemplate() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        Paginator<String> p = new Paginator<String>(rows(3), 30, 3, 2).setPath("/u");
        Map<String, Object> data = new HashMap<>();
        data.put("list", p);
        String out = engine.render("uses-paginator", data);
        assertTrue(out.contains("<ul class=\"pager\">"), "links() 应输出真实 HTML 而非被转义: " + out);
        assertFalse(out.contains("&lt;ul"), "不应出现被转义的标签: " + out);
        assertTrue(out.contains("row1,"), "@foreach 应能遍历分页器: " + out);
    }

    /** 真实的 mdui 分页器模板应能正常渲染（点号路径解析 + 嵌套条件）。 */
    @Test
    void mduiTemplateRenders() {
        Paginator<String> p = new Paginator<String>(rows(10), 1000, 10, 50)
                .setPath("/users").appends("kw", "x");
        String html = p.links("layouts.mdui.pageinator").toHtml();
        assertFalse(html.trim().isEmpty(), "mdui 模板应渲染出内容");
        assertTrue(html.contains("mdui-row"), "应含 mdui 布局: " + html);
        // href 中的 & 会被正确转义为 &amp;
        assertTrue(html.contains("/users?page=49&amp;kw=x"), "应含上一页链接: " + html);
        assertTrue(html.contains("/users?page=51&amp;kw=x"), "应含下一页链接: " + html);
        assertTrue(html.contains("mdui-color-theme-accent"), "当前页应高亮: " + html);
        assertTrue(html.contains("..."), "应含省略号: " + html);
    }

    /** 分页器本身可被 @foreach 直接遍历。 */
    @Test
    void paginatorIsIterable() {
        Paginator<String> p = new Paginator<>(Arrays.asList("a", "b"), 20, 2, 1);
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            sb.append(s);
        }
        assertEquals("ab", sb.toString());
    }
}
