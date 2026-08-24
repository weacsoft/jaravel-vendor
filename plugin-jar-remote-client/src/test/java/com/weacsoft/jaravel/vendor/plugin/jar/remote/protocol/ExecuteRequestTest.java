package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecuteRequest 树形路由元数据功能测试。
 * 验证 canRelay 防环检查与 markVisited 跳数递增行为。
 * <p>
 * ExecuteRequest 定义在 plugin-jar-remote-server 模块的 protocol 包中，
 * client 模块依赖 server 模块，可直接使用。
 */
class ExecuteRequestTest {

    /**
     * 测试 canRelay：未超过跳数且未在访问列表中返回 true。
     */
    @Test
    void testCanRelay() {
        ExecuteRequest request = new ExecuteRequest("req-1", "blog", "blogController",
                "list", List.of("{\"page\":1}"), List.of("java.lang.Integer"));
        // 默认 maxHops=5, currentHop=0, visitedNodes=[]
        assertTrue(request.canRelay("node-A"), "未超过跳数且未访问时应可转发");

        // 已有一些访问节点，但目标节点不在其中
        List<String> visited = new ArrayList<>();
        visited.add("node-X");
        visited.add("node-Y");
        request.setVisitedNodes(visited);
        assertTrue(request.canRelay("node-A"), "目标节点不在访问列表中时应可转发");
        assertTrue(request.canRelay("node-Z"), "目标节点不在访问列表中时应可转发");
    }

    /**
     * 测试 canRelay：超过跳数返回 false。
     */
    @Test
    void testCanRelayMaxHopsExceeded() {
        ExecuteRequest request = new ExecuteRequest("req-2", "blog", "blogController",
                "list", null, null);
        request.setMaxHops(5);

        // currentHop=4，未超过 maxHops=5
        request.setCurrentHop(4);
        assertTrue(request.canRelay("node-A"), "currentHop=4 < maxHops=5 应可转发");

        // currentHop=5，等于 maxHops=5，应不可转发
        request.setCurrentHop(5);
        assertFalse(request.canRelay("node-A"), "currentHop=5 >= maxHops=5 应不可转发");

        // currentHop=6，超过 maxHops=5
        request.setCurrentHop(6);
        assertFalse(request.canRelay("node-A"), "currentHop=6 > maxHops=5 应不可转发");

        // maxHops=0 时，currentHop=0 也应不可转发
        request.setMaxHops(0);
        request.setCurrentHop(0);
        assertFalse(request.canRelay("node-A"), "maxHops=0 且 currentHop=0 时应不可转发");
    }

    /**
     * 测试 canRelay：目标节点已在访问列表中返回 false。
     */
    @Test
    void testCanRelayAlreadyVisited() {
        ExecuteRequest request = new ExecuteRequest("req-3", "blog", "blogController",
                "list", null, null);
        // 默认 maxHops=5, currentHop=0
        List<String> visited = new ArrayList<>();
        visited.add("node-A");
        visited.add("node-B");
        request.setVisitedNodes(visited);

        assertFalse(request.canRelay("node-A"), "node-A 已在访问列表中应不可转发");
        assertFalse(request.canRelay("node-B"), "node-B 已在访问列表中应不可转发");
        assertTrue(request.canRelay("node-C"), "node-C 不在访问列表中应可转发");
    }

    /**
     * 测试 markVisited：标记访问后跳数 +1，visitedNodes 包含节点 ID，且原对象不变。
     */
    @Test
    void testMarkVisited() {
        ExecuteRequest request = new ExecuteRequest("req-4", "blog", "blogController",
                "list", List.of("{\"page\":1}"), List.of("java.lang.Integer"));
        request.setMaxHops(5);
        // 默认 currentHop=0, visitedNodes=[], sourceNodeId=null

        ExecuteRequest relay = request.markVisited("node-A");

        // 验证返回的副本
        assertNotNull(relay, "markVisited 应返回非 null 副本");
        assertEquals(1, relay.getCurrentHop(), "转发副本 currentHop 应 +1");
        assertEquals(5, relay.getMaxHops(), "转发副本 maxHops 应保持不变");
        assertTrue(relay.getVisitedNodes().contains("node-A"), "转发副本 visitedNodes 应包含 node-A");
        assertEquals(1, relay.getVisitedNodes().size(), "转发副本 visitedNodes 应只有 1 个元素");
        assertEquals("node-A", relay.getSourceNodeId(), "sourceNodeId 为 null 时应设为当前节点 ID");

        // 验证原对象未被修改（不可变性）
        assertEquals(0, request.getCurrentHop(), "原对象 currentHop 应保持 0");
        assertFalse(request.getVisitedNodes().contains("node-A"), "原对象 visitedNodes 不应包含 node-A");
        assertNull(request.getSourceNodeId(), "原对象 sourceNodeId 应保持 null");

        // 链式标记：在副本上再次标记
        ExecuteRequest relay2 = relay.markVisited("node-B");
        assertEquals(2, relay2.getCurrentHop(), "第二次标记 currentHop 应为 2");
        assertTrue(relay2.getVisitedNodes().contains("node-A"), "第二次标记应保留 node-A");
        assertTrue(relay2.getVisitedNodes().contains("node-B"), "第二次标记应包含 node-B");
        assertEquals(2, relay2.getVisitedNodes().size(), "第二次标记 visitedNodes 应有 2 个元素");
        assertEquals("node-A", relay2.getSourceNodeId(), "sourceNodeId 已设置后应保持不变");

        // sourceNodeId 已设置时，markVisited 不应覆盖
        request.setSourceNodeId("origin-node");
        ExecuteRequest relay3 = request.markVisited("node-A");
        assertEquals("origin-node", relay3.getSourceNodeId(), "sourceNodeId 已设置时应保持原值");
    }
}
