package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * json 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=json} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/JsonConfig.java}，
 * 内含自定义 {@code JsonCodec} 的替换说明。
 *
 * <h3>为什么放在 springboot 模块</h3>
 * json 模块<b>零 Spring 依赖</b>，且 {@code core} 反向依赖 {@code json}，
 * 若在 json 模块内实现 {@link PublishableConfig} 会形成 Maven 循环依赖。
 * 因此与 {@link JsonCodecAutoConfiguration} 一起放在 springboot 模块（同时依赖 json 与 core）。
 */
public class JsonPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "json";
    }

    @Override
    public String className() {
        return "JsonConfig";
    }

    @Override
    public String description() {
        return "JSON 编解码配置（Jackson 2 / Jackson 3 自动检测，可自定义 JsonCodec）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.json.JsonCodec;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * JSON 编解码配置。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=json} 发布生成，可自由修改。
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>json 模块<b>没有</b> {@code jaravel.json.*} 配置项，
                 *       实现按 classpath 自动检测：
                 *       存在 {@code tools.jackson.databind.ObjectMapper}（Jackson 3 / SB4）
                 *       则使用 {@code Jackson3JsonCodec}，否则回退
                 *       {@code com.fasterxml.jackson.databind.ObjectMapper}（Jackson 2 / SB3）
                 *       对应的 {@code Jackson2JsonCodec}。</li>
                 *   <li>如需完全自定义序列化行为，在本类中新增一个
                 *       {@code @Bean JsonCodec myJsonCodec()}，
                 *       框架的 {@code @ConditionalOnMissingBean(JsonCodec.class)} 会自动让位；
                 *       同时记得调用 {@code JsonCodecHolder.setCodec(codec)}
                 *       以便非 Spring 管理的类也能取到。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class JsonConfig {

                    /**
                     * 当前生效的 JsonCodec 实现快照，便于排查「JSON 行为与预期不符」类问题。
                     *
                     * @param provider JsonCodec 提供者
                     * @return 解析后的实现信息
                     */
                    @Bean
                    public LinkedHashMap<String, Object> jsonConfigMetadata(ObjectProvider<JsonCodec> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        JsonCodec codec = provider.getIfAvailable();
                        metadata.put("jaravel.json.codec",
                                codec == null ? "未装配（classpath 无可用 Jackson）" : codec.getClass().getName());
                        metadata.put("jaravel.json.jackson3-present",
                                isPresent("tools.jackson.databind.ObjectMapper"));
                        metadata.put("jaravel.json.jackson2-present",
                                isPresent("com.fasterxml.jackson.databind.ObjectMapper"));
                        return metadata;
                    }

                    private static boolean isPresent(String className) {
                        try {
                            Class.forName(className, false, JsonConfig.class.getClassLoader());
                            return true;
                        } catch (ClassNotFoundException e) {
                            return false;
                        }
                    }
                }
                """;
    }
}
