package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubServerRegistry 树形拓扑功能测试。
 * 验证树形注册、子树遍历、祖先链查询、根节点查询、在线过滤、深度限制等行为。
 */
class SubServerRegistryTest {

    private SubServerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SubServerRegistry();
    }

    /**
     * 构建测试树形结构：
     * <pre>
     * root (depth=0)
     * ├── node-A (depth=1)
     * │   ├── node-A1 (depth=2)
     * │   └── node-A2 (depth=2)
     * └── node-B (depth=1)
     * </pre>
     */
    private void buildTree() {
        registry.registerChild("root", "192.168.1.10", 9700, null);
        registry.registerChild("node-A", "192.168.1.11", 9701, "root");
        registry.registerChild("node-A1", "192.168.1.12", 9702, "node-A");
        registry.registerChild("node-A2", "192.168.1.13", 9703, "node-A");
        registry.registerChild("node-B", "192.168.1.14", 9704, "root");
    }

    /**
     * 测试注册子节点，验证父子关系与深度。
     */
    @Test
    void testRegisterChild() {
        registry.registerChild("root", "192.168.1.10", 9700, null);
        registry.registerChild("node-A", "192.168.1.11", 9701, "root");

        SubServerInfo root = registry.getSubServer("root");
        SubServerInfo nodeA = registry.getSubServer("node-A");

        assertNotNull(root, "root 节点应存在");
        assertNotNull(nodeA, "node-A 节点应存在");
        assertTrue(root.isRoot(), "root 应为根节点");
        assertEquals(0, root.getDepth(), "root 深度应为 0");
        assertTrue(root.getChildrenIds().contains("node-A"), "root 的子节点列表应包含 node-A");
        assertEquals("root", nodeA.getParentId(), "node-A 的父节点应为 root");
        assertEquals(1, nodeA.getDepth(), "node-A 深度应为 1");
        assertFalse(nodeA.isRoot(), "node-A 不应为根节点");
    }

    /**
     * 测试获取直接子节点（不含孙节点）。
     */
    @Test
    void testGetChildren() {
        buildTree();

        List<SubServerInfo> rootChildren = registry.getChildren("root");
        assertEquals(2, rootChildren.size(), "root 应有 2 个直接子节点");
        assertTrue(rootChildren.stream().anyMatch(s -> s.getId().equals("node-A")), "应包含 node-A");
        assertTrue(rootChildren.stream().anyMatch(s -> s.getId().equals("node-B")), "应包含 node-B");
        assertFalse(rootChildren.stream().anyMatch(s -> s.getId().equals("node-A1")), "不应包含孙节点 node-A1");

        List<SubServerInfo> nodeAChildren = registry.getChildren("node-A");
        assertEquals(2, nodeAChildren.size(), "node-A 应有 2 个直接子节点");

        List<SubServerInfo> leafChildren = registry.getChildren("node-A1");
        assertTrue(leafChildren.isEmpty(), "叶子节点 node-A1 应无子节点");

        List<SubServerInfo> notExist = registry.getChildren("not-exist");
        assertTrue(notExist.isEmpty(), "不存在的节点应返回空列表");
    }

    /**
     * 测试递归获取整个子树（含自身和所有后代）。
     */
    @Test
    void testGetSubtree() {
        buildTree();

        List<SubServerInfo> subtree = registry.getSubtree("root");
        assertEquals(5, subtree.size(), "root 子树应包含 5 个节点");
        assertTrue(subtree.stream().anyMatch(s -> s.getId().equals("root")), "应包含 root 自身");
        assertTrue(subtree.stream().anyMatch(s -> s.getId().equals("node-A")), "应包含 node-A");
        assertTrue(subtree.stream().anyMatch(s -> s.getId().equals("node-A1")), "应包含 node-A1");
        assertTrue(subtree.stream().anyMatch(s -> s.getId().equals("node-A2")), "应包含 node-A2");
        assertTrue(subtree.stream().anyMatch(s -> s.getId().equals("node-B")), "应包含 node-B");

        List<SubServerInfo> nodeASubtree = registry.getSubtree("node-A");
        assertEquals(3, nodeASubtree.size(), "node-A 子树应包含 3 个节点");

        List<SubServerInfo> leafSubtree = registry.getSubtree("node-B");
        assertEquals(1, leafSubtree.size(), "叶子节点子树应仅含自身");

        assertTrue(registry.getSubtree("not-exist").isEmpty(), "不存在的节点应返回空列表");
    }

    /**
     * 测试获取祖先链（从近到远，父节点到根节点）。
     */
    @Test
    void testGetAncestors() {
        buildTree();

        List<SubServerInfo> ancestors = registry.getAncestors("node-A1");
        assertEquals(2, ancestors.size(), "node-A1 应有 2 个祖先");
        assertEquals("node-A", ancestors.get(0).getId(), "第一个祖先应为 node-A（最近）");
        assertEquals("root", ancestors.get(1).getId(), "第二个祖先应为 root（最远）");

        List<SubServerInfo> rootAncestors = registry.getAncestors("root");
        assertTrue(rootAncestors.isEmpty(), "根节点应无祖先");

        assertTrue(registry.getAncestors("not-exist").isEmpty(), "不存在的节点应返回空列表");
    }

    /**
     * 测试获取所有根节点（parentId 为空的节点）。
     */
    @Test
    void testGetRoots() {
        buildTree();

        List<SubServerInfo> roots = registry.getRoots();
        assertEquals(1, roots.size(), "应只有 1 个根节点");
        assertEquals("root", roots.get(0).getId(), "根节点应为 root");
        assertTrue(roots.get(0).isRoot(), "根节点 isRoot() 应为 true");

        // 多根场景
        registry.registerChild("root2", "192.168.1.20", 9710, null);
        assertEquals(2, registry.getRoots().size(), "多根场景应有 2 个根节点");
    }

    /**
     * 测试获取所有在线后代（递归，不含自身）。
     */
    @Test
    void testGetOnlineDescendants() {
        buildTree();

        // 默认全部离线
        List<SubServerInfo> onlineDesc = registry.getOnlineDescendants("root");
        assertTrue(onlineDesc.isEmpty(), "全部离线时应无在线后代");

        // 设置 node-A1 和 node-B 在线
        registry.updateOnlineStatus("node-A1", true);
        registry.updateOnlineStatus("node-B", true);

        onlineDesc = registry.getOnlineDescendants("root");
        assertEquals(2, onlineDesc.size(), "应有 2 个在线后代");
        assertTrue(onlineDesc.stream().anyMatch(s -> s.getId().equals("node-A1")), "应包含在线的 node-A1");
        assertTrue(onlineDesc.stream().anyMatch(s -> s.getId().equals("node-B")), "应包含在线的 node-B");
        assertFalse(onlineDesc.stream().anyMatch(s -> s.getId().equals("node-A")), "不应包含离线的 node-A");
        assertFalse(onlineDesc.stream().anyMatch(s -> s.getId().equals("root")), "不应包含自身 root");

        // node-A 在线但其子节点离线时，仍递归检查孙节点
        registry.updateOnlineStatus("node-A", true);
        onlineDesc = registry.getOnlineDescendants("root");
        assertEquals(3, onlineDesc.size(), "node-A 上线后应有 3 个在线后代");
    }

    /**
     * 测试超过最大深度抛出 IllegalStateException。
     */
    @Test
    void testMaxDepth() {
        registry.setMaxDepth(2);

        registry.registerChild("root", "192.168.1.10", 9700, null);       // depth=0
        registry.registerChild("node-A", "192.168.1.11", 9701, "root");   // depth=1
        registry.registerChild("node-A1", "192.168.1.12", 9702, "node-A");// depth=2 (边界)

        // depth=3 超过 maxDepth=2，应抛异常
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                registry.registerChild("node-A1-1", "192.168.1.15", 9705, "node-A1"));
        assertTrue(ex.getMessage().contains("节点深度超过最大限制"), "异常信息应包含深度超限提示");

        assertEquals(2, registry.getMaxDepth(), "maxDepth 应为 2");
    }

    /**
     * 测试打印树形结构字符串。
     */
    @Test
    void testDumpTree() {
        buildTree();

        String tree = registry.dumpTree();
        assertNotNull(tree, "dumpTree 不应返回 null");
        assertFalse(tree.isEmpty(), "dumpTree 不应返回空字符串");

        // 验证所有节点 ID 出现在输出中
        assertTrue(tree.contains("root"), "应包含 root");
        assertTrue(tree.contains("node-A"), "应包含 node-A");
        assertTrue(tree.contains("node-A1"), "应包含 node-A1");
        assertTrue(tree.contains("node-A2"), "应包含 node-A2");
        assertTrue(tree.contains("node-B"), "应包含 node-B");

        // 默认离线，应标记 [-]
        assertTrue(tree.contains("[-]"), "离线节点应标记 [-]");

        // 叶子节点应标记 [leaf]
        assertTrue(tree.contains("[leaf]"), "叶子节点应标记 [leaf]");

        // 设置在线后应标记 [+]
        registry.updateOnlineStatus("root", true);
        String tree2 = registry.dumpTree();
        assertTrue(tree2.contains("[+]"), "在线节点应标记 [+]");
    }

    /**
     * 测试在线子节点过滤。
     */
    @Test
    void testOnlineChildren() {
        buildTree();

        // 默认全部离线
        List<SubServerInfo> onlineChildren = registry.getOnlineChildren("root");
        assertTrue(onlineChildren.isEmpty(), "全部离线时在线子节点应为空");

        // 设置 node-A 在线
        registry.updateOnlineStatus("node-A", true);
        onlineChildren = registry.getOnlineChildren("root");
        assertEquals(1, onlineChildren.size(), "应有 1 个在线子节点");
        assertEquals("node-A", onlineChildren.get(0).getId(), "在线子节点应为 node-A");

        // 设置 node-B 也在线
        registry.updateOnlineStatus("node-B", true);
        onlineChildren = registry.getOnlineChildren("root");
        assertEquals(2, onlineChildren.size(), "应有 2 个在线子节点");

        // 孙节点在线不影响直接子节点的在线过滤
        registry.updateOnlineStatus("node-A1", true);
        onlineChildren = registry.getOnlineChildren("root");
        assertEquals(2, onlineChildren.size(), "孙节点上线不影响直接子节点数量");
    }
}
