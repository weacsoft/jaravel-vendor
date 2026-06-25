package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Set;

/**
 * 租户感知的插件 Bean 注册器。
 * <p>
 * 继承 {@link PluginBeanRegistrar}，在注册/注销 Bean 时根据 {@link TenantContext}
 * 自动对 Bean 名称添加租户前缀，避免不同租户的同名 Bean 互相覆盖。
 * <p>
 * 命名规则：{@code tenantId:originalBeanName}，如 {@code studentA:blogController}。
 * <p>
 * 当 {@link TenantContext#getCurrentTenant()} 返回 null 时（非多租户插件），
 * 行为与父类完全一致，保持向后兼容。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>{@code TenantAwareHotPluginManager.enablePlugin()} 从 pluginId 提取租户 ID，
 *       通过 {@link TenantContext#setCurrentTenant} 注入 ThreadLocal</li>
 *   <li>父类 {@code HotPluginManager.enablePlugin()} 调用 {@link #registerBean}</li>
 *   <li>本类读取 ThreadLocal 中的租户 ID，对 Bean 名称前缀化后委托父类注册</li>
 *   <li>父类将<b>原始</b> Bean 名称存入 {@code PluginInfo.registeredBeanNames}</li>
 *   <li>禁用时，父类传入原始 Bean 名称调用 {@link #unregisterBean}</li>
 *   <li>本类再次读取租户 ID，前缀化后委托父类注销</li>
 * </ol>
 */
public class TenantAwarePluginBeanRegistrar extends PluginBeanRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TenantAwarePluginBeanRegistrar.class);

    /**
     * 构造租户感知的 Bean 注册器。
     *
     * @param applicationContext Spring 应用上下文
     */
    public TenantAwarePluginBeanRegistrar(ConfigurableApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 注册 Bean，自动添加租户前缀。
     * <p>
     * 若当前线程有租户上下文，将 Bean 名称前缀化为 {@code tenantId:beanName} 后委托父类注册。
     * 否则直接委托父类（向后兼容）。
     *
     * @param beanName  原始 Bean 名称
     * @param beanClass Bean 类
     * @return 注册成功返回 true
     */
    @Override
    public boolean registerBean(String beanName, Class<?> beanClass) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return super.registerBean(beanName, beanClass);
        }
        String prefixedName = TenantNaming.prefixBeanName(tenantId, beanName);
        log.debug("租户 Bean 注册: {} -> {}", beanName, prefixedName);
        return super.registerBean(prefixedName, beanClass);
    }

    /**
     * 注销 Bean，自动添加租户前缀。
     * <p>
     * 传入的 beanName 是原始名称（来自 {@code PluginInfo.registeredBeanNames}），
     * 本类根据当前租户上下文前缀化后委托父类注销。
     *
     * @param beanName 原始 Bean 名称
     */
    @Override
    public void unregisterBean(String beanName) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            super.unregisterBean(beanName);
            return;
        }
        String prefixedName = TenantNaming.prefixBeanName(tenantId, beanName);
        log.debug("租户 Bean 注销: {} -> {}", beanName, prefixedName);
        super.unregisterBean(prefixedName);
    }

    /**
     * 批量注销 Bean，自动添加租户前缀。
     *
     * @param beanNames 原始 Bean 名称集合
     */
    @Override
    public void unregisterBeans(Set<String> beanNames) {
        if (beanNames == null || beanNames.isEmpty()) {
            return;
        }
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            super.unregisterBeans(beanNames);
            return;
        }
        for (String beanName : beanNames) {
            unregisterBean(beanName);
        }
    }
}
