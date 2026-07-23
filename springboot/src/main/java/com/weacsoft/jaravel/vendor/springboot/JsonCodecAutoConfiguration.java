package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.json.Jackson2JsonCodec;
import com.weacsoft.jaravel.vendor.json.Jackson3JsonCodec;
import com.weacsoft.jaravel.vendor.json.JsonCodec;
import com.weacsoft.jaravel.vendor.json.JsonCodecHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * JsonCodec 自动装配。
 * <p>
 * 根据 classpath 中可用的 Jackson 版本，注册对应的 {@link JsonCodec} Bean，
 * 并设置到 {@link JsonCodecHolder} 供非 Spring 管理的类使用。
 * <ul>
 *   <li>SB3（Jackson 2 在 classpath）→ {@link Jackson2JsonCodec}</li>
 *   <li>SB4（Jackson 3 在 classpath）→ {@link Jackson3JsonCodec}</li>
 * </ul>
 */
@AutoConfiguration
public class JsonCodecAutoConfiguration {

    /**
     * Jackson 3 实现（SB4 优先）。
     */
    @Bean
    @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(JsonCodec.class)
    public JsonCodec jackson3JsonCodec() {
        JsonCodec codec = new Jackson3JsonCodec();
        JsonCodecHolder.setCodec(codec);
        return codec;
    }

    /**
     * Jackson 2 实现（SB3 回退）。
     */
    @Bean
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(JsonCodec.class)
    public JsonCodec jackson2JsonCodec() {
        JsonCodec codec = new Jackson2JsonCodec();
        JsonCodecHolder.setCodec(codec);
        return codec;
    }
}
