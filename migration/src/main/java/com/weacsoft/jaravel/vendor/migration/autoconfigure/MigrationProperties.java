package com.weacsoft.jaravel.vendor.migration.autoconfigure;


import com.weacsoft.jaravel.vendor.migration.engine.MigrationSource;
/**
 * 迁移配置，前缀 {@code jaravel.migration}。
 * <p>
 * <b>独立于 SpringBoot</b>：此类为纯 POJO，不依赖任何 Spring 注解。
 * 在 SpringBoot 环境中通过 {@link MigrationAutoConfiguration} 的
 * {@code @Bean @ConfigurationProperties} 方法绑定配置；
 * 在独立运行时通过 {@link MigrationCLI} 手动设置。
 * <p>
 * 支持五种迁移源模式：
 * <pre>
 * # 目录模式（需要 JDK）
 * jaravel:
 *   migration:
 *     source: DIRECTORY
 *     directory: migrations
 *
 * # 预编译目录模式（只需要 JRE）
 * jaravel:
 *   migration:
 *     source: DIRECTORY_CLASSES
 *     classes-dir: precompiled/migrations
 *
 * # 打包模式（只需要 JRE）
 * jaravel:
 *   migration:
 *     source: PACKAGED
 *     package-path: precompiled/migrations.jmigration.zip
 *
 * # JAR 模式（只需要 JRE）
 * jaravel:
 *   migration:
 *     source: JAR
 *     jar-path: /path/to/migrations.jar
 *
 * # Classpath 模式（内置迁移）
 * jaravel:
 *   migration:
 *     source: CLASSPATH
 * </pre>
 * <p>
 * 其他配置项：
 * <pre>
 * jaravel:
 *   migration:
 *     enabled: true
 *     table: migrations
 *     auto-run: false        # 启动时是否自动执行 migrate
 *     package-in-jar: false  # 构建时是否将迁移目录打包进 jar
 * </pre>
 *
 * @see MigrationSource
 */
public class MigrationProperties {

    /** 是否启用迁移模块 */
    private boolean enabled = true;

    /** 迁移记录表名 */
    private String table = "migrations";

    /** 迁移源类型 */
    private MigrationSource source = MigrationSource.DIRECTORY;

    /** 迁移 .java 文件所在目录（DIRECTORY 模式，运行时内存编译扫描的目录） */
    private String directory = "migrations";

    /** 预编译 class 文件目录（DIRECTORY_CLASSES 模式） */
    private String classesDir = "";

    /** 预编译打包文件路径（PACKAGED 模式，如 migrations.jmigration.zip） */
    private String packagePath = "";

    /** JAR 文件路径（JAR 模式，预编译的迁移类 jar） */
    private String jarPath = "";

    /** 是否将迁移目录打包进 jar（构建时资源配置，用于 CLASSPATH 模式） */
    private boolean packageInJar = false;

    /** 启动时是否自动执行 migrate */
    private boolean autoRun = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public MigrationSource getSource() { return source; }
    public void setSource(MigrationSource source) { this.source = source; }
    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public String getClassesDir() { return classesDir; }
    public void setClassesDir(String classesDir) { this.classesDir = classesDir; }
    public String getPackagePath() { return packagePath; }
    public void setPackagePath(String packagePath) { this.packagePath = packagePath; }
    public String getJarPath() { return jarPath; }
    public void setJarPath(String jarPath) { this.jarPath = jarPath; }
    public boolean isPackageInJar() { return packageInJar; }
    public void setPackageInJar(boolean packageInJar) { this.packageInJar = packageInJar; }
    public boolean isAutoRun() { return autoRun; }
    public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }
}
