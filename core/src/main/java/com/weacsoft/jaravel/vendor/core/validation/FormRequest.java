package com.weacsoft.jaravel.vendor.core.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laravel 风格 Form Request 基类。
 * <p>
 * 子类定义 {@link #rules()} 与可选的 {@link #messages()}、{@link #authorize()}，
 * 调用 {@link #validate(Map)} 完成校验。
 * <pre>
 * public class StoreUserRequest extends FormRequest {
 *     public Map&lt;String,String&gt; rules() {
 *         return Map.of("name", "required|string|min:2", "age", "required|integer|min:1|max:150");
 *     }
 * }
 * StoreUserRequest req = new StoreUserRequest();
 * Map&lt;String,Object&gt; data = req.validate(inputData);
 * </pre>
 */
public abstract class FormRequest {

    /**
     * 校验规则，key 为字段名，value 为规则串（如 {@code "required|string|min:2"}）。
     */
    public abstract Map<String, String> rules();

    /**
     * 自定义错误消息，key 形如 {@code "field.rule"}。
     */
    public Map<String, String> messages() {
        return Map.of();
    }

    /**
     * 授权检查，返回 false 时校验将抛出 {@link UnauthorizedException}。
     */
    public boolean authorize() {
        return true;
    }

    /**
     * 执行校验，成功返回已校验数据，失败抛 {@link ValidationException}。
     */
    public Map<String, Object> validate(Map<String, Object> data) {
        if (!authorize()) {
            throw new UnauthorizedException("Unauthorized");
        }
        return Validator.make(data, rules(), messages()).validate();
    }

    /**
     * 仅校验，不抛异常，返回是否通过。
     */
    public boolean isValid(Map<String, Object> data) {
        return Validator.make(data, rules(), messages()).passes();
    }

    /**
     * 预处理/过滤数据，默认原样返回。子类可重写以实现 only/except 等。
     */
    public Map<String, Object> prepare(Map<String, Object> data) {
        return data == null ? new LinkedHashMap<>() : data;
    }
}
