package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.engine.MigrationScanner;
import com.weacsoft.jaravel.vendor.migration.engine.Migrator;

/**
 * 迁移接口，对齐 Laravel 的 {@code Illuminate\Database\Migrations\Migration}。
 * <p>
 * 每个迁移类实现此接口，提供 {@link #up(Schema)} 与 {@link #down(Schema)} 两个方法，
 * 分别表示「正向执行」与「回滚」。
 * <p>
 * <b>重要变更</b>：迁移类不再是 Spring 组件（不使用 {@code @Component}），
 * 而是使用 {@link MigrationAnnotation} 标记。迁移文件在运行时由
 * {@link MigrationScanner} 扫描、内存编译、反射实例化、执行后自动释放。
 * <p>
 * <b>命名约定（推荐）</b>：类名采用 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 形式，
 * 例如 {@code Migration_2024_01_01_CreateUsersTable}、{@code Migration_2024_01_02_AddEmailToUsersTable}。
 * 由于类名自带 {@code YYYY_MM_DD} 时间前缀，{@link Migrator} 默认按类名（即 {@link #getName()}）
 * 字典序排序即可获得正确的迁移执行顺序，无需额外维护时间戳字段，与
 * <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 框架保持一致。
 * <pre>
 * &#64;MigrationAnnotation
 * public class Migration_2024_01_01_CreateUsersTable implements Migration {
 *     public void up(Schema schema) {
 *         schema.create("users", table -> {
 *             table.id();
 *             table.string("name");
 *             table.string("email").unique();
 *             table.timestamps();
 *         });
 *     }
 *     public void down(Schema schema) {
 *         schema.dropIfExists("users");
 *     }
 * }
 * </pre>
 * <p>
 * <b>多表支持</b>：单个 {@link #up(Schema)} 可连续调用多次 {@link Schema#create(String, java.util.function.Consumer)}
 * 或 {@link Schema#table(String, java.util.function.Consumer)} 处理多张表，
 * {@link #down(Schema)} 应对称地删除/回滚所有在 up() 中创建或修改的表。
 *
 * @see MigrationAnnotation
 * @see MigrationScanner
 */
public interface Migration {

    /**
     * 正向迁移：建表、加字段、加索引等。
     * <p>
     * 一次 up() 可处理多张表，例如先 {@code schema.create("users", ...)} 再 {@code schema.create("user_profiles", ...)}。
     *
     * @param schema schema 构建器
     */
    void up(Schema schema);

    /**
     * 回滚迁移：删表、删字段等，应与 {@link #up(Schema)} 对称。
     * <p>
     * 若 up() 创建/修改了多张表，down() 应按相反顺序删除/回滚全部相关表。
     *
     * @param schema schema 构建器
     */
    void down(Schema schema);

    /**
     * 迁移名称，用于排序与记录到 migrations 表。默认返回类名。
     * <p>
     * 建议类名采用 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 形式
     * （例如 {@code Migration_2024_01_01_CreateUsersTable}），这样 {@link Migrator}
     * 按名称字典序排序即可得到按日期递增的执行顺序，无需额外时间戳字段。
     * 如无特殊需求，无需覆写本方法。
     * <p>
     * 若迁移类上的 {@link MigrationAnnotation#name()} 指定了非空名称，
     * {@link Migrator} 会优先使用注解中的名称。
     *
     * @return 迁移名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
