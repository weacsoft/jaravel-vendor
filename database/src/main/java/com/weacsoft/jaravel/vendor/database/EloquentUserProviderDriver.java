package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.auth.contract.UserProviderDriver;
import gaarason.database.contract.eloquent.Model;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Eloquent 用户提供者驱动，对齐 Laravel {@code EloquentUserProvider} 的工厂创建。
 * <p>
 * 支持 {@code "eloquent"} 驱动名，从配置中读取 Model 类名和凭证字段，
 * 通过 Spring 容器获取 Model 单例后创建 {@link EloquentUserProvider}。
 *
 * <h3>配置示例</h3>
 * <pre>
 * jaravel:
 *   auth:
 *     providers:
 *       users:
 *         driver: eloquent
 *         model: com.weacsoft.jaravel.app.model.User
 *         credential-field: number
 * </pre>
 *
 * <p>
 * Model 实例通过 {@link ApplicationContext#getBean(Class)} 获取（Spring 管理的单例），
 * 因此 Model 类必须标注 {@code @Repository} 或通过其他方式注册为 Spring Bean。
 */
public class EloquentUserProviderDriver implements UserProviderDriver {

    private final ApplicationContext applicationContext;

    public EloquentUserProviderDriver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean support(String driver) {
        return "eloquent".equalsIgnoreCase(driver);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public UserProvider create(Map<String, Object> config) {
        String modelClassName = (String) config.get("model");
        if (modelClassName == null || modelClassName.isEmpty()) {
            throw new IllegalStateException("eloquent provider 配置缺少 'model' 属性");
        }

        String credentialField = (String) config.get("credential-field");

        try {
            Class<?> modelClass = Class.forName(modelClassName);
            Model model = (Model) applicationContext.getBean(modelClass);
            if (credentialField != null && !credentialField.isEmpty()) {
                return new EloquentUserProvider(model, credentialField);
            }
            return new EloquentUserProvider(model);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 Model 类: " + modelClassName, e);
        }
    }
}
