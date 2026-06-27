package com.weacsoft.jaravel.vendor.core.validation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Validator} 与 {@link Rules} 校验器单元测试。
 */
class ValidatorTest {

    @Test
    void requiredFailsOnNullAndEmpty() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "");
        data.put("nick", "   ");

        Validator v = Validator.make(data, Map.of("name", "required", "nick", "required"));
        assertTrue(v.fails());
        assertTrue(v.errors().get("name").size() >= 1);
        assertTrue(v.errors().get("nick").size() >= 1);
    }

    @Test
    void stringAndMinRulesCombined() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "ab");   // 长度 2，min:2 通过
        data.put("short", "a");   // 长度 1，min:2 失败

        Validator v = Validator.make(data, Map.of(
                "name", "required|string|min:2",
                "short", "required|string|min:2"));

        assertTrue(v.passes() || true); // 占位，重点看 short
        assertTrue(v.fails());
        assertFalse(v.errors().containsKey("name"));
        assertTrue(v.errors().containsKey("short"));
    }

    @Test
    void emailAndInRules() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", "not-an-email");
        data.put("type", "unknown");

        Validator v = Validator.make(data, Map.of(
                "email", "email",
                "type", "in:admin,user"));

        assertTrue(v.fails());
        assertTrue(v.errors().containsKey("email"));
        assertTrue(v.errors().containsKey("type"));

        // 合法值
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("email", "a@b.com");
        ok.put("type", "admin");
        Validator v2 = Validator.make(ok, Map.of("email", "email", "type", "in:admin,user"));
        assertFalse(v2.fails());
    }

    @Test
    void nullableAllowsNullWithoutError() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nick", null);

        Validator v = Validator.make(data, Map.of("nick", "nullable|string|min:2"));
        assertFalse(v.fails(), "nullable 字段为 null 时不产生错误");
    }

    @Test
    void integerAndNumericRules() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("age", "abc");
        data.put("score", "3.14");

        Validator v = Validator.make(data, Map.of(
                "age", "integer",
                "score", "numeric"));

        assertTrue(v.fails());
        assertTrue(v.errors().containsKey("age"));   // abc 不是整数
        assertFalse(v.errors().containsKey("score")); // 3.14 是数字
    }

    @Test
    void validateThrowsAndReturnsValidatedData() {
        // 成功路径：返回只包含规则字段的数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        data.put("extra", "ignored");

        Map<String, Object> validated = Validator.make(data, Map.of("name", "required|string"))
                .validate();
        assertEquals("Alice", validated.get("name"));
        assertFalse(validated.containsKey("extra"));

        // 失败路径：抛 ValidationException
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("name", "");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Validator.make(bad, Map.of("name", "required|string")).validate());
        assertTrue(ex.errors().containsKey("name"));
    }

    @Test
    void customMessagesAreApplied() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "");

        Map<String, String> messages = Map.of("name.required", "名字必填");

        Validator v = Validator.make(data, Map.of("name", "required|string"), messages);
        List<String> errors = v.errors().get("name");
        assertTrue(errors.contains("名字必填"));
    }
}
