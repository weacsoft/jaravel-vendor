package com.weacsoft.jaravel.vendor.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定 Model 使用的数据库连接<b>别名</b>，对齐 Laravel Model 的 {@code $connection} 属性。
 * <pre>
 * &#64;DataSource("mysql")
 * &#64;Table(name = "products")
 * public class Product extends BaseModel&lt;Product, Long&gt; { ... }
 * </pre>
 * 未标注此注解的 Model 使用默认连接（{@code sqlite}）。
 *
 * <h3>别名解析顺序</h3>
 * 这里填写的是<b>连接别名</b>而非 Spring bean name。
 * {@link BaseModel#getGaarasonDataSource()} 解析时：
 * <ol>
 *   <li>先在 {@link ConnectionManager} 注册表中查找，即
 *       {@code config/DatabaseConfig.java} 里用 {@link RegisterConnection @RegisterConnection}
 *       声明的连接；</li>
 *   <li>找不到再回退到 Spring 容器中同名的 {@code GaarasonDataSource} /
 *       {@code javax.sql.DataSource} bean。</li>
 * </ol>
 * 因此别名可以自由取名（如 {@code mysql}、{@code sqlite}），不会与同名 Spring bean 冲突。
 *
 * @see RegisterConnection
 * @see ConnectionManager#connection(String)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {

    /** 数据库连接别名（对应 {@code @RegisterConnection} 的 value） */
    String value();
}
