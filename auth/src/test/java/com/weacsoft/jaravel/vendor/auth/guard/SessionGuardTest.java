package com.weacsoft.jaravel.vendor.auth.guard;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link SessionGuard} 测试。
 * <p>
 * 分两部分：
 * <ul>
 *   <li>无请求上下文时的缓存用户基本逻辑（login/user/check/logout）；</li>
 *   <li>有请求上下文时与 HTTP Session 的交互（login 写入、user 读取、logout 移除），
 *       使用 Mockito 模拟 HttpServletRequest / HttpSession。</li>
 * </ul>
 */
class SessionGuardTest {

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    static class TestUser implements Authenticatable {
        private final Object id;
        TestUser(Object id) { this.id = id; }
        @Override
        public Object getAuthIdentifier() { return id; }
    }

    static class StubProvider implements UserProvider {
        @Override
        public Authenticatable retrieveById(Object identifier) {
            return identifier == null ? null : new TestUser(identifier);
        }
        @Override
        public Authenticatable retrieveByCredentials(Map<String, Object> credentials) {
            return null;
        }
    }

    // ==================== 无请求上下文：缓存用户基本逻辑 ====================

    @Test
    void testGuestWhenNoUserAndNoRequest() {
        SessionGuard guard = new SessionGuard("web", new StubProvider());
        assertTrue(guard.guest());
        assertFalse(guard.check());
        assertNull(guard.user());
    }

    @Test
    void testLoginCheckLogoutWithoutRequest() {
        SessionGuard guard = new SessionGuard("web", new StubProvider());

        TestUser user = new TestUser(2001L);
        guard.login(user);

        assertTrue(guard.check());
        assertNotNull(guard.user());
        assertEquals(2001L, guard.user().getAuthIdentifier());

        guard.logout();
        assertFalse(guard.check());
        assertNull(guard.user());
    }

    @Test
    void testIdReturnsNullWhenNotLoggedIn() {
        SessionGuard guard = new SessionGuard("web", new StubProvider());
        assertNull(guard.id());
    }

    @Test
    void testTokenIsNullByDefault() {
        SessionGuard guard = new SessionGuard("web", new StubProvider());
        assertNull(guard.token(), "SessionGuard 不签发 token，应返回 null");
    }

    // ==================== 有请求上下文：Session 交互 ====================

    @Test
    void testLoginWritesToSessionAndUserReadsFromSession() {
        // 准备 mock servlet request 与 session
        HttpSession sessionMock = Mockito.mock(HttpSession.class);
        when(sessionMock.getAttributeNames()).thenReturn(Collections.enumeration(Collections.emptyList()));

        HttpServletRequest servletMock = Mockito.mock(HttpServletRequest.class);
        when(servletMock.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(servletMock.getCookies()).thenReturn(null);
        when(servletMock.getSession(false)).thenReturn(sessionMock);
        when(servletMock.getSession(true)).thenReturn(sessionMock);

        Request req = new Request();
        req.setRequest(servletMock);
        AuthContext.set(req);

        SessionGuard guard = new SessionGuard("web", new StubProvider());

        // login 应将用户标识写入 session
        TestUser user = new TestUser(3001L);
        guard.login(user);
        verify(sessionMock).setAttribute("login_web_id", 3001L);

        // login 后缓存生效，check 返回 true
        assertTrue(guard.check());
        assertEquals(3001L, guard.user().getAuthIdentifier());
    }

    @Test
    void testUserRetrievesFromSessionWhenNotResolved() {
        HttpSession sessionMock = Mockito.mock(HttpSession.class);
        // session 中已存在登录标识
        when(sessionMock.getAttribute("login_web_id")).thenReturn(4001L);
        when(sessionMock.getAttributeNames()).thenReturn(Collections.enumeration(Collections.emptyList()));

        HttpServletRequest servletMock = Mockito.mock(HttpServletRequest.class);
        when(servletMock.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(servletMock.getCookies()).thenReturn(null);
        when(servletMock.getSession(false)).thenReturn(sessionMock);

        Request req = new Request();
        req.setRequest(servletMock);
        AuthContext.set(req);

        SessionGuard guard = new SessionGuard("web", new StubProvider());

        // 首次 user() 应从 session 读取并通过 provider 还原用户
        Authenticatable user = guard.user();
        assertNotNull(user);
        assertEquals(4001L, user.getAuthIdentifier());
        verify(sessionMock).getAttribute("login_web_id");
    }

    @Test
    void testLogoutRemovesFromSession() {
        HttpSession sessionMock = Mockito.mock(HttpSession.class);
        when(sessionMock.getAttributeNames()).thenReturn(Collections.enumeration(Collections.emptyList()));

        HttpServletRequest servletMock = Mockito.mock(HttpServletRequest.class);
        when(servletMock.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(servletMock.getCookies()).thenReturn(null);
        when(servletMock.getSession(false)).thenReturn(sessionMock);
        when(servletMock.getSession(true)).thenReturn(sessionMock);

        Request req = new Request();
        req.setRequest(servletMock);
        AuthContext.set(req);

        SessionGuard guard = new SessionGuard("web", new StubProvider());
        guard.login(new TestUser(5001L));
        assertTrue(guard.check());

        // logout 应从 session 移除登录标识
        guard.logout();
        verify(sessionMock).removeAttribute("login_web_id");
        assertFalse(guard.check());
    }

    @Test
    void testUserReturnsNullWhenNoSession() {
        HttpServletRequest servletMock = Mockito.mock(HttpServletRequest.class);
        when(servletMock.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(servletMock.getCookies()).thenReturn(null);
        // getSession(false) 返回 null（无活动会话）
        when(servletMock.getSession(false)).thenReturn(null);

        Request req = new Request();
        req.setRequest(servletMock);
        AuthContext.set(req);

        SessionGuard guard = new SessionGuard("web", new StubProvider());
        assertNull(guard.user(), "无 session 时 user 应返回 null");
        assertFalse(guard.check());
    }
}
