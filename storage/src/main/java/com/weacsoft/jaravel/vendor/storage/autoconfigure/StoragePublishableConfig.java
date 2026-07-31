package com.weacsoft.jaravel.vendor.storage.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * storage 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=storage} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/StorageConfig.java}，
 * 内含 {@code @RegisterDisk} 示例方法，对齐 Laravel {@code config/filesystems.php}。
 */
public class StoragePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "storage";
    }

    @Override
    public String className() {
        return "StorageConfig";
    }

    @Override
    public String description() {
        return "文件磁盘注册（local / public / database）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.storage.RegisterDisk;\n"
                + "import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;\n"
                + "import com.weacsoft.jaravel.vendor.storage.contract.Visibility;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "/**\n"
                + " * 文件存储配置，对齐 Laravel config/filesystems.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=storage} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>说明</h3>\n"
                + " * <ul>\n"
                + " *   <li>{@code @RegisterDisk} 注册的磁盘<b>不会</b>成为 Spring Bean，\n"
                + " *       磁盘名称不会与容器内同名 bean 冲突。</li>\n"
                + " *   <li>注解式注册优先于配置式（{@code jaravel.storage.disks}），同名时覆盖。</li>\n"
                + " *   <li>删除本文件即可回退到框架默认（local 磁盘，根目录 storage/app）。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@Configuration\n"
                + "public class StorageConfig {\n"
                + "\n"
                + "    /**\n"
                + "     * 本地私有磁盘，根目录 {@code storage/app}。\n"
                + "     */\n"
                + "    @RegisterDisk(value = \"local\", defaultDisk = true)\n"
                + "    public DiskDefinition localDisk() {\n"
                + "        return DiskDefinition.local(\"storage/app\");\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 公开磁盘，可通过 {@code /storage} 前缀访问。\n"
                + "     * <p>\n"
                + "     * 使用 {@code Storage.disk(\"public\").url(path)} 生成访问地址。\n"
                + "     */\n"
                + "    @RegisterDisk(\"public\")\n"
                + "    public DiskDefinition publicDisk() {\n"
                + "        return DiskDefinition.local(\"storage/app/public\")\n"
                + "                .url(\"/storage\")\n"
                + "                .visibility(Visibility.PUBLIC);\n"
                + "    }\n"
                + "\n"
                + "    // 数据库磁盘：需要 DataSource，磁盘实例创建时会自动建表 storage_file 与 storage_file_chunk\n"
                + "    // @RegisterDisk(\"database\")\n"
                + "    // public DiskDefinition databaseDisk() {\n"
                + "    //     return DiskDefinition.of(\"database\").with(\"table\", \"storage_file\");\n"
                + "    // }\n"
                + "}\n";
    }
}
