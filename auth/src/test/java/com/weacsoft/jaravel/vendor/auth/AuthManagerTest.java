package com.weacsoft.jaravel.vendor.auth;

import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.guard.SessionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuthManager} 认证管理器测试。
 * <p>
 * 覆盖默认守卫、守卫注册与切换、请求级守卫缓存、GuardFactory 插件机制、
 * 未注册守卫抛异常，以及 login/logout/user/check 便捷方法。
 */
class AuthManagerTest {

    @AfterEach
    void clearThreadLocal() {
        // 清理 ThreadLocal，避免跨测试串态
        AuthContext.clear();
    }

    /** 简单可认证用户 */
    static class TestUser implements Authenticatable {
        private final Object id;
        TestUser(Object id) { this.id = id; }
        @Override
        public Object getAuthIdentifier() { return id; }
    }

    /** 内存用户提供者 */
    static class InMemoryProvider implements UserProvider {
        @Override
        public Authenticatable retrieveById(Object identifier) {
            if (identifier == null) return null;
            return new TestUser(identifier);
        }

        @Override
        public Authenticatable retrieveByCredentials(Map<String, Object> credentials) {
            Object id = credentials.get("id");
            return id == null ? null : new TestUser(id);
        }
    }

    @Test
    void testDefaultGuardName() {
        AuthManager manager = new AuthManager();
        assertEquals("web", manager.getDefaultGuard());
    }

    @Test
    void testRegisterSessionGuardAndResolve() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "session", "users");

        AuthGuard guard = manager.guard("web");
        assertNotNull(guard);
        assertTrue(guard instanceof SessionGuard, "session 驱动应创建 SessionGuard");
    }

    @Test
    void testDefaultGuardResolution() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "session", "users");

        AuthGuard defaultGuard = manager.guard();
        assertNotNull(defaultGuard);
        assertTrue(defaultGuard instanceof SessionGuard);
    }

    @Test
    void testGuardSwitching() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "session", "users");
        manager.registerGuard("api", "session", "users");

        AuthGuard webGuard = manager.guard("web");
        AuthGuard apiGuard = manager.guard("api");

        assertNotNull(webGuard);
        assertNotNull(apiGuard);
        assertNotSame(webGuard, apiGuard, "不同名称的守卫应是不同实例");

        // 切换默认守卫
        manager.setDefaultGuard("api");
        assertSame(apiGuard, manager.guard(), "默认守卫应切换为 api");
    }

    @Test
    void testGuardCachedPerThread() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "session", "users");

        AuthGuard first = manager.guard("web");
        AuthGuard second = manager.guard("web");
        assertSame(first, second, "同一请求内多次获取应返回同一实例");

        // clear 后应获得新实例
        manager.clear();
        AuthGuard third = manager.guard("web");
        assertNotSame(first, third, "clear 后应创建新实例");
    }

    @Test
    void testGuardFactoryPlugin() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("jwt", "jwt", "users");

        // 注册自定义 jwt 驱动工厂
        manager.registerGuardDriver("jwt", (name, provider, config) -> new StubTokenGuard());

        AuthGuard guard = manager.guard("jwt");
        assertNotNull(guard);
        assertTrue(guard instanceof StubTokenGuard, "应使用注册的 GuardFactory 创建守卫");
    }

    @Test
    void testUnregisteredGuardThrows() {
        AuthManager manager = new AuthManager();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.guard("unknown"));
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void testUnregisteredProviderThrows() {
        AuthManager manager = new AuthManager();
        // 注册了守卫但未注册提供者
        manager.registerGuard("web", "session", "missing-provider");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.guard("web"));
        assertTrue(ex.getMessage().contains("missing-provider"));
    }

    @Test
    void testUnknownDriverThrows() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "weird-driver", "users");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.guard("web"));
        assertTrue(ex.getMessage().contains("weird-driver"));
    }

    @Test
    void testLoginUserCheckLogoutViaDefaultGuard() {
        AuthManager manager = new AuthManager();
        manager.registerProvider("users", new InMemoryProvider());
        manager.registerGuard("web", "session", "users");

        // 初始未登录
        assertFalse(manager.check(), "初始应为未登录");
        assertTrue(manager.guest());

        // 登录（无请求上下文，仅缓存用户）
        TestUser user = new TestUser(1001L);
        manager.login(user);

        assertTrue(manager.check(), "login 后应已登录");
        assertFalse(manager.guest());
        assertNotNull(manager.user());
        assertEquals(1001L, manager.user().getAuthIdentifier());

        // id() 便捷方法
        assertEquals(1001L, manager.id());

        // 登出
        manager.logout();
        assertFalse(manager.check(), "logout 后应为未登录");
        assertNull(manager.user());
    }

    /** 用于测试 GuardFactory 的桩 Guard */
    static class StubTokenGuard implements AuthGuard {
        @Override
        public boolean check() { return false; }
        @Override
        public boolean guest() { return true; }
        @Override
        public Authenticatable user() { return null; }
        @Override
        public void login(Authenticatable user) { }
        @Override
        public void logout() { }
    }
}
