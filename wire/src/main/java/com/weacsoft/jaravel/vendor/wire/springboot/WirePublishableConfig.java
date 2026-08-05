package com.weacsoft.jaravel.vendor.wire.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * wire 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=wire} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/WireConfig.java}，内含 {@code jaravel.wire.*}
 * 配置项说明与一个只读的配置快照 Bean，用户可直接修改。
 */
public class WirePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "wire";
    }

    @Override
    public String className() {
        return "WireConfig";
    }

    @Override
    public String description() {
        return "Wire 前端交互组件配置（JS 自动注入、排除区块）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.wire.springboot.WireProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * Wire 配置，对齐 Laravel Livewire 的组件交互能力。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=wire} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   wire:
                 *     enabled: true              # 是否启用 wire 模块，默认 true
                 *     auto-inject-js: true       # 是否在 HTML 响应中自动注入 wire.js，默认 true
                 *     js-path: /static/wire.js   # wire.js 的访问路径，默认 /static/wire.js
                 *     excluded-sections: []      # 不做自动注入的区块名列表，默认空
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的任何 Bean。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class WireConfig {

                    /**
                     * Wire 模块生效配置快照，便于排查「配置没生效」类问题。
                     *
                     * @param provider WireProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> wireConfigMetadata(ObjectProvider<WireProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        WireProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.wire", "未装配（wire 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.wire.enabled", properties.isEnabled());
                        metadata.put("jaravel.wire.auto-inject-js", properties.isAutoInjectJs());
                        metadata.put("jaravel.wire.js-path", properties.getJsPath());
                        metadata.put("jaravel.wire.excluded-sections", properties.getExcludedSections());
                        return metadata;
                    }
                }
                """;
    }
}
