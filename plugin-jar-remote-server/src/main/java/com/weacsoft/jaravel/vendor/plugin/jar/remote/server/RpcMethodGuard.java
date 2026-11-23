package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 远程调用的方法访问控制。
 * <p>
 * 远程执行端点按「Bean 名 + 方法名」反射调用目标方法，持有有效令牌的调用方
 * 原则上可以触达容器内任意 Bean 的任意 public 方法。本类提供可选的访问控制：
 * <ul>
 *   <li><b>allowedBeanNames</b>：允许调用的 Bean 名白名单；为空表示不按 Bean 限制。</li>
 *   <li><b>allowedMethods</b>：允许调用的方法名白名单；为空表示不按方法限制。</li>
 * </ul>
 * 两项均为空时保持与既有版本一致的行为（不限制），但线程控制类方法
 * （{@code wait}/{@code notify}/{@code notifyAll}）在任何配置下都拒绝，
 * 因为它们可被用于干扰 Bean 自身的同步状态。
 * <p>
 * 白名单按名称匹配，忽略大小写；Bean 名同时与 {@code pluginId#beanName} 形式比较，
 * 以便在多插件场景下区分同名 Bean。
 */
public final class RpcMethodGuard {

    /** 始终拒绝的方法名：可改变目标对象线程状态。 */
    private static final Set<String> DENIED_METHODS = Set.of("wait", "notify", "notifyall");

    private final Set<String> allowedBeanNames;
    private final Set<String> allowedMethods;

    private RpcMethodGuard(Set<String> allowedBeanNames, Set<String> allowedMethods) {
        this.allowedBeanNames = allowedBeanNames;
        this.allowedMethods = allowedMethods;
    }

    /**
     * 创建访问控制。
     *
     * @param allowedBeanNames 允许的 Bean 名（可为空表示不限制）
     * @param allowedMethods  允许的方法名（可为空表示不限制）
     * @return 访问控制实例
     */
    public static RpcMethodGuard of(List<String> allowedBeanNames, List<String> allowedMethods) {
        return new RpcMethodGuard(normalize(allowedBeanNames), normalize(allowedMethods));
    }

    /**
     * 创建不限制 Bean 与方法名的访问控制（仍拒绝线程控制类方法）。
     *
     * @return 访问控制实例
     */
    public static RpcMethodGuard unrestricted() {
        return new RpcMethodGuard(Set.of(), Set.of());
    }

    private static Set<String> normalize(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                result.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 判断一次远程调用是否被允许。
     *
     * @param pluginId   插件 ID（可为 null）
     * @param beanName   Bean 名
     * @param methodName 方法名
     * @return 允许返回 true；否则返回 false
     */
    public boolean isAllowed(String pluginId, String beanName, String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return false;
        }
        String method = methodName.trim().toLowerCase(Locale.ROOT);
        if (DENIED_METHODS.contains(method)) {
            return false;
        }
        if (!allowedMethods.isEmpty() && !allowedMethods.contains(method)) {
            return false;
        }
        if (allowedBeanNames.isEmpty()) {
            return true;
        }
        if (beanName != null && allowedBeanNames.contains(beanName.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (pluginId != null && !pluginId.isBlank() && beanName != null) {
            String qualified = (pluginId + "#" + beanName).trim().toLowerCase(Locale.ROOT);
            return allowedBeanNames.contains(qualified);
        }
        return false;
    }

    /**
     * 返回拒绝原因说明，供响应内容与日志使用。
     *
     * @param pluginId   插件 ID
     * @param beanName   Bean 名
     * @param methodName 方法名
     * @return 拒绝原因（允许时返回 null）
     */
    public String rejectReason(String pluginId, String beanName, String methodName) {
        if (isAllowed(pluginId, beanName, methodName)) {
            return null;
        }
        String method = methodName == null ? "" : methodName.trim();
        if (DENIED_METHODS.contains(method.toLowerCase(Locale.ROOT))) {
            return "方法不允许远程调用: " + method;
        }
        if (!allowedMethods.isEmpty()) {
            return "方法不在允许列表中: " + method;
        }
        return "Bean 不在允许列表中: pluginId=" + pluginId + ", beanName=" + beanName;
    }
}
