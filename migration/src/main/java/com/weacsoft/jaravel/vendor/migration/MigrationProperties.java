package com.weacsoft.jaravel.vendor.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 迁移配置，前缀 {@code jaravel.migration}。
 * <p>
 * 支持三种迁移源模式：
 * <pre>
 * # 目录模式（需要 JDK）
 * jaravel:
 *   migration:
 *     source: DIRECTORY
 *     directory: migrations
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
@ConfigurationProperties(prefix = "jaravel.migration")
public class MigrationProperties {

    /** 是否启用迁移模块 */
    private boolean enabled = true;

    /** 迁移记录表名 */
    private String table = "migrations";

    /** 迁移源类型 */
    private MigrationSource source = MigrationSource.DIRECTORY;

    /** 迁移 .java 文件所在目录（DIRECTORY 模式，运行时内存编译扫描的目录） */
    private String directory = "migrations";

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
    public String getJarPath() { return jarPath; }
    public void setJarPath(String jarPath) { this.jarPath = jarPath; }
    public boolean isPackageInJar() { return packageInJar; }
    public void setPackageInJar(boolean packageInJar) { this.packageInJar = packageInJar; }
    public boolean isAutoRun() { return autoRun; }
    public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }
}
