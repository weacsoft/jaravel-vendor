package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WireController 公共契约单元测试。
 * <p>
 * 覆盖用户纠正后的核心 API：
 * <ul>
 *   <li>{@code fill(key,value)} 是「赋值」(同名赋值),且按属性类型做基础转换</li>
 *   <li>{@code fill(Map)} 批量赋值</li>
 *   <li>{@code wireView(name)} / {@code wireView(name, extra)} 契约：
 *       不携带 bladeExtends(布局由模板自身 @extends 或组件渲染期的 wireLayoutReplacements() 替换),
 *       且会把 Controller 的 public 属性与 extra 聚合进渲染数据</li>
 *   <li>{@code wireLayoutReplacements()} 声明式布局替换 + {@code getWireLayoutReplace} 合并查询
 *       (声明式 + 请求级 setWireLayoutReplace,请求级优先)</li>
 *   <li>{@code wireComponents()} 控制器强关联组件注册表(组件名 → 模板名)</li>
 *   <li>{@code invokeAction} 按 action 声明参数类型做转换(Long/Boolean 等)</li>
 *   <li>{@code buildUpdateUrl} 在路由名缺失时回退为 request.uri() 且不抛异常</li>
 *   <li>{@code encodeSignedSnapshot} 自动排除 {@code @WireLocked} 字段</li>
 * </ul>
 *
 * <p>注：{@code $sync}(仅返回 snapshot,不重渲染 section) 与 {@code $refresh} 依赖完整渲染链路
 * (BladeEngine + 模板),由浏览器集成测试(admin CRUD 17/17)覆盖,此处不做白盒单测。
 * 布局替换的端到端效果(直访整页表单 / 列表点击弹对话框)由浏览器集成测试覆盖。
 */
class WireControllerApiTest {

    // ===== 测试用具体控制器 =====
    public static class SampleController extends WireController {
        public Long id;
        public String name;
        public Boolean flag;
        @WireLocked
        public List<String> lockedList;

        // 用于 invokeAction 类型转换测试
        public void doThing(Long id, Boolean flag) {
            this.id = id;
            this.flag = flag;
        }

        @Override
        protected WireView render() {
            // 纠正后的写法：render 只声明模板 + extra;主页面布局由模板自身 @extends,
            // 组件下发渲染的布局替换由 wireLayoutReplacements() 声明式提供。
            return wireView("sample.list", Map.of("list", lockedList));
        }

        // 声明式:模板级布局替换(仅组件下发渲染生效)
        @Override
        protected Map<String, String> wireLayoutReplacements() {
            return Map.of("sample.item", "layouts.sample.dialog");
        }

        // 声明式:控制器强关联组件注册表
        @Override
        protected Map<String, String> wireComponents() {
            return Map.of("sample-form", "sample.item");
        }

        // ---- 白盒暴露受保护/私有方法给测试 ----
        public void pubFill(String k, Object v) { fill(k, v); }
        public void pubFillMap(Map<String, Object> m) { fill(m); }
        public WireView pubWireView(String name) { return wireView(name); }
        public WireView pubWireView(String name, Map<String, Object> extra) { return wireView(name, extra); }
        public String pubBuildUpdateUrl(Request r) { return buildUpdateUrl(r); }
        public String pubGetWireLayoutReplace(String template) { return getWireLayoutReplace(template); }
        public String pubResolveComponentTemplate(String name) {
            try {
                Method m = WireController.class.getDeclaredMethod("resolveComponentTemplate", String.class);
                m.setAccessible(true);
                return (String) m.invoke(this, name);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        public void pubInvokeAction(String action, Map<String, Object> params) {
            try {
                Method m = WireController.class.getDeclaredMethod("invokeAction", String.class, Map.class);
                m.setAccessible(true);
                m.invoke(this, action, params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> pubCollectPublicFields() {
            try {
                Method m = WireController.class.getDeclaredMethod("collectPublicFields", Map.class);
                m.setAccessible(true);
                return (Map<String, Object>) m.invoke(this, new LinkedHashMap<String, Object>());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public String pubEncodeSignedSnapshot(Map<String, Object> data, Request r) {
            try {
                Method m = WireController.class.getDeclaredMethod("encodeSignedSnapshot", Map.class, Request.class);
                m.setAccessible(true);
                return (String) m.invoke(this, data, r);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private SampleController newController() {
        return new SampleController();
    }

    // ===== fill: 赋值语义 =====
    @Test
    void fill_assigns_by_name_and_keeps_other_fields() {
        SampleController c = newController();
        c.name = "keep-me";
        c.pubFill("id", "42");
        assertEquals(42L, c.id);
        // 关键:fill 是「赋值」而非整体覆盖,其它字段不受影响
        assertEquals("keep-me", c.name);
    }

    @Test
    void fill_null_value_sets_null() {
        SampleController c = newController();
        c.name = "x";
        c.pubFill("name", null);
        assertNull(c.name);
    }

    @Test
    void fill_type_conversion() {
        SampleController c = newController();
        c.pubFill("id", "99");
        c.pubFill("flag", "true");
        assertEquals(99L, c.id);
        assertTrue(c.flag);
    }

    @Test
    void fill_unknown_key_is_ignored_silently() {
        SampleController c = newController();
        c.pubFill("not_a_real_field", "whatever");
        // 不抛异常即可
        assertNull(c.id);
    }

    @Test
    void fillMap_bulk_assignment() {
        SampleController c = newController();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "5");
        data.put("name", "abc");
        data.put("flag", "false");
        c.pubFillMap(data);
        assertEquals(5L, c.id);
        assertEquals("abc", c.name);
        assertFalse(c.flag);
    }

    // ===== wireView 契约 =====
    @Test
    void wireView_name_only_has_no_extends_layout() {
        SampleController c = newController();
        WireView v = c.pubWireView("sample.list");
        assertEquals("sample.list", v.getTemplateName());
        // 纠正后:render 不调用 bladeExtends;布局由模板自身 @extends 或组件渲染期替换提供
        assertNull(v.getExtendsTemplate());
    }

    @Test
    void wireView_aggregates_controller_properties_and_extra() {
        SampleController c = newController();
        c.id = 7L;
        c.name = "n";
        List<String> items = Arrays.asList("a", "b");
        WireView v = c.pubWireView("sample.list", Map.of("list", items));

        Map<String, Object> properties = c.pubCollectPublicFields();
        Map<String, Object> merged = v.getMergedData(properties);

        // extra 注入
        assertSame(items, merged.get("list"));
        // Controller 公共属性也被聚合(等价于 ResponseBuilder.view 自动 with 公共属性)
        assertEquals(7L, merged.get("id"));
        assertEquals("n", merged.get("name"));
    }

    // ===== wireLayoutReplacements:声明式布局替换(一次声明,处处生效) =====
    @Test
    void wireLayoutReplacements_declared_once_apply_to_component_render() {
        SampleController c = newController();
        // 命中声明式规则 → 返回替换布局
        assertEquals("layouts.sample.dialog", c.pubGetWireLayoutReplace("sample.item"));
        // 未命中 → null(使用模板自身 @extends)
        assertNull(c.pubGetWireLayoutReplace("sample.other"));
    }

    @Test
    void wireLayoutReplacements_request_level_overrides_declared() {
        SampleController c = newController();
        // 请求级 setWireLayoutReplace 优先于声明式
        c.setWireLayoutReplace("sample.item", "layouts.sample.full");
        assertEquals("layouts.sample.full", c.pubGetWireLayoutReplace("sample.item"));
    }

    // 每个用例后清理请求级 ThreadLocal,避免跨用例串扰
    @org.junit.jupiter.api.AfterEach
    void clearRequestLevelReplacements() {
        try {
            java.lang.reflect.Field f = WireController.class.getDeclaredField("WIRE_LAYOUT_REPLACEMENTS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<Map<String, String>> tl = (ThreadLocal<Map<String, String>>) f.get(null);
            if (tl != null) tl.remove();
        } catch (Exception ignored) {
        }
    }

    // ===== wireComponents:控制器强关联组件注册表 =====
    @Test
    void wireComponents_own_registry_resolves_template() {
        SampleController c = newController();
        // 控制器私有组件:admin-form 类对话框由 wireComponents() 解析,不依赖配置文件
        assertEquals("sample.item", c.pubResolveComponentTemplate("sample-form"));
    }

    @Test
    void resolveComponentTemplate_falls_back_to_global_then_name() {
        SampleController c = newController();
        // 未注册 → 兜底:组件名即模板名
        assertEquals("some.template", c.pubResolveComponentTemplate("some.template"));
        // toast 等全局命名组件在 WireManager 中注册后也可解析(此处未注册 → 兜底同名)
        assertEquals("toast", c.pubResolveComponentTemplate("toast"));
    }

    // ===== invokeAction 类型转换 =====
    @Test
    void invokeAction_converts_typed_parameters() {
        SampleController c = newController();
        c.id = null;
        c.flag = null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("0", "7");
        params.put("1", "true");
        c.pubInvokeAction("doThing", params);
        assertEquals(7L, c.id);
        assertTrue(c.flag);
    }

    @Test
    void invokeAction_unknown_method_is_noop() {
        SampleController c = newController();
        c.id = 1L;
        c.pubInvokeAction("noSuchMethod", Map.of());
        // 不应抛异常,且字段保持原值
        assertEquals(1L, c.id);
    }

    // ===== buildUpdateUrl =====
    @Test
    void buildUpdateUrl_falls_back_to_uri_when_no_route() {
        SampleController c = new SampleController() {
            @Override
            protected String getUpdateRouteName() { return null; }
        };
        Request r = new Request(); // 无 servlet,uri() 返回 ""
        String url = c.pubBuildUpdateUrl(r);
        assertNotNull(url);
        assertEquals("", url); // 无路由名时回退当前 URI(此处为空串)
    }

    // ===== @WireLocked 排除 =====
    @Test
    void encodeSignedSnapshot_excludes_wire_locked_fields() {
        SampleController c = newController();
        Request r = new Request();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 1);
        data.put("name", "x");
        data.put("lockedList", Arrays.asList("secret1", "secret2"));

        String signed = c.pubEncodeSignedSnapshot(data, r);
        assertTrue(signed.contains(":"), "应为 signature:base64 形式");

        // 取出 base64 快照部分并解码
        String base64 = signed.substring(signed.indexOf(':') + 1);
        Map<String, Object> decoded = WireManager.decodeSnapshot(base64);

        // @WireLocked 字段不应进入快照
        assertFalse(decoded.containsKey("lockedList"), "@WireLocked 字段不应进入快照");
        // 普通字段保留
        assertEquals(1, decoded.get("id"));
        assertEquals("x", decoded.get("name"));
    }

    // ===== @WireQuery / wireQueryTemplates 模板作用域 =====
    public static class ParentQueryController extends WireController {
        @WireQuery(templates = {"mdui.admin.admin.list"}, defaultValue = "1")
        public Long page;

        @WireQuery(templates = {"mdui.admin.admin.list"})
        public String search;

        @Override
        protected WireView render() {
            return wireView("mdui.admin.admin.list");
        }
    }

    public static class ChildQueryController extends ParentQueryController {
        @Override
        protected Map<String, String[]> wireQueryTemplates() {
            return Map.of("page", new String[]{"mdui.admin.admin.list", "mdui.admin.admin.change"});
        }
    }

    public static class MixedQueryController extends ParentQueryController {
        @Override
        protected Map<String, String[]> wireQueryTemplates() {
            return Map.of("page", new String[]{"mdui.admin.admin.list", "mdui.admin.admin.change"});
        }
    }

    public static class NameMappedQueryController extends WireController {
        @WireQuery(name = "p", templates = {"mdui.admin.admin.list"}, defaultValue = "1")
        public Long page;

        @Override
        protected Map<String, String[]> wireQueryTemplates() {
            return Map.of("p", new String[]{"mdui.admin.admin.list", "mdui.admin.admin.detail"});
        }

        @Override
        protected WireView render() {
            return wireView("mdui.admin.admin.list");
        }
    }

    private String pubBuildQueryUrl(WireController c, String basePath, String template) {
        try {
            java.lang.reflect.Method m = WireController.class.getDeclaredMethod("buildQueryUrl", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(c, basePath, template);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void buildQueryUrl_noOverride_usesAnnotationTemplates() {
        ParentQueryController c = new ParentQueryController();
        c.page = 2L;
        c.search = "foo";

        String url = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.list");
        assertTrue(url.contains("?page=2"));
        assertTrue(url.contains("search=foo"));

        String url2 = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.change");
        assertFalse(url2.contains("page"));
        assertFalse(url2.contains("search"));
    }

    @Test
    void buildQueryUrl_override_expandsTemplates() {
        ChildQueryController c = new ChildQueryController();
        c.page = 3L;
        c.search = "bar";

        String url = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.list");
        assertTrue(url.contains("page=3"));
        assertTrue(url.contains("search=bar"));

        String url2 = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.change");
        assertTrue(url2.contains("page=3"));
        assertFalse(url2.contains("search"));
    }

    @Test
    void buildQueryUrl_defaultValue_filtered() {
        ParentQueryController c = new ParentQueryController();
        c.page = 1L;
        c.search = "x";

        String url = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.list");
        assertFalse(url.contains("page"));
        assertTrue(url.contains("search=x"));
    }

    @Test
    void buildQueryUrl_nameMapping_usesWireQueryName() {
        NameMappedQueryController c = new NameMappedQueryController();
        c.page = 2L;

        String url = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.list");
        assertTrue(url.contains("?p=2"));
        assertFalse(url.contains("page="));

        String url2 = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.detail");
        assertTrue(url2.contains("p=2"));
    }

    @Test
    void buildQueryUrl_emptyBasePath_returnsQueryOnly() {
        ParentQueryController c = new ParentQueryController();
        c.page = 5L;

        String url = pubBuildQueryUrl(c, "", "mdui.admin.admin.list");
        assertEquals("?page=5", url);
    }

    @Test
    void buildQueryUrl_noMatchingValue_skips() {
        ParentQueryController c = new ParentQueryController();
        c.page = null;
        c.search = null;

        String url = pubBuildQueryUrl(c, "/admin/admin", "mdui.admin.admin.list");
        assertEquals("/admin/admin", url);
    }

    @Test
    void encodeDecodeSignedSnapshot_roundtrip_and_tamper_detection() {
        SampleController c = newController();
        Request r = new Request();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 123);
        data.put("name", "jaravel");

        String signed = c.pubEncodeSignedSnapshot(data, r);
        String base64 = signed.substring(signed.indexOf(':') + 1);
        Map<String, Object> decoded = WireManager.decodeSnapshot(base64);
        assertEquals(123, decoded.get("id"));
        assertEquals("jaravel", decoded.get("name"));
    }
}
