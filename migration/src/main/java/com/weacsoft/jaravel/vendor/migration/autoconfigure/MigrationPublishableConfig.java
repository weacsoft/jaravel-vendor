package com.weacsoft.jaravel.vendor.migration.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * migration 模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=migration} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/MigrationConfig.java}，
 * 内含 {@code jaravel.migration.*} 配置项说明。
 */
public class MigrationPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "migration";
    }

    @Override
    public String className() {
        return "MigrationConfig";
    }

    @Override
    public String description() {
        return "数据库迁移配置（迁移表、迁移源、自动执行）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + """

                import com.weacsoft.jaravel.vendor.migration.autoconfigure.MigrationProperties;
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                import java.util.LinkedHashMap;

                /**
                 * 数据库迁移配置，对齐 Laravel 的 migrations 机制。
                 * <p>
                 * 由 {@code artisan vendor:publish --tag=migration} 发布生成，可自由修改。
                 *
                 * <h3>配置项（application.yml）</h3>
                 * <pre>
                 * jaravel:
                 *   migration:
                 *     enabled: true            # 是否启用迁移模块，默认 true
                 *     table: migrations        # 迁移版本记录表名，默认 migrations
                 *     source: DIRECTORY        # 迁移源：DIRECTORY / CLASSES / PACKAGE / JAR
                 *     directory: migrations    # source=DIRECTORY 时的迁移脚本目录
                 *     classes-dir: ""          # source=CLASSES 时的已编译迁移类目录
                 *     package-path: ""         # source=PACKAGE 时的迁移类包名
                 *     jar-path: ""             # source=JAR 时的 jar 路径
                 *     package-in-jar: false    # 迁移类是否打包在 jar 内，默认 false
                 *     auto-run: false          # 应用启动时是否自动执行迁移，默认 false
                 * </pre>
                 *
                 * <h3>说明</h3>
                 * <ul>
                 *   <li>本类只读取配置生成一份快照，<b>不会</b>覆盖框架自动装配的 MigrationExecutor。</li>
                 *   <li>手动执行迁移：{@code artisan migrate} / {@code artisan migrate:rollback}。</li>
                 *   <li>删除本文件不影响启动。</li>
                 * </ul>
                 */
                @Configuration
                public class MigrationConfig {

                    /**
                     * 迁移模块生效配置快照。
                     *
                     * @param provider MigrationProperties 提供者（模块未启用时为空）
                     * @return 解析后的配置键值对
                     */
                    @Bean
                    public LinkedHashMap<String, Object> migrationConfigMetadata(
                            ObjectProvider<MigrationProperties> provider) {
                        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                        MigrationProperties properties = provider.getIfAvailable();
                        if (properties == null) {
                            metadata.put("jaravel.migration", "未装配（migration 模块未启用）");
                            return metadata;
                        }
                        metadata.put("jaravel.migration.enabled", properties.isEnabled());
                        metadata.put("jaravel.migration.table", properties.getTable());
                        metadata.put("jaravel.migration.source", String.valueOf(properties.getSource()));
                        metadata.put("jaravel.migration.directory", properties.getDirectory());
                        metadata.put("jaravel.migration.classes-dir", properties.getClassesDir());
                        metadata.put("jaravel.migration.package-path", properties.getPackagePath());
                        metadata.put("jaravel.migration.jar-path", properties.getJarPath());
                        metadata.put("jaravel.migration.package-in-jar", properties.isPackageInJar());
                        metadata.put("jaravel.migration.auto-run", properties.isAutoRun());
                        return metadata;
                    }
                }
                """;
    }
}
