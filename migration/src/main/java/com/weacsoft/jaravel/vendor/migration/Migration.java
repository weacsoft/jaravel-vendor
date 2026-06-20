package com.weacsoft.jaravel.vendor.migration;

/**
 * 迁移接口，对齐 Laravel 的 {@code Illuminate\Database\Migrations\Migration}。
 * <p>
 * 每个迁移类实现此接口，提供 {@link #up(Schema)} 与 {@link #down(Schema)} 两个方法，
 * 分别表示「正向执行」与「回滚」。迁移名称默认取类名，也可覆写 {@link #getName()} 自定义。
 * <p>
 * <b>命名约定（推荐）</b>：类名采用 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 形式，
 * 例如 {@code Migration_2024_01_01_CreateUsersTable}、{@code Migration_2024_01_02_AddEmailToUsersTable}。
 * 由于类名自带 {@code YYYY_MM_DD} 时间前缀，{@link Migrator} 默认按类名（即 {@link #getName()}）
 * 字典序排序即可获得正确的迁移执行顺序，无需额外维护时间戳字段，与
 * <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 框架保持一致。
 * <pre>
 * public class Migration_2024_01_01_CreateUsersTable implements Migration {
 *     // getName() 默认返回类名 "Migration_2024_01_01_CreateUsersTable"，无需覆写
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
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 指定此迁移使用的数据源 Bean 名称，对齐 Laravel 迁移的 {@code $connection} 属性。
     * <p>
     * 返回 null 或空字符串时使用默认（Primary）数据源。
     * 返回非空字符串时，Migrator 会从 Spring 容器中查找对应名称的 {@link javax.sql.DataSource} Bean，
     * 并为该迁移创建独立的 {@link Schema} 实例。
     *
     * @return 数据源 Bean 名称，或 null
     */
    default String getDataSourceName() {
        return null;
    }
}
