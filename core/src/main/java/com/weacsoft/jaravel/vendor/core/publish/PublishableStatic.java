package com.weacsoft.jaravel.vendor.core.publish;

import java.util.Map;

/**
 * 可发布的静态前端资源声明。
 * <p>
 * 与 {@link PublishableConfig} 平行且完全独立：
 * <ul>
 *   <li>{@link PublishableConfig} —— 发布 <b>Java 配置类源码</b>，由 {@code vendor:publish} 处理；</li>
 *   <li>{@code PublishableStatic} —— 发布 <b>静态前端资源</b>（js / css / html），
 *       由 {@code vendor:publish:static} 处理。</li>
 * </ul>
 * 两者互不触发：执行 {@code vendor:publish} 不会写出任何静态资源，
 * 执行 {@code vendor:publish:static} 也不会写出任何 Java 源码。
 *
 * <h3>实现示例</h3>
 * <pre>
 * public class CaptchaStaticPublishable implements PublishableStatic {
 *     public String tag() { return "captcha"; }
 *     public Map&lt;String, String&gt; resources() {
 *         return Map.of("static/jaravel-captcha.js", "static/jaravel-captcha.js");
 *     }
 * }
 * </pre>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>key 为 <b>classpath 资源路径</b>（不以 {@code /} 开头），由模块 jar 自带；</li>
 *   <li>value 为 <b>相对业务工程 resources 根目录</b> 的目标路径，
 *       通常以 {@code static/} 开头，最终写入 {@code src/main/resources/static/...}。</li>
 * </ul>
 */
public interface PublishableStatic {

    /**
     * 发布标签，用于 {@code vendor:publish:static --tag=<标签>} 精确发布。
     * <p>
     * 建议使用模块名，如 {@code captcha} / {@code wire}。
     *
     * @return 标签名，不可为 null
     */
    String tag();

    /**
     * 待发布的资源清单：{@code classpath 源路径 -> resources 根下的目标相对路径}。
     * <p>
     * 使用有序 Map（如 {@link java.util.LinkedHashMap}）可保证输出顺序稳定。
     *
     * @return 资源映射，不可为 null（无资源时返回空 Map）
     */
    Map<String, String> resources();

    /**
     * 资源用途描述，用于 {@code --list} 输出。
     *
     * @return 描述文本，默认空串
     */
    default String description() {
        return "";
    }

    /**
     * 加载资源的类加载器。
     * <p>
     * 默认使用实现类自身的类加载器，确保能读到模块 jar 内的资源。
     *
     * @return 类加载器
     */
    default ClassLoader resourceClassLoader() {
        return getClass().getClassLoader();
    }
}
