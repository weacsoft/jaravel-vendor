package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.engine.MigrationScanner;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 迁移类标记注解，用于在运行时内存编译后识别哪些类是迁移类。
 * <p>
 * <b>替代 {@code @Component}</b>：迁移文件不再作为 Spring Bean 注册到容器，
 * 而是通过 {@link MigrationScanner} 在运行时编译、加载、实例化、执行后自动释放。
 * <p>
 * 对齐 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 中的 {@code @MigrationAnnotation} 设计。
 * <pre>
 * &#64;MigrationAnnotation
 * public class Migration_2024_01_01_CreateUsersTable implements Migration {
 *     public void up(Schema schema) { ... }
 *     public void down(Schema schema) { ... }
 * }
 * </pre>
 *
 * @see Migration
 * @see MigrationScanner
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrationAnnotation {

    /**
     * 迁移名称，用于排序与记录到 migrations 表。
     * <p>
     * 默认为空字符串，此时 {@link Migration#getName()} 返回类名。
     * 若指定了名称，则以注解中的名称为准。
     *
     * @return 迁移名称，空字符串表示使用类名
     */
    String name() default "";
}
