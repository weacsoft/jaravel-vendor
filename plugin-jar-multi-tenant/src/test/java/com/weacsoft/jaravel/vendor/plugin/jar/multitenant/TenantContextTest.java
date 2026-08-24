package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantContext 租户上下文（ThreadLocal）测试。
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testInitialTenantIsNull() {
        TenantContext.clear();
        assertNull(TenantContext.getCurrentTenant(), "初始状态下租户应为 null");
    }

    @Test
    void testSetAndGetTenant() {
        TenantContext.setCurrentTenant("studentA");
        assertEquals("studentA", TenantContext.getCurrentTenant());
    }

    @Test
    void testSetNullTenant() {
        TenantContext.setCurrentTenant("tenant1");
        TenantContext.setCurrentTenant(null);
        assertNull(TenantContext.getCurrentTenant(), "设置为 null 后应返回 null");
    }

    @Test
    void testClearRemovesTenant() {
        TenantContext.setCurrentTenant("studentA");
        assertEquals("studentA", TenantContext.getCurrentTenant());
        TenantContext.clear();
        assertNull(TenantContext.getCurrentTenant(), "clear 后租户应为 null");
    }
}
