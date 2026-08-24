package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

/**
 * 租户上下文（ThreadLocal）。
 * <p>
 * 在插件启用/禁用期间，由 {@code TenantAwareHotPluginManager} 将当前租户 ID 注入 ThreadLocal，
 * 使 {@code TenantAwarePluginBeanRegistrar} 和 {@code TenantAwarePluginRouteRegistrar}
 * 能够感知当前正在处理的租户，从而对 Bean 名称和路由路径进行前缀化。
 * <p>
 * 生命周期：
 * <ol>
 *   <li>{@code enablePlugin(pluginId)} 调用前：从 pluginId 中提取租户 ID，{@link #setCurrentTenant} 注入</li>
 *   <li>注册 Bean / 路由期间：{@link #getCurrentTenant} 读取</li>
 *   <li>{@code enablePlugin(pluginId)} 调用后（finally 块）：{@link #clear} 清理</li>
 * </ol>
 * <p>
 * 线程安全：基于 {@link ThreadLocal}，每个线程独立。由于 {@code HotPluginManager} 使用写锁串行化
 * 插件状态变更，同一时间只有一个线程在启用/禁用插件，不存在并发问题。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 设置当前租户 ID。
     *
     * @param tenantId 租户 ID，为 null 表示无租户（非多租户模式）
     */
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户 ID，无租户时返回 null
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * 清除当前线程的租户上下文。
     * <p>
     * 必须在 finally 块中调用，避免 ThreadLocal 泄漏。
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
