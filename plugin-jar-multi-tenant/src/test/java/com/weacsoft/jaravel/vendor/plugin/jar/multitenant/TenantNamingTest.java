package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantNaming 租户命名工具测试。
 * 覆盖 pluginId 解析（tenant@plugin 格式）、Bean 名称前缀化、路由路径前缀化。
 */
class TenantNamingTest {

    private static final String SEPARATOR = "@";

    @Test
    void testExtractTenant() {
        assertEquals("studentA", TenantNaming.extractTenant("studentA@blog", SEPARATOR),
                "应从 studentA@blog 中提取租户 studentA");
        assertEquals("tenant1", TenantNaming.extractTenant("tenant1@plugin1", SEPARATOR),
                "应从 tenant1@plugin1 中提取租户 tenant1");
    }

    @Test
    void testExtractTenantNoSeparatorReturnsNull() {
        assertNull(TenantNaming.extractTenant("blog", SEPARATOR),
                "不含分隔符时应返回 null（非多租户插件）");
    }

    @Test
    void testExtractTenantEdgeCases() {
        assertNull(TenantNaming.extractTenant(null, SEPARATOR), "null pluginId 应返回 null");
        assertNull(TenantNaming.extractTenant("blog", null), "null separator 应返回 null");
        assertNull(TenantNaming.extractTenant("blog", ""), "空 separator 应返回 null");
        assertNull(TenantNaming.extractTenant("@blog", SEPARATOR), "分隔符在开头应返回 null");
        assertNull(TenantNaming.extractTenant("blog@", SEPARATOR), "分隔符在末尾应返回 null");
    }

    @Test
    void testExtractBasePluginId() {
        assertEquals("blog", TenantNaming.extractBasePluginId("studentA@blog", SEPARATOR),
                "应从 studentA@blog 中提取基础插件 ID blog");
        assertEquals("plugin1", TenantNaming.extractBasePluginId("tenant1@plugin1", SEPARATOR),
                "应从 tenant1@plugin1 中提取基础插件 ID plugin1");
    }

    @Test
    void testExtractBasePluginIdNoSeparatorReturnsOriginal() {
        assertEquals("blog", TenantNaming.extractBasePluginId("blog", SEPARATOR),
                "不含分隔符时应返回原值");
    }

    @Test
    void testPrefixBeanName() {
        assertEquals("studentA:blogController",
                TenantNaming.prefixBeanName("studentA", "blogController"),
                "Bean 名称应添加租户前缀 tenantId:beanName");
    }

    @Test
    void testPrefixBeanNameWithEmptyTenant() {
        assertEquals("blogController", TenantNaming.prefixBeanName(null, "blogController"),
                "null 租户应返回原始 Bean 名称");
        assertEquals("blogController", TenantNaming.prefixBeanName("", "blogController"),
                "空租户应返回原始 Bean 名称");
    }

    @Test
    void testPrefixRoutePathWithLeadingSlash() {
        assertEquals("/studentA/blog/list",
                TenantNaming.prefixRoutePath("studentA", "/blog/list"),
                "路径以 / 开头时应前缀化为 /tenantId/path");
    }

    @Test
    void testPrefixRoutePathWithoutLeadingSlash() {
        assertEquals("/studentA/blog/list",
                TenantNaming.prefixRoutePath("studentA", "blog/list"),
                "路径不以 / 开头时应前缀化为 /tenantId/path");
    }

    @Test
    void testPrefixRoutePathWithEmptyTenant() {
        assertEquals("/blog/list", TenantNaming.prefixRoutePath(null, "/blog/list"),
                "null 租户应返回原始路径");
        assertEquals("/blog/list", TenantNaming.prefixRoutePath("", "/blog/list"),
                "空租户应返回原始路径");
    }

    @Test
    void testPrefixRoutePathWithNullPath() {
        assertEquals("/studentA", TenantNaming.prefixRoutePath("studentA", null),
                "null 路径应返回 /tenantId");
    }

    @Test
    void testBuildPluginId() {
        assertEquals("studentA@blog", TenantNaming.buildPluginId("studentA", "blog", SEPARATOR),
                "应拼接为 tenantId@basePluginId");
    }

    @Test
    void testBuildPluginIdWithEmptyTenant() {
        assertEquals("blog", TenantNaming.buildPluginId(null, "blog", SEPARATOR),
                "null 租户应返回基础插件 ID");
        assertEquals("blog", TenantNaming.buildPluginId("", "blog", SEPARATOR),
                "空租户应返回基础插件 ID");
    }

    @Test
    void testBuildPluginIdWithCustomSeparator() {
        assertEquals("tenant1::plugin1", TenantNaming.buildPluginId("tenant1", "plugin1", "::"),
                "应支持自定义分隔符");
    }
}
