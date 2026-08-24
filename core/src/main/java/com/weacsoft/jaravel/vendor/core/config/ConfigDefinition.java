package com.weacsoft.jaravel.vendor.core.config;

import java.util.Map;

/**
 * 代码级配置定义接口，对齐 Laravel 的 config/*.php 数组配置。
 * <p>
 * 用户实现此接口，在 {@link #values()} 中返回配置数组，框架自动合并到
 * {@link ConfigRepository}。类名（去掉后缀）作为配置命名空间，
 * 如 {@code App} -> "app"，{@code Database} -> "database"。
 * <p>
 * 使用示例：
 * <pre>
 * // config/App.java
 * &#64;Component
 * public class App implements ConfigDefinition {
 *     &#64;Override
 *     public String namespace() { return "app"; }
 *
 *     &#64;Override
 *     public Map&lt;String, Object&gt; values() {
 *         return Map.of(
 *             "name", "Jaravel",
 *             "env", "production",
 *             "debug", false,
 *             "timezone", "Asia/Shanghai"
 *         );
 *     }
 * }
 * </pre>
 * 读取：{@code Config.get("app.name")} -> "Jaravel"。
 */
public interface ConfigDefinition {

    /**
     * 配置命名空间，如 "app"、"database"、"auth"。
     * <p>
     * 对应 Laravel 的 config/app.php -> config('app.name')。
     *
     * @return 命名空间字符串，不可为 null
     */
    String namespace();

    /**
     * 配置内容，返回嵌套 Map 结构。
     * <p>
     * 如 {@code Map.of("name", "Jaravel", "env", "production", "timezone", "Asia/Shanghai")}，
     * 多级配置可继续嵌套 Map：
     * {@code Map.of("connections", Map.of("sqlite", Map.of("driver", "sqlite")))}。
     *
     * @return 配置内容 Map，返回 null 时将被忽略
     */
    Map<String, Object> values();
}
