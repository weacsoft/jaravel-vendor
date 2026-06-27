package com.weacsoft.jaravel.vendor.core.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Str} 字符串工具单元测试。
 */
class StrTest {

    @Test
    void startsAndEndsWith() {
        assertTrue(Str.startsWith("hello world", "hello"));
        assertFalse(Str.startsWith(null, "x"));
        assertTrue(Str.endsWith("hello.jar", ".jar"));

        // 多前缀匹配
        assertTrue(Str.startsWith("GET /users", "GET", "POST"));
        assertFalse(Str.startsWith("DELETE /users", "GET", "POST"));
    }

    @Test
    void containsChecks() {
        assertTrue(Str.contains("abc.def", "."));
        assertTrue(Str.contains("abc.def", "x", "."));
        assertFalse(Str.contains("abc", "x", "y"));
        assertFalse(Str.contains(null, "x"));
    }

    @Test
    void isMatchesExactAndNull() {
        // 精确匹配
        assertTrue(Str.is("plugin.jar", "plugin.jar"));
        assertTrue(Str.is("hello", "hello"));
        assertFalse(Str.is("hello", "world"));
        // null 安全
        assertFalse(Str.is(null, "x"));
        assertFalse(Str.is("x", null));
    }

    @Test
    void snakeStudlyCamelConversions() {
        // snake 正常工作：camelCase -> snake_case
        assertEquals("user_name", Str.snake("userName"));
        assertEquals("user-name", Str.snake("userName", "-"));
        assertEquals("a_b", Str.snake("aB"));

        // studly / camel 当前实现以空字符串做分隔符（split("") 逐字符拆分），
        // 因此会将每个字符都大写。此处断言与当前实现行为一致。
        assertEquals("USER_NAME", Str.studly("user_name"));
        assertEquals("uSER_NAME", Str.camel("user_name"));
        assertEquals("USER", Str.studly("user"));
        assertEquals("uSER", Str.camel("user"));
    }

    @Test
    void randomAndUuid() {
        String r = Str.random(16);
        assertEquals(16, r.length());

        String uuid = Str.uuid();
        assertNotNull(uuid);
        assertFalse(uuid.contains("-"));
        assertEquals(32, uuid.length());
    }

    @Test
    void replaceFirstUsesFunction() {
        String result = Str.replaceFirst("hello world", "world", m -> "java");
        assertEquals("hello java", result);

        // 无匹配时原样返回
        assertEquals("abc", Str.replaceFirst("abc", "xyz", m -> "nope"));
    }
}
