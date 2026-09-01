package com.weacsoft.jaravel.vendor.core.publish;

import java.util.List;
import java.util.Map;

/**
 * 可发布的模块迁移文件声明（Java 版 Laravel {@code vendor:publish --tag=migrations}）。
 * <p>
 * 与 {@link PublishableConfig}（配置类源码）和 {@link PublishableStatic}（静态前端资源）平行，
 * 但三者都继承 {@link Publishable}，由同一条 {@code vendor:publish} 命令统一扫描发布：
 * <ul>
 *   <li>{@link PublishableConfig} —— 发布 <b>Java 配置类源码</b>；</li>
 *   <li>{@link PublishableStatic} —— 发布 <b>静态前端资源</b>（js / css / html）；</li>
 *   <li>{@code PublishableMigration} —— 发布 <b>模块迁移 Java 源文件</b>（建表等 DDL 声明）。</li>
 * </ul>
 * <p>
 * <h3>为什么迁移也要走 vendor:publish</h3>
 * 使用 database / storage(database) / queue(database) 等驱动的模块都需要先建表。
 * 对齐 Laravel 的做法：模块把自己需要的表结构<b>以迁移文件形式打进 jar</b>，
 * 开发者执行 {@code artisan vendor:publish --tag=migrations} 一键发布
 * <b>所有模块</b>的迁移文件到业务工程迁移目录，再执行 {@code artisan migrate} 完成建表——
 * 而不是每个模块各自一套 {@code xxx:table} + 手动 SQL 的割裂体验。
 * <p>
 * <h3>文件清单约定</h3>
 * <ul>
 *   <li>key 为 <b>classpath 资源路径</b>（不以 {@code /} 开头），迁移 Java 源文件，由模块 jar 自带；</li>
 *   <li>value 为<b>发布后的文件名</b>（不含目录），遵循
 *       {@code Migration_YYYY_MM_DD_...} 命名约定，
 *       目标目录由命令层统一决定（业务工程的迁移源代码目录），模块不关心落盘位置。</li>
 * </ul>
 *
 * <h3>实现示例</h3>
 * <pre>
 * public class CacheDatabaseMigrationPublishable implements PublishableMigration {
 *     public String tag() { return "cache-database"; }
 *     public List&lt;Map.Entry&lt;String, String&gt;&gt; migrationFiles() {
 *         return List.of(Map.entry(
 *             "jaravel/migrations/Migration_20240101_CreateJaravelCacheTable.java",
 *             "Migration_20240101_CreateJaravelCacheTable.java"));
 *     }
 * }
 * </pre>
 *
 * @see Publishable
 * @see PublishableConfig
 * @see PublishableStatic
 */
public interface PublishableMigration extends Publishable {

    /**
     * 待发布的迁移文件清单：{@code classpath 源路径 -> 发布文件名}。
     * <p>
     * 使用有序结构（如 {@code List.of(...)} 或有序 Map）保证输出顺序稳定。
     *
     * @return 迁移文件映射，不可为 null（无迁移时返回空列表）
     */
    List<Map.Entry<String, String>> migrationFiles();

    /**
     * 迁移文件用途描述，用于 {@code --list} 输出。
     *
     * @return 描述文本，默认空串
     */
    @Override
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
    default ClassLoader sourceClassLoader() {
        return getClass().getClassLoader();
    }

    /**
     * 可发布项类型固定为 {@link PublishType#MIGRATION}（发布迁移 Java 源文件）。
     */
    @Override
    default PublishType type() {
        return PublishType.MIGRATION;
    }
}
