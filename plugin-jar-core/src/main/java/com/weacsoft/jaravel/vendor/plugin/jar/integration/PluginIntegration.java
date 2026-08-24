package com.weacsoft.jaravel.vendor.plugin.jar.integration;

import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Jaravel 集成接口。
 * <p>
 * 允许插件在启用/禁用时访问 jaravel 提供的服务（Auth、Cache、Event 等），
 * 并声明插件可见的共享包前缀与额外的共享 JAR。
 * <p>
 * 默认实现 {@link DefaultPluginIntegration} 为空操作，用于 jaravel 不可用的场景。
 * jaravel 启动器（starter 模块）可提供具体实现，向插件注入 jaravel 上下文。
 */
public interface PluginIntegration {

    /**
     * 插件启用时回调，允许向插件上下文注入 jaravel 服务。
     *
     * @param pluginId 插件 ID
     * @param context  插件的 Spring 上下文
     */
    void onPluginEnabled(String pluginId, ConfigurableApplicationContext context);

    /**
     * 插件禁用时回调。
     *
     * @param pluginId 插件 ID
     */
    void onPluginDisabled(String pluginId);

    /**
     * 返回插件可见的共享包前缀。
     * <p>
     * 匹配这些前缀的类将由共享 ClassLoader 加载，对所有插件可见。
     *
     * @return 共享包前缀集合
     */
    Set<String> getSharedPackagePrefixes();

    /**
     * 返回额外的共享 JAR 路径（如 jaravel vendor JAR）。
     * <p>
     * 这些 JAR 会被加入共享 ClassLoader，使插件可访问 jaravel 服务。
     *
     * @return 共享 JAR 路径列表
     */
    List<Path> getAdditionalSharedJars();

    /**
     * 创建插件请求对象。
     * <p>
     * 当 jaravel 可用时，创建并返回 jaravel 的 {@code Request} 对象；
     * 当 jaravel 不可用时返回 {@code null}，表示使用 Servlet 原生请求。
     *
     * @param servletRequest Servlet 请求对象（运行时为 HttpServletRequest，兼容 javax/jakarta）
     * @return jaravel Request 对象，或 null
     */
    Object createPluginRequest(Object servletRequest);

    /**
     * 写入插件响应。
     * <p>
     * 当 jaravel 可用时，处理 jaravel {@code Response} 返回值（提取 status/headers/content 写入 ServletResponse）；
     * 当 jaravel 不可用时返回 {@code false}，由调用方使用默认写入逻辑。
     *
     * @param result          插件方法返回值
     * @param servletResponse Servlet 响应对象（运行时为 HttpServletResponse，兼容 javax/jakarta）
     * @param produces        Content-Type
     * @return true 表示已处理，false 表示未处理（使用默认逻辑）
     */
    boolean writePluginResponse(Object result, Object servletResponse, String produces);

    /**
     * 读取配置值。
     * <p>
     * 当 jaravel 可用时，委托给 {@code Config.get()}；
     * 当 jaravel 不可用时，从 Spring Environment 读取。
     *
     * @param key          配置键（如 "jaravel.plugin-jar.auto-register"）
     * @param defaultValue 默认值
     * @return 配置值
     */
    <T> T getConfigValue(String key, T defaultValue);
}
