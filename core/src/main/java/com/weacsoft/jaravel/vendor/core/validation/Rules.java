package com.weacsoft.jaravel.vendor.core.validation;

import java.util.Map;

/**
 * 内置校验规则集合，对齐 Laravel 常用规则。
 * 通过 {@link Rules#get(String, String[])} 按名称获取规则实例。
 */
public final class Rules {

    private Rules() {
    }

    /** 必填（非 null 且非空串） */
    public static final Rule REQUIRED = new Required();
    /** 可为 null（仅占位，校验恒通过；空值时跳过其它规则由 Validator 处理） */
    public static final Rule NULLABLE = (f, v, p, d) -> true;
    public static final Rule STRING = new StringRule();
    public static final Rule INTEGER = new IntegerRule();
    public static final Rule NUMERIC = new NumericRule();
    public static final Rule BOOLEAN = new BooleanRule();
    public static final Rule EMAIL = new EmailRule();
    public static final Rule ARRAY = new ArrayRule();

    public static Rule min(int min) {
        return new Min(min);
    }

    public static Rule max(int max) {
        return new Max(max);
    }

    public static Rule in(String... values) {
        return new In(values);
    }

    public static Rule notIn(String... values) {
        return new NotIn(values);
    }

    /**
     * 按规则名与参数构造规则。支持 required/nullable/string/integer/numeric/boolean/email/array
     * 以及带参数的 min:N / max:N / in:a,b,c / not_in:a,b。
     */
    public static Rule get(String name, String[] params) {
        return switch (name) {
            case "required" -> REQUIRED;
            case "nullable" -> NULLABLE;
            case "string" -> STRING;
            case "integer", "int" -> INTEGER;
            case "numeric" -> NUMERIC;
            case "boolean", "bool" -> BOOLEAN;
            case "email" -> EMAIL;
            case "array" -> ARRAY;
            case "min" -> new Min(Integer.parseInt(params[0]));
            case "max" -> new Max(Integer.parseInt(params[0]));
            case "in" -> new In(params);
            case "not_in" -> new NotIn(params);
            default -> throw new IllegalArgumentException("未知的校验规则: " + name);
        };
    }

    // ---- 规则实现 ----

    public static final class Required implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return false;
            if (value instanceof String s) return !s.trim().isEmpty();
            if (value instanceof java.util.Collection<?> c) return !c.isEmpty();
            if (value instanceof Map<?, ?> m) return !m.isEmpty();
            return true;
        }

        @Override
        public String message() {
            return "The :field field is required.";
        }
    }

    public static final class StringRule implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            return value == null || value instanceof String;
        }

        @Override
        public String message() {
            return "The :field must be a string.";
        }
    }

    public static final class IntegerRule implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            if (value instanceof Number) return true;
            try {
                Long.parseLong(String.valueOf(value));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String message() {
            return "The :field must be an integer.";
        }
    }

    public static final class NumericRule implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            try {
                Double.parseDouble(String.valueOf(value));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String message() {
            return "The :field must be a number.";
        }
    }

    public static final class BooleanRule implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            String s = String.valueOf(value).trim().toLowerCase();
            return s.equals("true") || s.equals("false") || s.equals("0") || s.equals("1");
        }

        @Override
        public String message() {
            return "The :field field must be true or false.";
        }
    }

    public static final class EmailRule implements Rule {
        private static final java.util.regex.Pattern P =
                java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            return value == null || (value instanceof String s && P.matcher(s).matches());
        }

        @Override
        public String message() {
            return "The :field must be a valid email address.";
        }
    }

    public static final class ArrayRule implements Rule {
        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            return value == null || value instanceof java.util.Collection<?> || value instanceof Map<?, ?>;
        }

        @Override
        public String message() {
            return "The :field must be an array.";
        }
    }

    public static final class Min implements Rule {
        private final int min;

        public Min(int min) {
            this.min = min;
        }

        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            if (value instanceof String s) return s.length() >= min;
            if (value instanceof java.util.Collection<?> c) return c.size() >= min;
            if (value instanceof Number n) return n.doubleValue() >= min;
            return String.valueOf(value).length() >= min;
        }

        @Override
        public String message() {
            return "The :field must be at least :param0 characters.";
        }
    }

    public static final class Max implements Rule {
        private final int max;

        public Max(int max) {
            this.max = max;
        }

        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            if (value instanceof String s) return s.length() <= max;
            if (value instanceof java.util.Collection<?> c) return c.size() <= max;
            if (value instanceof Number n) return n.doubleValue() <= max;
            return String.valueOf(value).length() <= max;
        }

        @Override
        public String message() {
            return "The :field may not be greater than :param0 characters.";
        }
    }

    public static final class In implements Rule {
        private final String[] values;

        public In(String[] values) {
            this.values = values;
        }

        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            String s = String.valueOf(value);
            for (String v : values) if (v.equals(s)) return true;
            return false;
        }

        @Override
        public String message() {
            return "The selected :field is invalid.";
        }
    }

    public static final class NotIn implements Rule {
        private final String[] values;

        public NotIn(String[] values) {
            this.values = values;
        }

        @Override
        public boolean passes(String field, Object value, String[] params, Map<String, Object> data) {
            if (value == null) return true;
            String s = String.valueOf(value);
            for (String v : values) if (v.equals(s)) return false;
            return true;
        }

        @Override
        public String message() {
            return "The :field field is invalid.";
        }
    }
}
