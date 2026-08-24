package com.weacsoft.jaravel.vendor.wire.navigation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WireRenderer} 栈式 section 提取与差异化 diff 测试。
 *
 * <p>覆盖 2026-08-13 修复：嵌套 section 必须能被独立提取（旧实现用正则 find()
 * 只匹配最外层 section，导致父 section 的 hash 永远受内部嵌套内容影响，
 * 导航时 body 大区块整体重建 —— 「模板继承差异化」能力失效）。
 */
class WireRendererTest {

    /** 构造一个嵌套结构的 HTML：body 内嵌 appbar/bar_end/drawer_content/content。 */
    private String nestedHtml(String contentValue, String barEndValue) {
        return "<html><head>"
                + "<title>测试页</title>"
                + "</head><body>"
                + "<!--wire:section-start:body-->"
                + "<div class=\"appbar\">"
                + "<!--wire:section-start:bar_end--><button>" + barEndValue + "</button><!--wire:section-end:bar_end-->"
                + "</div>"
                + "<!--wire:section-start:drawer_content--><nav>菜单</nav><!--wire:section-end:drawer_content-->"
                + "<!--wire:section-start:content--><main>" + contentValue + "</main><!--wire:section-end:content-->"
                + "<!--wire:section-end:body-->"
                + "</body></html>";
    }

    // ===== extractAllSections：嵌套提取 =====

    @Test
    void testExtractAllSectionsIncludesNested() {
        String html = nestedHtml("A", "B");
        List<WireRenderer.Section> sections = WireRenderer.extractAllSections(html);
        assertEquals(4, sections.size(), "应提取 body + bar_end + drawer_content + content 共 4 个 section");

        Map<String, String> byName = new LinkedHashMap<>();
        for (WireRenderer.Section s : sections) byName.put(s.name, s.content);

        assertTrue(byName.containsKey("body"));
        assertTrue(byName.containsKey("bar_end"));
        assertTrue(byName.containsKey("drawer_content"));
        assertTrue(byName.containsKey("content"));

        // 嵌套 section 内容不含自身标记，但含更内层内容
        assertTrue(byName.get("content").contains("<main>A</main>"));
        assertTrue(byName.get("bar_end").contains("<button>B</button>"));
        // body 内容包含所有嵌套 section 的完整标记
        assertTrue(byName.get("body").contains("<!--wire:section-start:content-->"));
        assertTrue(byName.get("body").contains("<!--wire:section-end:content-->"));
    }

    @Test
    void testExtractAllSectionsEmpty() {
        assertTrue(WireRenderer.extractAllSections("").isEmpty());
        assertTrue(WireRenderer.extractAllSections("<html>无标记</html>").isEmpty());
        assertTrue(WireRenderer.extractAllSections(null).isEmpty());
    }

    // ===== 差异化 hash：父 section 与子 section 内容解耦 =====

    @Test
    void testParentHashUnchangedWhenChildChanges() {
        String htmlA = nestedHtml("内容A", "按钮A");
        String htmlB = nestedHtml("内容B", "按钮A"); // 仅 content 变，bar_end 不变

        Map<String, String> hashesA = WireRenderer.computeHashes(htmlA);
        Map<String, String> hashesB = WireRenderer.computeHashes(htmlB);

        // 子 section content 变化 → 自身 hash 变化
        assertNotEquals(hashesA.get("content"), hashesB.get("content"));
        // 父 section body 的 hash 必须稳定（剥离子 section 后独有内容一致）
        assertEquals(hashesA.get("body"), hashesB.get("body"),
                "父 section 的 hash 不应受嵌套子 section 内容影响（差异化 diff 的核心）");
        // 未变化的嵌套 section hash 一致
        assertEquals(hashesA.get("bar_end"), hashesB.get("bar_end"));
        assertEquals(hashesA.get("drawer_content"), hashesB.get("drawer_content"));
    }

    @Test
    void testChildHashChanges() {
        String htmlA = nestedHtml("内容A", "按钮A");
        String htmlB = nestedHtml("内容A", "按钮B"); // 仅 bar_end 变

        Map<String, String> hashesA = WireRenderer.computeHashes(htmlA);
        Map<String, String> hashesB = WireRenderer.computeHashes(htmlB);

        assertNotEquals(hashesA.get("bar_end"), hashesB.get("bar_end"));
        assertEquals(hashesA.get("body"), hashesB.get("body"),
                "bar_end 变化也不应使 body 整体判定为变化");
    }

    // ===== computeHashes：首屏 hash 完整（含嵌套） =====

    @Test
    void testComputeHashesContainsAllSections() {
        String html = nestedHtml("A", "B");
        Map<String, String> hashes = WireRenderer.computeHashes(html);
        assertEquals(4, hashes.size(), "首屏 __wireHashes 必须包含嵌套 section，否则导航时这些 section 被误判为变化");
    }

    // ===== renderDiff：最小 diff（利用 WireContext） =====

    @Test
    void testRenderDiffOnlyChangedSections() {
        String oldHtml = nestedHtml("内容A", "按钮A");
        String newHtml = nestedHtml("内容B", "按钮A"); // 仅 content 变

        Map<String, String> oldHashes = WireRenderer.computeHashes(oldHtml);
        // 模拟客户端上报旧页面全部 hash
        WireContext.begin(new LinkedHashMap<>(oldHashes));
        try {
            String json = WireRenderer.renderDiff(newHtml, "/page-b");
            assertNotNull(json);

            // 解析 JSON：应只下发 content，body/bar_end/drawer_content 不下发
            // 注意 hashes 是全量下发的（所有 section 的 hash 都含 "body" 键），
            // 判断下发内容只能看 "sections" 段。
            String sectionsPart = json.substring(json.indexOf("\"sections\":"),
                    json.indexOf("\"hashes\":"));
            assertTrue(sectionsPart.contains("\"content\""), "content 变化应下发: " + json);
            assertFalse(sectionsPart.contains("\"body\""), "body 未变化不应下发: " + json);
            assertFalse(sectionsPart.contains("\"bar_end\""), "bar_end 未变化不应下发: " + json);
        } finally {
            WireContext.clear();
        }
    }

    @Test
    void testRenderDiffAllChanged() {
        String oldHtml = nestedHtml("内容A", "按钮A");
        String newHtml = nestedHtml("内容B", "按钮B"); // content + bar_end 都变

        Map<String, String> oldHashes = WireRenderer.computeHashes(oldHtml);
        WireContext.begin(new LinkedHashMap<>(oldHashes));
        try {
            String json = WireRenderer.renderDiff(newHtml, "/page-b");
            String sectionsPart = json.substring(json.indexOf("\"sections\":"),
                    json.indexOf("\"hashes\":"));
            assertTrue(sectionsPart.contains("\"content\""));
            assertTrue(sectionsPart.contains("\"bar_end\""));
            assertFalse(sectionsPart.contains("\"body\""), "body 仍不应下发（独有内容未变）");
        } finally {
            WireContext.clear();
        }
    }
}
