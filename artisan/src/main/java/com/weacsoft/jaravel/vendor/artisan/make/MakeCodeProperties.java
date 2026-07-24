package com.weacsoft.jaravel.vendor.artisan.make;

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
 *       migration-dir: database/migrations # 迁移文件目录（默认 database/migrations）
 * </pre>
 * <p>
 * 生成路径映射（对齐 Laravel app/Http/Controllers 等）：
 * <ul>
 *   <li>Controller  → {@code base-package}.http.controllers</li>
 *   <li>Middleware  → {@code base-package}.http.middleware</li>
 *   <li>Model       → {@code base-package}.models</li>
 *   <li>Migration   → {@code migration-dir}（e.g. database/migrations）</li>
 *   <li>Command     → {@code base-package}.console.commands</li>
 *   <li>Event       → {@code base-package}.events</li>
 *   <li>Listener    → {@code base-package}.listeners</li>
 * </ul>
 */
public class MakeCodeProperties {

    /** 基包名（生成类的根包） */
    private String basePackage = "com.weacsoft.jaravel";

    /** 输出根目录（Java 源码根目录） */
    private String outputDir = "src/main/java";

    /** 迁移文件目录（相对路径，不带包名前缀） */
    private String migrationDir = "database/migrations";

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getMigrationDir() { return migrationDir; }
    public void setMigrationDir(String migrationDir) { this.migrationDir = migrationDir; }
}
