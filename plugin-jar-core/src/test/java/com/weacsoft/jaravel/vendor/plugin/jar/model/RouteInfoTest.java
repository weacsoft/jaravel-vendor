package com.weacsoft.jaravel.vendor.plugin.jar.model;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RouteInfo} 路由信息模型单元测试（equals/hashCode/toString）。
 */
class RouteInfoTest {

    @Test
    void equalsAndHashCodeBasedOnPathMethodBeanMethod() {
        RouteInfo r1 = new RouteInfo("/users/{id}", HttpMethod.GET, "userController", "show", "application/json");
        RouteInfo r2 = new RouteInfo("/users/{id}", HttpMethod.GET, "userController", "show", "text/plain");

        // produces 不参与 equals/hashCode
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void differsWhenKeyFieldsDiffer() {
        RouteInfo base = new RouteInfo("/users", HttpMethod.GET, "c", "list", null);
        assertNotEquals(base, new RouteInfo("/orders", HttpMethod.GET, "c", "list", null));
        assertNotEquals(base, new RouteInfo("/users", HttpMethod.POST, "c", "list", null));
        assertNotEquals(base, new RouteInfo("/users", HttpMethod.GET, "other", "list", null));
        assertNotEquals(base, new RouteInfo("/users", HttpMethod.GET, "c", "page", null));
        assertNotEquals(base, null);
        assertNotEquals(base, "not a route");
        assertEquals(base, base);
    }

    @Test
    void toStringFormatsMethodPathAndHandler() {
        RouteInfo r = new RouteInfo("/users/{id}", HttpMethod.DELETE, "userController", "destroy", null);
        String s = r.toString();
        assertTrue(s.contains("DELETE"));
        assertTrue(s.contains("/users/{id}"));
        assertTrue(s.contains("userController.destroy()"));
    }

    @Test
    void toStringHandlesNullMethodGracefully() {
        RouteInfo r = new RouteInfo("/ping", null, "c", "ping", null);
        // method 为 null 时以 "?" 显示，不抛异常
        assertTrue(r.toString().startsWith("? "));
    }

    @Test
    void gettersAndSettersRoundTrip() {
        RouteInfo r = new RouteInfo();
        r.setPath("/health");
        r.setMethod(HttpMethod.HEAD);
        r.setBeanName("h");
        r.setMethodName("check");
        r.setProduces("text/plain");

        assertEquals("/health", r.getPath());
        assertEquals(HttpMethod.HEAD, r.getMethod());
        assertEquals("h", r.getBeanName());
        assertEquals("check", r.getMethodName());
        assertEquals("text/plain", r.getProduces());
    }

    @Test
    void hashCodeConsistentWithEqualsForHashSetUsage() {
        RouteInfo r1 = new RouteInfo("/a", HttpMethod.GET, "c", "m", null);
        RouteInfo r2 = new RouteInfo("/a", HttpMethod.GET, "c", "m", "application/json");

        java.util.Set<RouteInfo> set = new java.util.HashSet<>();
        assertTrue(set.add(r1));
        assertFalse(set.add(r2));   // 相等，无法再次加入
        assertEquals(1, set.size());
    }
}
