package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.contract.eloquent.Record;

import java.util.Map;

/**
 * 基于 gaarason/database-all 的用户提供者，对齐 Laravel 的 {@code EloquentUserProvider}。
 * <p>
 * 仅负责通过 Eloquent Model 按主键 / 凭证<b>取出</b>用户，<b>不</b>负责校验密码。
 * 密码校验是应用层的责任（在 Controller / Service 中完成），因此本类不再包含
 * {@code CredentialMatcher} 与 {@code validateCredentials}。
 * <p>
 * 认证流程：
 * <pre>
 * // 1. 应用层按凭证查出用户
 * User user = (User) provider.retrieveByCredentials(Map.of("number", "1001"));
 * // 2. 应用层自行校验密码（生产环境应使用 BCrypt）
 * if (user == null || !encoder.matches(inputPassword, user.getPassword())) {
 *     throw new RuntimeException("工号或密码错误");
 * }
 * // 3. 登入（Auth 以主键比对，不涉及密码）
 * Auth.login(user);
 * </pre>
 *
 * @param <T> 用户实体类型（需实现 {@link Authenticatable}）
 * @param <K> 主键类型
 */
public class EloquentUserProvider<T extends Authenticatable, K> implements UserProvider {

    private final Model<?, T, K> model;
    private final String credentialField;

    /**
     * @param model           Eloquent Model（Spring 单例）
     * @param credentialField 凭证字段名（如 {@code "number"}），用于 {@link #retrieveByCredentials}
     */
    public EloquentUserProvider(Model<?, T, K> model, String credentialField) {
        this.model = model;
        this.credentialField = credentialField;
    }

    @Override
    public Authenticatable retrieveById(Object identifier) {
        Record<T, K> record = model.find(identifier);
        return record == null ? null : record.toObject();
    }

    @Override
    public Authenticatable retrieveByCredentials(Map<String, Object> credentials) {
        Object value = credentials.get(credentialField);
        if (value == null) {
            return null;
        }
        Record<T, K> record = model.newQuery().where(credentialField, value).first();
        return record == null ? null : record.toObject();
    }
}
