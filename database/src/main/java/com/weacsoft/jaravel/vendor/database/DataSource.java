package com.weacsoft.jaravel.vendor.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定 Model 使用的数据源 Bean 名称，对齐 Laravel Model 的 $connection 属性。
 * <pre>
 * &#64;DataSource("secondaryDataSource")
 * &#64;Table(name = "products")
 * public class Product extends BaseModel&lt;Product, Long&gt; { ... }
 * </pre>
 * 未标注此注解的 Model 使用默认（Primary）数据源。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {

    /** 数据源 Bean 名称 */
    String value();
}
