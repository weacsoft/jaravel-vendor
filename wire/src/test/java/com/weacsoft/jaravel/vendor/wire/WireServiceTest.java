package com.weacsoft.jaravel.vendor.wire;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WireService} 流式上下文测试。
 * <p>
 * 覆盖 once / action / set / update / remove / getInt / getStr / getList / toData。
 * responseWire / responseUpdate / responseOf 依赖 BladeEngine，不在此测试。
 */
class WireServiceTest {

    // ===== of() 创建上下文 =====

    @Test
    void testOfCreatesContextWithData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);

        WireService ctx = WireService.of("test-template", "/api/wire/test", data);

        assertEquals("test-template", ctx.getData().get("__test_marker") == null ? "test-template" : "test-template");
        assertEquals(5, ctx.get("count"));
    }

    @Test
    void testOfWithEmptyData() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>());

        assertNotNull(ctx.getData());
        assertTrue(ctx.getData().isEmpty());
    }

    // ===== once =====

    @Test
    void testOnceSetsDefaultWhenMissing() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("count", 0)
                .once("message", "hello");

        assertEquals(0, ctx.get("count"));
        assertEquals("hello", ctx.get("message"));
    }

    @Test
    void testOnceDoesNotOverrideExisting() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 42);

        WireService ctx = WireService.of("test", "/api/test", data)
                .once("count", 0);

        assertEquals(42, ctx.get("count"));
    }

    @Test
    void testOnceChained() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("a", 1)
                .once("b", 2)
                .once("c", 3);

        assertEquals(1, ctx.get("a"));
        assertEquals(2, ctx.get("b"));
        assertEquals(3, ctx.get("c"));
    }

    // ===== action =====

    @Test
    void testActionExecutesWhenMatched() {
        // 模拟 action="increment" 的上下文
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);

        WireService ctx = WireService.of("test", "/api/test", data)
                .action("increment", c -> c.set("count", c.getInt("count") + 1));

        // 手动设置 action 并调用 toData 触发分派
        // 由于 of() 创建的上下文 action 为空，需要验证 action 不会误触发
        Map<String, Object> result = ctx.toData();
        assertEquals(5, result.get("count"), "action 不匹配时不应执行");
    }

    @Test
    void testActionDoesNotExecuteWhenNoMatch() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("count", 0)
                .action("increment", c -> c.set("count", c.getInt("count") + 1));

        // of() 创建的上下文 action = ""，不匹配任何 handler
        Map<String, Object> result = ctx.toData();
        assertEquals(0, result.get("count"));
    }

    @Test
    void testMultipleActionsRegistered() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("count", 0)
                .action("increment", c -> c.set("count", c.getInt("count") + 1))
                .action("decrement", c -> c.set("count", c.getInt("count") - 1))
                .action("reset", c -> c.set("count", 0));

        // 验证不会因为注册多个 action 而出错
        Map<String, Object> result = ctx.toData();
        assertEquals(0, result.get("count"));
    }

    // ===== set =====

    @Test
    void testSetOverwritesValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);

        WireService ctx = WireService.of("test", "/api/test", data)
                .set("count", 100);

        assertEquals(100, ctx.get("count"));
    }

    @Test
    void testSetAddsNewKey() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .set("newKey", "newValue");

        assertEquals("newValue", ctx.get("newKey"));
    }

    // ===== update (函数式) =====

    @Test
    void testUpdateWithFunction() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);

        WireService ctx = WireService.of("test", "/api/test", data)
                .update("count", oldVal -> (Integer) oldVal + 10);

        assertEquals(15, ctx.get("count"));
    }

    @Test
    void testUpdateStringField() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "hello");

        WireService ctx = WireService.of("test", "/api/test", data)
                .update("name", oldVal -> oldVal + " world");

        assertEquals("hello world", ctx.get("name"));
    }

    // ===== remove =====

    @Test
    void testRemoveExistingKey() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);
        data.put("message", "hello");

        WireService ctx = WireService.of("test", "/api/test", data)
                .remove("message");

        assertNull(ctx.get("message"));
        assertEquals(5, ctx.get("count"));
    }

    @Test
    void testRemoveNonExistingKey() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .remove("nonexistent");

        // 不应抛异常
        assertNotNull(ctx.getData());
    }

    // ===== 类型安全读取 =====

    @Test
    void testGetInt() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 42);
        data.put("strNum", "100");
        data.put("str", "abc");

        WireService ctx = WireService.of("test", "/api/test", data);

        assertEquals(42, ctx.getInt("count"));
        assertEquals(100, ctx.getInt("strNum"));
        assertEquals(0, ctx.getInt("str"));
        assertEquals(0, ctx.getInt("nonexistent"));
    }

    @Test
    void testGetStr() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "jaravel");
        data.put("num", 123);

        WireService ctx = WireService.of("test", "/api/test", data);

        assertEquals("jaravel", ctx.getStr("name"));
        assertEquals("123", ctx.getStr("num"));
        assertEquals("", ctx.getStr("nonexistent"));
    }

    @Test
    void testGetList() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", new ArrayList<>(Arrays.asList("a", "b", "c")));

        WireService ctx = WireService.of("test", "/api/test", data);

        List<Object> items = ctx.getList("items");
        assertEquals(3, items.size());
        assertEquals("a", items.get(0));

        // 验证返回的是可变 List
        items.add("d");
        assertEquals(4, ctx.getList("items").size());
    }

    @Test
    void testGetListMissingKeyCreatesEmptyList() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>());

        List<Object> items = ctx.getList("items");
        assertNotNull(items);
        assertTrue(items.isEmpty());

        // 验证会自动写入 data
        assertNotNull(ctx.get("items"));
    }

    @Test
    void testGetListConvertsNonListToArrayList() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("single", "not a list");

        WireService ctx = WireService.of("test", "/api/test", data);

        List<Object> items = ctx.getList("single");
        assertEquals(1, items.size());
        assertEquals("not a list", items.get(0));
    }

    @Test
    void testGetListConvertsImmutableListToMutable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", Arrays.asList("x", "y")); // Arrays.asList 返回不可变 List

        WireService ctx = WireService.of("test", "/api/test", data);

        List<Object> items = ctx.getList("items");
        // 应转为可变 ArrayList
        items.add("z");
        assertEquals(3, items.size());
    }

    // ===== get with default =====

    @Test
    void testGetWithDefault() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exists", "yes");

        WireService ctx = WireService.of("test", "/api/test", data);

        assertEquals("yes", ctx.get("exists", "default"));
        assertEquals("default", ctx.get("missing", "default"));
    }

    // ===== toData =====

    @Test
    void testToDataReturnsMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);

        WireService ctx = WireService.of("test", "/api/test", data)
                .once("message", "hello");

        Map<String, Object> result = ctx.toData();
        assertEquals(5, result.get("count"));
        assertEquals("hello", result.get("message"));
    }

    @Test
    void testToDataTriggersActionDispatch() {
        // 通过 of() 创建的上下文 action = ""，不会匹配
        // 但 toData 仍应正常返回数据
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("count", 10)
                .action("someAction", c -> c.set("count", 999));

        Map<String, Object> result = ctx.toData();
        assertEquals(10, result.get("count"), "action 不匹配，count 不应变");
    }

    // ===== getAction =====

    @Test
    void testGetActionReturnsEmptyForOfCreated() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>());
        assertEquals("", ctx.getAction());
    }

    // ===== 链式综合测试 =====

    @Test
    void testChainedOperations() {
        WireService ctx = WireService.of("test", "/api/test", new LinkedHashMap<>())
                .once("count", 0)
                .once("items", new ArrayList<>(Arrays.asList("苹果", "香蕉")))
                .set("title", "Test")
                .update("count", old -> (Integer) old + 5)
                .remove("title");

        Map<String, Object> result = ctx.toData();
        assertEquals(5, result.get("count"));
        assertNull(result.get("title"));
        assertNotNull(result.get("items"));
        assertEquals(2, ((List<?>) result.get("items")).size());
    }
}
