package com.weacsoft.jaravel.vendor.plugin.jar.multitenant;

/**
 * 租户命名工具。
 * <p>
 * 提供统一的 Bean 名称前缀化和路由路径前缀化逻辑，
 * 供 {@code TenantAwarePluginBeanRegistrar} 和 {@code TenantAwarePluginRouteRegistrar} 共用，
 * 确保两者使用一致的命名规则。
 * <p>
 * 命名规则：
 * <ul>
 *   <li>pluginId 格式：{@code tenantId + separator + basePluginId}，如 {@code studentA@blog}</li>
 *   <li>Bean 名称前缀：{@code tenantId + ":" + beanName}，如 {@code studentA:blogController}</li>
 *   <li>路由路径前缀：{@code "/" + tenantId + path}，如 {@code /studentA/blog/list}</li>
 * </ul>
 */
public final class TenantNaming {

    private TenantNaming() {
    }

    /**
     * 从 pluginId 中提取租户 ID。
     * <p>
     * pluginId 格式为 {@code tenantId + separator + basePluginId}。
     * 若 pluginId 不包含分隔符，返回 null（表示非多租户插件）。
     *
     * @param pluginId  插件 ID
     * @param separator 租户分隔符（如 "@"）
     * @return 租户 ID，无租户时返回 null
     */
    public static String extractTenant(String pluginId, String separator) {
        if (pluginId == null || separator == null || separator.isEmpty()) {
            return null;
        }
        int idx = pluginId.indexOf(separator);
        if (idx <= 0 || idx >= pluginId.length() - separator.length()) {
            return null;
        }
        return pluginId.substring(0, idx);
    }

    /**
     * 从 pluginId 中提取基础插件 ID（去除租户前缀）。
     *
     * @param pluginId  插件 ID
     * @param separator 租户分隔符
     * @return 基础插件 ID，无租户前缀时返回原值
     */
    public static String extractBasePluginId(String pluginId, String separator) {
        if (pluginId == null || separator == null || separator.isEmpty()) {
            return pluginId;
        }
        int idx = pluginId.indexOf(separator);
        if (idx <= 0 || idx >= pluginId.length() - separator.length()) {
            return pluginId;
        }
        return pluginId.substring(idx + separator.length());
    }

    /**
     * 构造租户前缀化的 Bean 名称。
     * <p>
     * 如 tenantId="studentA", beanName="blogController" → "studentA:blogController"
     *
     * @param tenantId 租户 ID
     * @param beanName 原始 Bean 名称
     * @return 前缀化的 Bean 名称
     */
    public static String prefixBeanName(String tenantId, String beanName) {
        if (tenantId == null || tenantId.isEmpty()) {
            return beanName;
        }
        return tenantId + ":" + beanName;
    }

    /**
     * 构造租户前缀化的路由路径。
     * <p>
     * 如 tenantId="studentA", path="/blog/list" → "/studentA/blog/list"
     *
     * @param tenantId 租户 ID
     * @param path     原始路由路径
     * @return 前缀化的路由路径
     */
    public static String prefixRoutePath(String tenantId, String path) {
        if (tenantId == null || tenantId.isEmpty()) {
            return path;
        }
        if (path == null || path.isEmpty()) {
            return "/" + tenantId;
        }
        if (path.startsWith("/")) {
            return "/" + tenantId + path;
        }
        return "/" + tenantId + "/" + path;
    }

    /**
     * 构造完整的 pluginId（租户 + 分隔符 + 基础插件 ID）。
     *
     * @param tenantId      租户 ID
     * @param basePluginId  基础插件 ID
     * @param separator     租户分隔符
     * @return 完整 pluginId
     */
    public static String buildPluginId(String tenantId, String basePluginId, String separator) {
        if (tenantId == null || tenantId.isEmpty()) {
            return basePluginId;
        }
        return tenantId + separator + basePluginId;
    }
}
