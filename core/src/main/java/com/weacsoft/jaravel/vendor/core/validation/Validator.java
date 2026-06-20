package com.weacsoft.jaravel.vendor.core.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel 风格校验器。
 * <pre>
 * Validator v = Validator.make(data, Map.of("name", "required|string|min:2", "age", "integer|min:1"));
 * if (v.fails()) {
 *     Map&lt;String,List&lt;String&gt;&gt; errors = v.errors();
 * }
 * Map&lt;String,Object&gt; valid = v.validate(); // 失败抛 ValidationException
 * </pre>
 */
public class Validator {

    private final Map<String, Object> data;
    private final Map<String, String> rules;
    private final Map<String, String> messages;
    private Map<String, List<String>> errors;

    private Validator(Map<String, Object> data, Map<String, String> rules, Map<String, String> messages) {
        this.data = data;
        this.rules = rules;
        this.messages = messages;
    }

    /**
     * 创建校验器。
     *
     * @param data     待校验数据
     * @param rules    规则，如 {@code "required|string|min:2"}
     * @param messages 自定义消息（key 形如 {@code "field.rule"}），可为 null
     */
    public static Validator make(Map<String, Object> data, Map<String, String> rules, Map<String, String> messages) {
        return new Validator(data, rules, messages == null ? Map.of() : messages);
    }

    public static Validator make(Map<String, Object> data, Map<String, String> rules) {
        return make(data, rules, null);
    }

    public boolean fails() {
        return !errors().isEmpty();
    }

    public boolean passes() {
        return !fails();
    }

    public Map<String, List<String>> errors() {
        if (errors != null) return errors;
        errors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String field = entry.getKey();
            Object value = data == null ? null : data.get(field);
            for (ParsedRule pr : parse(entry.getValue())) {
                boolean nullable = entry.getValue().contains("nullable");
                // null 值：仅 required 生效，其余跳过（除非显式 nullable 后由具体规则处理）
                if (value == null) {
                    if (pr.name.equals("required")) {
                        addError(field, pr, value);
                    }
                    continue;
                }
                if (!pr.rule.passes(field, value, pr.params, data)) {
                    addError(field, pr, value);
                }
                // nullable 仅用于占位，不参与实际判断
                if (nullable && pr.name.equals("nullable")) {
                    // no-op
                }
            }
        }
        return errors;
    }

    /**
     * 执行校验，失败抛 {@link ValidationException}，成功返回已校验数据。
     */
    public Map<String, Object> validate() {
        if (fails()) {
            throw new ValidationException(errors);
        }
        Map<String, Object> validated = new LinkedHashMap<>();
        if (data != null) {
            for (String field : rules.keySet()) {
                if (data.containsKey(field)) {
                    validated.put(field, data.get(field));
                }
            }
        }
        return validated;
    }

    private void addError(String field, ParsedRule pr, Object value) {
        String tpl = messages.getOrDefault(field + "." + pr.name, pr.rule.message());
        String msg = tpl
                .replace(":field", field)
                .replace(":value", value == null ? "" : String.valueOf(value))
                .replace(":param0", pr.params.length > 0 ? pr.params[0] : "");
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(msg);
    }

    /**
     * 解析规则串，如 {@code required|string|min:2|in:a,b}。
     */
    private List<ParsedRule> parse(String ruleStr) {
        List<ParsedRule> list = new ArrayList<>();
        if (ruleStr == null || ruleStr.isBlank()) return list;
        for (String part : ruleStr.split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String name;
            String[] params;
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                name = trimmed.substring(0, colon);
                String paramStr = trimmed.substring(colon + 1);
                params = paramStr.split(",");
            } else {
                name = trimmed;
                params = new String[0];
            }
            list.add(new ParsedRule(name, params, Rules.get(name, params)));
        }
        return list;
    }

    private record ParsedRule(String name, String[] params, Rule rule) {
    }
}
