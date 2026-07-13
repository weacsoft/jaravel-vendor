package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

import com.weacsoft.jaravel.vendor.plugin.jar.integration.PluginIntegration;
import com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.SharedInterfaceDescriptor;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginBeanRegistrar;
import com.weacsoft.jaravel.vendor.plugin.jar.registrar.PluginRouteRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户感知的热插件管理器。
 * <p>
 * 继承 {@link HotPluginManager}，在 {@link #enablePlugin} 和 {@link #disablePlugin} 前后
 * 注入 {@link TenantContext}，使下游的 {@code TenantAwarePluginBeanRegistrar} 和
 * {@code TenantAwarePluginRouteRegistrar} 能感知当前租户并自动前缀化。
 * <p>
 * <h3>工作原理</h3>
 * <ol>
 *   <li>调用方使用带租户的 pluginId（如 {@code studentA@blog}）调用 {@link #enablePlugin}</li>
 *   <li>本类从 pluginId 提取租户 ID，通过 {@link TenantContext#setCurrentTenant} 注入 ThreadLocal</li>
 *   <li>调用 {@code super.enablePlugin()}，父类内部的 Bean 注册和路由注册读取 ThreadLocal 进行前缀化</li>
 *   <li>finally 块中 {@link TenantContext#clear()} 清理 ThreadLocal</li>
 * </ol>
 * <p>
 * <h3>向后兼容</h3>
 * 当 pluginId 不包含分隔符时（如 {@code blog}），{@link TenantNaming#extractTenant} 返回 null，
 * 不设置租户上下文，行为与父类完全一致。
 * <p>
 * <h3>跨插件服务获取</h3>
 * 重写 {@link #getServiceFromPlugin}，在查找 Bean 时使用前缀化的 Bean 名称，
 * 解决父类用原始名称查找导致 {@code NoSuchBeanDefinitionException} 的问题。
 * <p>
 * <h3>便捷 API</h3>
 * 提供 {@link #registerPluginForTenant}、{@link #enablePluginForTenant} 等便捷方法，
 * 自动拼接 {@code tenantId + separator + pluginId}。
 */
public class TenantAwareHotPluginManager extends HotPluginManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareHotPluginManager.class);

    /** Bean 注册器引用（父类 private，此处保留自有引用用于 getServiceFromPlugin） */
    private final PluginBeanRegistrar beanRegistrar;
    /** 租户分隔符 */
    private final String separator;

    /**
     * 构造租户感知的热插件管理器。
     *
     * @param pluginsDir     插件目录
     * @param beanRegistrar  Bean 注册器（应为 TenantAwarePluginBeanRegistrar）
     * @param routeRegistrar 路由注册器（应为 TenantAwarePluginRouteRegistrar）
     * @param persistence    元数据持久化
     * @param integration    jaravel 集成
     * @param autoRegister   是否自动注册路由
     * @param separator      租户分隔符（如 "@"）
     */
    public TenantAwareHotPluginManager(Path pluginsDir, PluginBeanRegistrar beanRegistrar,
                                       PluginRouteRegistrar routeRegistrar,
                                       MetadataPersistence persistence, PluginIntegration integration,
                                       boolean autoRegister, String separator) {
        super(pluginsDir, beanRegistrar, routeRegistrar, persistence, integration, autoRegister);
        this.beanRegistrar = beanRegistrar;
        this.separator = separator;
    }

    /**
     * 启用插件，自动注入租户上下文。
     * <p>
     * 从 pluginId 提取租户 ID，注入 {@link TenantContext} 后委托父类执行。
     * 父类内部的 Bean 注册和路由注册会读取上下文进行前缀化。
     *
     * @param pluginId 插件 ID（可含租户前缀，如 {@code studentA@blog}）
     * @return 启用成功返回 true
     */
    @Override
    public boolean enablePlugin(String pluginId) {
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            log.debug("启用租户插件: tenant={}, pluginId={}", tenantId, pluginId);
        }
        try {
            return super.enablePlugin(pluginId);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 禁用插件，自动注入租户上下文。
     * <p>
     * 禁用时需要租户上下文，因为 {@code TenantAwarePluginBeanRegistrar.unregisterBean}
     * 需要前缀化 Bean 名称才能正确注销。
     *
     * @param pluginId 插件 ID（可含租户前缀）
     * @return 禁用成功返回 true
     */
    @Override
    public boolean disablePlugin(String pluginId) {
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            log.debug("禁用租户插件: tenant={}, pluginId={}", tenantId, pluginId);
        }
        try {
            return super.disablePlugin(pluginId);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 从指定插件获取服务 Bean，自动使用前缀化的 Bean 名称查找。
     * <p>
     * 父类用原始 Bean 名称查找，但 Bean 实际以前缀化名称注册，会找不到。
     * 本方法将 Bean 名称前缀化后再查找。
     *
     * @param pluginId 插件 ID
     * @param beanName 原始 Bean 名称
     * @return Bean 实例，不存在返回 null
     */
    @Override
    public Object getServiceFromPlugin(String pluginId, String beanName) {
        PluginInfo info = getPlugin(pluginId);
        if (info == null || info.getState() != PluginInfo.State.ENABLED) {
            return null;
        }
        // registeredBeanNames 存储的是原始名称，校验用原始名称
        if (!info.getRegisteredBeanNames().contains(beanName)) {
            return null;
        }
        // 查找时使用前缀化名称
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        String lookupName = tenantId != null
                ? TenantNaming.prefixBeanName(tenantId, beanName)
                : beanName;
        try {
            return beanRegistrar.getApplicationContext().getBean(lookupName);
        } catch (Exception e) {
            log.warn("获取插件服务失败: pluginId={}, beanName={}", pluginId, beanName, e);
            return null;
        }
    }

    /**
     * 获取共享接口对应的 Bean 实例，自动使用前缀化的 Bean 名称查找。
     * <p>
     * 多租户模式下，Bean 实际以前缀化名称注册（如 "studentA:blogController"），
     * 因此查找时需要将 descriptor 中的原始 Bean 名称前缀化。
     *
     * @param descriptor 共享接口描述符
     * @return Bean 实例
     * @throws Exception 获取失败时抛出
     */
    @Override
    protected Object lookupSharedInterfaceBean(SharedInterfaceDescriptor descriptor) throws Exception {
        String tenantId = TenantNaming.extractTenant(descriptor.getPluginId(), separator);
        String lookupBeanName = (tenantId != null)
                ? TenantNaming.prefixBeanName(tenantId, descriptor.getBeanName())
                : descriptor.getBeanName();
        return beanRegistrar.getApplicationContext().getBean(lookupBeanName);
    }

    /**
     * 注销单条路由，自动使用前缀化的路径清理 routeHandler。
     * <p>
     * 父类用原始路径清理 routeHandler，但路由实际以前缀化路径注册，清理无效。
     * 本方法在父类执行后，额外用前缀化路径清理 routeHandler。
     *
     * @param pluginId   插件 ID
     * @param path       原始路由路径
     * @param httpMethod HTTP 方法名
     * @return 注销成功返回 true
     */
    @Override
    public boolean unregisterRoute(String pluginId, String path, String httpMethod) {
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        if (tenantId == null) {
            return super.unregisterRoute(pluginId, path, httpMethod);
        }
        // 父类用原始路径清理 routeMappings 和 persistence（routeHandler 清理无效但不影响正确性）
        boolean result = super.unregisterRoute(pluginId, path, httpMethod);
        if (result) {
            // 额外用前缀化路径清理 routeHandler
            String prefixedPath = TenantNaming.prefixRoutePath(tenantId, path);
            getRouteRegistrar().getRouteHandler().unregisterRouteInfo(httpMethod, prefixedPath);
        }
        return result;
    }

    /**
     * 为已注册的路由注册别名路径，自动注入租户上下文。
     * <p>
     * 在多租户模式下，需要注入租户上下文使路由注册器能前缀化路径。
     *
     * @param pluginId     插件 ID（可含租户前缀）
     * @param existingPath 已注册的原始路由路径
     * @param aliasPath    别名路由路径
     * @param httpMethod   HTTP 方法名
     * @return 注册成功返回 true
     */
    @Override
    public boolean registerRouteAlias(String pluginId, String existingPath,
                                       String aliasPath, String httpMethod) {
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
        }
        try {
            return super.registerRouteAlias(pluginId, existingPath, aliasPath, httpMethod);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 为已注册的路由注册别名路径（自动检测 HTTP 方法），自动注入租户上下文。
     */
    @Override
    public boolean registerRouteAlias(String pluginId, String existingPath, String aliasPath) {
        String tenantId = TenantNaming.extractTenant(pluginId, separator);
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
        }
        try {
            return super.registerRouteAlias(pluginId, existingPath, aliasPath);
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 便捷 API ====================

    /**
     * 为指定租户注册插件。
     * <p>
     * 将 pluginId 拼接为 {@code tenantId + separator + pluginId} 后委托父类。
     *
     * @param jarFile   JAR 文件路径
     * @param tenantId  租户 ID
     * @param pluginId  基础插件 ID
     * @param persist   是否持久化
     * @return 完整 pluginId
     */
    public String registerPluginForTenant(Path jarFile, String tenantId, String pluginId, boolean persist) {
        String fullPluginId = TenantNaming.buildPluginId(tenantId, pluginId, separator);
        return registerPluginFromPath(jarFile, fullPluginId, persist);
    }

    /**
     * 启用指定租户的插件。
     *
     * @param tenantId 租户 ID
     * @param pluginId 基础插件 ID
     * @return 启用成功返回 true
     */
    public boolean enablePluginForTenant(String tenantId, String pluginId) {
        return enablePlugin(TenantNaming.buildPluginId(tenantId, pluginId, separator));
    }

    /**
     * 禁用指定租户的插件。
     *
     * @param tenantId 租户 ID
     * @param pluginId 基础插件 ID
     * @return 禁用成功返回 true
     */
    public boolean disablePluginForTenant(String tenantId, String pluginId) {
        return disablePlugin(TenantNaming.buildPluginId(tenantId, pluginId, separator));
    }

    /**
     * 卸载指定租户的插件。
     *
     * @param tenantId 租户 ID
     * @param pluginId 基础插件 ID
     * @return 卸载成功返回 true
     */
    public boolean uninstallPluginForTenant(String tenantId, String pluginId) {
        return uninstallPlugin(TenantNaming.buildPluginId(tenantId, pluginId, separator));
    }

    /**
     * 获取指定租户的插件信息。
     *
     * @param tenantId 租户 ID
     * @param pluginId 基础插件 ID
     * @return 插件信息，不存在返回 null
     */
    public PluginInfo getPluginForTenant(String tenantId, String pluginId) {
        return getPlugin(TenantNaming.buildPluginId(tenantId, pluginId, separator));
    }

    /**
     * 获取指定租户的所有插件。
     * <p>
     * 遍历所有插件，筛选出 pluginId 以 {@code tenantId + separator} 开头的插件。
     *
     * @param tenantId 租户 ID
     * @return 该租户的插件列表
     */
    public List<PluginInfo> getPluginsByTenant(String tenantId) {
        String prefix = tenantId + separator;
        List<PluginInfo> result = new ArrayList<>();
        for (PluginInfo info : getAllPlugins()) {
            if (info.getPluginId() != null && info.getPluginId().startsWith(prefix)) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 返回租户分隔符。
     *
     * @return 分隔符
     */
    public String getSeparator() {
        return separator;
    }
}
