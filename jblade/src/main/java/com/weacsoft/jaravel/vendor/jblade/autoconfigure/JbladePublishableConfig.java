package com.weacsoft.jaravel.vendor.jblade.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * jblade 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=jblade} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/JbladeConfig.java}，
 * 内含 {@code jaravel.view.*} 配置项说明。
 * <p>
 * 注意：jblade 模块没有 {@code @ConfigurationProperties} 属性类，
 * 配置通过 {@link ViewAutoConfiguration} 上的 {@code @Value} 直接读取，
 * 因此生成的配置类改为从 {@code Environment} 读取。
 */
public class JbladePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "jblade";
    }

    @Override
    public String className() {
        return "JbladeConfig";
    }

    @Override
    public String description() {
        return "Blade 模板引擎配置（模板目录、后缀、默认视图实现、静态资源前缀）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.core.env.Environment;

                import java.util.LinkedHashMap;

                /**
                 * Blade 模板引擎配置，对齐 Laravel config/view.php。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=jblade} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   view:
                 *     default: ""                    # 默认激活的 View 实现名，留空按注解/兜底判定
                 *     template-dir: templates        # 模板根目录，默认 templates
                 *     suffix: .blade.java            # 模板文件后缀，默认 .blade.java
                 *     asset-url-prefix: /static      # 静态资源 URL 前缀，默认 /static
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>jblade 模块没有属性类，以上配置由 {@code ViewAutoConfiguration} 的
                 *       {@code @Value} 直接读取，本类同样从 {@code Environment} 读取以保持一致。</li>
                 *   <li>自定义 Blade 指令请使用 {@code @RegisterDirective}
                 *       （可执行 {@code artisan vendor:publish --tag=view} 获取示例）。</li>
                 *   <li>本类<b>不会</b>覆盖框架自动装配的 ViewManager，删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class JbladeConfig {

                    /**
                     * Blade 视图生效配置快照。
                     *
                     * @param environment Spring 环境对象
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> jbladeConfigMetadata(Environment environment) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        metadata.put("jaravel.view.default",
                                environment.getProperty("jaravel.view.default", ""));
                        metadata.put("jaravel.view.template-dir",
                                environment.getProperty("jaravel.view.template-dir", "templates"));
                        metadata.put("jaravel.view.suffix",
                                environment.getProperty("jaravel.view.suffix", ".blade.java"));
                        metadata.put("jaravel.view.asset-url-prefix",
                                environment.getProperty("jaravel.view.asset-url-prefix", "/static"));
                        return metadata;
                    }
                }
                """;
    }
}
