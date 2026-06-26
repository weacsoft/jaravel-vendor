package com.weacsoft.jaravel.vendor.modelcache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Model 类上，手动开启查询缓存。
 * <p>
 * 参考 Laravel {@code laravel-model-caching} 方案：仅在标注本注解的类上启用缓存，
 * 未标注的类直接回源，避免无差别缓存带来的数据一致性问题与内存浪费。
 * <pre>
 * &#64;CachableModel(ttl = 600, prefix = "user")
 * public class User extends BaseModel&lt;User, Long&gt; { ... }
 * </pre>
 * 缓存键结构：{@code {keyPrefix}{prefix}:v{version}:{suffix}}，其中 {@link #prefix()} 为空时
 * 使用类名（{@link Class#getSimpleName()}）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CachableModel {

    /**
     * 缓存 TTL（秒）。
     *
     * @return TTL，-1 表示使用全局默认值 {@code jaravel.model-cache.default-ttl}，
     *         0 表示永不过期（仅靠版本号失效），正数为有效秒数
     */
    long ttl() default -1;

    /**
     * 自定义缓存键前缀（拼在全局 keyPrefix 之后，类名位置）。
     *
     * @return 前缀，为空时使用类名（{@link Class#getSimpleName()}）
     */
    String prefix() default "";
}
