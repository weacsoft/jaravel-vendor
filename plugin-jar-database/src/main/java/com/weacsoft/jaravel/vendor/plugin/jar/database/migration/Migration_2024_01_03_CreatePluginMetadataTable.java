package com.weacsoft.jaravel.vendor.plugin.jar.database.migration;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.Schema;
import org.springframework.stereotype.Component;

/**
 * 创建 {@code plugin_metadata} 表的迁移。
 * <p>
 * 表结构对齐 {@link com.weacsoft.jaravel.vendor.plugin.jar.database.model.PluginMetadataModel}，
 * 复杂字段（共享类依赖、组件类、路由映射）以 JSON 字符串存储在 TEXT 列中。
 * <p>
 * 命名遵循 {@code Migration_YYYY_MM_DD_Description} 约定，{@link com.weacsoft.jaravel.vendor.migration.engine.Migrator}
 * 按类名字典序排序即可获得正确的执行顺序。
 */
@Component
public class Migration_2024_01_03_CreatePluginMetadataTable implements Migration {

    @Override
    public void up(Schema schema) {
        schema.create("plugin_metadata", table -> {
            table.id();
            table.string("plugin_id", 100);
            table.string("version", 50);
            table.string("jar_path", 500);
            table.string("state", 20);
            table.text("shared_class_dependencies");  // JSON
            table.text("component_classes");           // JSON
            table.text("route_mappings");              // JSON
            table.string("error_message", 500).nullable();
            table.booleanColumn("persisted");
            table.timestamps();
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("plugin_metadata");
    }
}
