package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExecuteRequest} 树形路由元数据单元测试。
 */
class ExecuteRequestTest {

    @Test
    void defaultsForTreeRoutingMetadata() {
        ExecuteRequest req = new ExecuteRequest("r1", "plugin-1", "demoBean", "hello",
                List.of("\"x\""), List.of("java.lang.String"));

        // 默认 maxHops=5，currentHop=0
        assertEquals(5, req.getMaxHops());
        assertEquals(0, req.getCurrentHop());
        // 默认 visitedNodes 为空列表（非 null）
        assertTrue(req.getVisitedNodes().isEmpty());
    }

    @Test
    void canRelayRespectsMaxHops() {
        ExecuteRequest req = new ExecuteRequest("r1", "p", "b", "m", List.of(), List.of());
        req.setMaxHops(2);
        req.setCurrentHop(2);

        // 已达到最大跳数，禁止转发
        assertFalse(req.canRelay("node-a"));
    }

    @Test
    void canRelayRejectsAlreadyVisitedNode() {
        ExecuteRequest req = new ExecuteRequest("r1", "p", "b", "m", List.of(), List.of());
        req.setVisitedNodes(Arrays.asList("node-a", "node-b"));

        // node-a 已访问过，禁止再次转发
        assertFalse(req.canRelay("node-a"));
        // 未访问的节点可以转发
        assertTrue(req.canRelay("node-c"));
    }

    @Test
    void markVisitedIncrementsHopAndRecordsNode() {
        ExecuteRequest req = new ExecuteRequest("r1", "p", "b", "m",
                List.of("\"a\""), List.of("java.lang.String"));
        req.setSourceNodeId("origin");
        req.setMaxHops(5);
        req.setCurrentHop(1);

        ExecuteRequest forwarded = req.markVisited("node-x");

        // 副本跳数 +1
        assertEquals(2, forwarded.getCurrentHop());
        // 副本访问列表包含当前节点
        assertTrue(forwarded.getVisitedNodes().contains("node-x"));
        // 源节点继承
        assertEquals("origin", forwarded.getSourceNodeId());
        // 原请求不被修改
        assertEquals(1, req.getCurrentHop());
        assertFalse(req.getVisitedNodes().contains("node-x"));
    }

    @Test
    void markVisitedSetsSourceNodeWhenAbsent() {
        ExecuteRequest req = new ExecuteRequest("r1", "p", "b", "m", List.of(), List.of());
        // sourceNodeId 默认 null
        ExecuteRequest forwarded = req.markVisited("first-node");
        assertEquals("first-node", forwarded.getSourceNodeId());
    }

    @Test
    void getVisitedNodesReturnsEmptyListWhenNull() {
        ExecuteRequest req = new ExecuteRequest("r1", "p", "b", "m", List.of(), List.of());
        req.setVisitedNodes(null);
        // 即便设置为 null，getter 也返回空列表而非 null
        assertTrue(req.getVisitedNodes().isEmpty());
    }
}
