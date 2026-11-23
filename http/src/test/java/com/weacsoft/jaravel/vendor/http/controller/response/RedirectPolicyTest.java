package com.weacsoft.jaravel.vendor.http.controller.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RedirectPolicy} 重定向目标结构校验测试。
 * <p>
 * 覆盖放行项（相对路径、http/https 绝对地址）与拒绝项（可执行协议、协议相对写法、
 * 控制字符、反斜杠、空地址）。
 */
class RedirectPolicyTest {

    @Test
    void testRelativePathAccepted() {
        assertEquals("/login", RedirectPolicy.validate("/login"));
        assertEquals("/admin/users?page=2", RedirectPolicy.validate("/admin/users?page=2"));
    }

    @Test
    void testHttpAndHttpsAccepted() {
        assertEquals("https://example.com/home", RedirectPolicy.validate("https://example.com/home"));
        assertEquals("http://example.com/home", RedirectPolicy.validate("http://example.com/home"));
    }

    @Test
    void testSurroundingWhitespaceTrimmed() {
        assertEquals("/home", RedirectPolicy.validate("  /home  "));
    }

    @Test
    void testNullRejected() {
        assertThrows(IllegalArgumentException.class, () -> RedirectPolicy.validate(null));
    }

    @Test
    void testEmptyRejected() {
        assertThrows(IllegalArgumentException.class, () -> RedirectPolicy.validate(""));
        assertThrows(IllegalArgumentException.class, () -> RedirectPolicy.validate("   "));
    }

    @Test
    void testScriptProtocolRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("javascript:alert(document.cookie)"));
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("data:text/html;base64,PHNjcmlwdD4x"));
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("vbscript:msgbox(1)"));
    }

    @Test
    void testProtocolRelativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> RedirectPolicy.validate("//evil.example.com"));
    }

    @Test
    void testBackslashRejected() {
        // 浏览器把 \ 当作 /，/\evil.example.com 等价于 //evil.example.com
        assertThrows(IllegalArgumentException.class, () -> RedirectPolicy.validate("/\\evil.example.com"));
    }

    @Test
    void testControlCharacterRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("/home\r\nX-Injected: 1"));
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("/home\nX-Injected: 1"));
        assertThrows(IllegalArgumentException.class,
                () -> RedirectPolicy.validate("/home\u0000"));
    }

    @Test
    void testColonInPathSegmentNotTreatedAsScheme() {
        // 路径中的冒号不是协议分隔符，应放行
        assertEquals("/redirect/urn:isbn:123", RedirectPolicy.validate("/redirect/urn:isbn:123"));
    }

    @Test
    void testUppercaseSchemeAccepted() {
        assertEquals("HTTPS://example.com/home", RedirectPolicy.validate("HTTPS://example.com/home"));
    }
}
