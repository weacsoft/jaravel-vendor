package com.weacsoft.jaravel.vendor.core.publish;

/**
 * 可发布配置契约，对齐 Laravel {@code ServiceProvider::publishes()}。
 * <p>
 * 各模块实现本接口并注册为 Spring Bean，即可被 {@code artisan vendor:publish}
 * 命令发现并把配置类源码发布到业务工程的 {@code config/} 包下。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>本接口位于 <b>core</b> 模块，不依赖 artisan。因此各模块声明可发布配置时
 *       无需引入 artisan 依赖，实现「有 artisan 就能发布，没有也不影响」。</li>
 *   <li>发布产物是 <b>Java 配置类源码</b>，内含 {@code @RegisterCacheStore} /
 *       {@code @RegisterDisk} 等注解方法，用户发布后可直接修改代码。</li>
 *   <li>{@link #tag()} 对齐 Laravel 的 {@code --tag} 选项，用于分组发布。</li>
 * </ul>
 *
 * <h3>实现示例</h3>
 * <pre>{@code
 * public class CachePublishableConfig implements PublishableConfig {
 *     public String tag()       { return "cache"; }
 *     public String className() { return "CacheConfig"; }
 *     public String source(String basePackage) {
 *         return "package " + basePackage + ".config;\n ...";
 *     }
 * }
 * }</pre>
 */
public interface PublishableConfig {

    /**
     * 发布标签，对齐 Laravel {@code --tag=cache}。
     * <p>
     * 通常与模块名一致，如 {@code cache} / {@code storage} / {@code auth}。
     *
     * @return 标签名，不可为空
     */
    String tag();

    /**
     * 发布后生成的配置类简单类名（不含包名、不含 {@code .java} 后缀）。
     *
     * @return 类名，如 {@code CacheConfig}
     */
    String className();

    /**
     * 生成配置类的完整 Java 源码。
     *
     * @param basePackage 业务工程基包名，如 {@code com.example.demo}；
     *                    实现方应把配置类放在 {@code basePackage + ".config"} 包下
     * @return 完整的 Java 源码文本
     */
    String source(String basePackage);

    /**
     * 配置类用途描述，供 {@code vendor:publish --list} 展示。
     *
     * @return 描述文本
     */
    default String description() {
        return "";
    }
}
