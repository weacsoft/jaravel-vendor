package com.weacsoft.jaravel.vendor.wire;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 {@link WireController} 中参与 URL 查询串(query string)的 public 字段。
 * <p>
 * 用于 pushUrl / backUrl 的自动生成:框架在生成「带参 URL」时,只收集被该注解标记的字段,
 * 并按以下规则过滤:
 * <ul>
 *   <li>当前值为 {@code null} → 不加入 URL(空字符串已被「空串转 null」中间件归一化,无需单独判断);</li>
 *   <li>当前值等于 {@link #defaultValue()} 且 defaultValue 非空 → 不加入 URL;</li>
 *   <li>{@link #templates()} 非空时,以 {@code getTemplateName()} 为上下文匹配模板名,
 *       模板不在列表内 → 不加入 URL;空数组表示所有模板都生效。</li>
 * </ul>
 * <p>
 * 典型用途:
 * <ul>
 *   <li>分页参数 {@code page} 标注 {@code @WireQuery(templates={"mdui.admin.admin.list"}, defaultValue="1")}
 *       —— 翻到第 2 页时 URL 自动带 {@code ?page=2},翻回第 1 页时自动还原为无参 URL;</li>
 *   <li>搜索条件 {@code searchKey}/{@code searchValue} 标注 {@code @WireQuery(...)}
 *       —— 翻页/取消对话框后 URL 仍保留搜索条件。</li>
 * </ul>
 * <p>
 * 由此「翻页 → 点修改 → 取消」时,backUrl 会还原为 {@code /admin/admin?page=2}(而非丢失参数
 * 的 {@code /admin/admin}),地址栏与页面内容保持一致。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireQuery {

    /**
     * 该 query 参数在 URL 中使用的名字;为空时使用字段名。
     * 当字段名与 URL 查询参数名不一致时使用(如控制器字段 {@code searchKey}
     * 对应 URL 参数 {@code key})。
     */
    String name() default "";

    /**
     * 该 query 参数生效的模板名列表;空数组表示所有模板都生效。
     * 生成 URL 时以 {@code getTemplateName()} 为上下文匹配:
     * 列表非空且当前模板不在列表内时,该参数不加入 URL。
     * <p>
     * 可通过 {@code WireController.wireQueryTemplates()} 集中覆盖;
     * 注意该覆盖映射的<b>键是字段名(属性名)</b>,与 {@link #name()} 无关。
     */
    String[] templates() default {};

    /**
     * 默认值;当前值等于该值或为 {@code null} 时不把参数加入 URL。
     * <p>
     * 默认空串表示「未设置默认值」→ 仅当前值为 null 时不加入。
     * (运行时有「空字符串转 null」中间件,因此无需单独判断空字符串。)
     */
    String defaultValue() default "";
}
