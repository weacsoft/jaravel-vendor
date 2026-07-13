package com.weacsoft.jaravel.vendor.migration.engine;


import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
/**
 * 迁移源类型。
 * <p>
 * 支持五种迁移来源，适配不同的部署场景：
 * <ul>
 *   <li>{@link #DIRECTORY} - 从目录读取 {@code .java} 文件，运行时内存编译（需要 JDK）</li>
 *   <li>{@link #DIRECTORY_CLASSES} - 从目录加载预编译的 {@code .class} 文件（只需要 JRE）</li>
 *   <li>{@link #PACKAGED} - 从预编译的 zip 包加载迁移类（只需要 JRE）</li>
 *   <li>{@link #JAR} - 从 {@code .jar} 文件加载预编译的迁移类（只需要 JRE）</li>
 *   <li>{@link #CLASSPATH} - 从当前 classpath 扫描迁移类（内置迁移，打包在框架 jar 中）</li>
 * </ul>
 * <p>
 * 五种模式均通过 {@link MigrationAnnotation} 注解自动识别迁移类，
 * 无需手动指定包名。
 *
 * @see MigrationScanner
 * @see MigrationProperties
 */
public enum MigrationSource {
    /**
     * 目录模式：从目录读取 {@code .java} 文件，运行时内存编译。
     * <p>
     * 需要完整的 JDK 环境（依赖 {@code javax.tools.JavaCompiler}）。
     * 适用于开发阶段，迁移文件以源码形式存在，修改后即时生效。
     */
    DIRECTORY,

    /**
     * 预编译目录模式：从目录加载预编译的 {@code .class} 文件。
     * <p>
     * 只需要 JRE 环境。适用于开发阶段用 JDK 编译后，生产环境用 JRE 运行。
     */
    DIRECTORY_CLASSES,

    /**
     * 打包模式：从预编译的 zip 包加载迁移类。
     * <p>
     * 只需要 JRE 环境。zip 包格式与 jblade 的 .jblade.zip 一致。
     */
    PACKAGED,

    /**
     * JAR 模式：从 {@code .jar} 文件加载预编译的迁移类。
     * <p>
     * 只需要 JRE 环境，无需 JDK。
     * 适用于生产部署，迁移类已预编译打包为独立 jar。
     */
    JAR,

    /**
     * Classpath 模式：从当前 classpath 扫描迁移类。
     * <p>
     * 只需要 JRE 环境。迁移类与框架打包在同一 jar 中（内置迁移），
     * 扫描 classpath 上所有 {@code .class} 文件，通过 {@link MigrationAnnotation} 识别。
     */
    CLASSPATH
}
