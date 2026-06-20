package com.weacsoft.jaravel.vendor.core.validation;

import java.util.Map;

/**
 * 校验规则契约，对齐 Laravel 的 Rule。
 * <p>
 * 每个规则判断给定值是否通过，并返回错误消息模板。
 */
@FunctionalInterface
public interface Rule {

    /**
     * 判断是否通过校验。
     *
     * @param field     字段名
     * @param value     字段值
     * @param params    规则参数（如 min:1 中的 ["1"]）
     * @param data      全部数据
     * @return true 表示通过
     */
    boolean passes(String field, Object value, String[] params, Map<String, Object> data);

    /**
     * 错误消息模板，可用 {@code :field} {@code :value} {@code :param0} 占位。
     */
    default String message() {
        return "The :field field is invalid.";
    }
}
