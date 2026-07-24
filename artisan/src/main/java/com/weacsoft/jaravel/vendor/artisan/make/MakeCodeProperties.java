package com.weacsoft.jaravel.vendor.artisan.make;

import java.nio.file.Paths;

/**
 * 代码生成配置属性，前缀 {@code jaravel.artisan.make}。
 * <p>
 * 控制生成文件的基包名和输出目录。对齐 Laravel 的目录约定：
 * <pre>
 * jaravel:
 *   artisan:
 *     make:
 *       base-package: com.example.app      # 基包名（默认 com.weacsoft.jaravel）
 *       output-dir: src/main/java          # 输出根目录（默认 src/main/java）
 * </pre>
 * <p>
 * 生成路径映射（对齐 Laravel app/Http/Controllers 等）：
 * <ul>
 *   <li>Controller  → {@code base-package}.app.http.controllers</li>
 *   <li>Middleware  → {@code base-package}.app.http.middleware</li>
 *   <li>Model       → {@code base-package}.app.models</li>
 *   <li>Migration   → {@code base-package}.database.migration（生成到 Java 源码树中，与 Controller/Model 一致）</li>
 *   <li>Command     → {@code base-package}.app.console.commands</li>
 *   <li>Event       → {@code base-package}.app.events</li>
 *   <li>Listener    → {@code base-package}.app.listeners</li>
 * </ul>
 * <p>
 * Migration 文件生成到 {@code output-dir/基包/database/migration/} 目录下，
 * 而非独立的 {@code database/migrations} 目录。这确保迁移 Java 文件能被编译器
 * 正常编译，并由 CLASSPATH 模式的 {@code MigrationScanner} 自动发现。
 */
public class MakeCodeProperties {

    /** 迁移文件所在包名后缀（singular，对齐用户现有约定） */
    private static final String MIGRATION_PACKAGE_SUFFIX = ".database.migration";

    /** 基包名（生成类的根包） */
    private String basePackage = "com.weacsoft.jaravel";

    /** 输出根目录（Java 源码根目录） */
    private String outputDir = "src/main/java";

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    /**
     * 获取迁移文件完整的包名。
     *
     * @return 迁移包名，如 {@code com.weacsoft.jaravel.database.migration}
     */
    public String getMigrationPackage() {
        return basePackage + MIGRATION_PACKAGE_SUFFIX;
    }

    /**
     * 获取迁移文件所在的源码目录路径（相对路径）。
     * <p>
     * 由 {@code outputDir} 和迁移包名拼接而成，例如：
     * {@code src/main/java/com/weacsoft/jaravel/database/migration}
     * <p>
     * 供 {@code make:model-from-migration} 命令解析迁移文件使用。
     *
     * @return 迁移源码目录路径
     */
    public String getMigrationSourceDir() {
        String packagePath = getMigrationPackage().replace('.', '/');
        return Paths.get(outputDir, packagePath).toString();
    }
}
