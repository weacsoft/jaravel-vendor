package com.weacsoft.jaravel.vendor.migration.engine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 迁移文件预编译工具。
 * <p>
 * 在开发阶段（有 JDK）将所有迁移 {@code .java} 文件预编译为字节码，支持两种输出模式：
 * <ul>
 *   <li>模式一：打包为单个 zip 文件（默认后缀 {@code .jmigration.zip}，可自定义），运行时整体加载</li>
 *   <li>模式二：输出为散乱的 {@code .class} 文件到目录，运行时从目录加载</li>
 * </ul>
 * 预编译后，运行时无需 JDK，只需 JRE 即可运行。
 * <p>
 * zip 包格式与 jblade 的 {@code .jblade.zip} 一致：
 * <ul>
 *   <li>{@code manifest.txt}：类名列表（每行一个类全限定名，以 {@code #} 开头的行视为注释）</li>
 *   <li>其余 {@code .class} 文件按包结构存储（如 {@code com/weacsoft/Foo.class}）</li>
 * </ul>
 * <p>
 * 典型用法：
 * <pre>
 * MigrationPrecompiler precompiler = new MigrationPrecompiler("migrations");
 * precompiler.compileAllToZip("precompiled", "migrations.jmigration.zip");
 * </pre>
 * <p>
 * <b>注意</b>：此类需要 JDK 运行（因为它要编译 {@code .java} 文件），
 * 但加载预编译产物时只需要 JRE。
 *
 * @see MigrationScanner
 * @see MigrationSource#PACKAGED
 * @see MigrationSource#DIRECTORY_CLASSES
 */
public class MigrationPrecompiler {

    /** manifest 文件名 */
    static final String MANIFEST_NAME = "manifest.txt";

    /** class 文件后缀 */
    static final String CLASS_SUFFIX = ".class";

    /**
     * 预编译模式
     */
    public enum CompileMode {
        /** 打包为单个 zip 文件 */
        PACKAGED,
        /** 散乱 class 文件到目录 */
        CLASSES
    }

    private final String sourceDir;

    /**
     * 创建预编译工具。
     *
     * @param sourceDir 迁移 {@code .java} 文件所在目录
     */
    public MigrationPrecompiler(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    /**
     * 预编译所有迁移文件。
     *
     * @param outputDir 输出目录
     * @param mode      编译模式 (PACKAGED 或 CLASSES)
     * @param fileName  打包文件名（仅 PACKAGED 模式，null 时默认 "migrations.jmigration.zip"）
     * @return 编译成功的文件数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAll(String outputDir, CompileMode mode, String fileName) throws IOException {
        // 1. 使用 MigrationScanner 编译 .java 文件
        Map<String, byte[]> classBytecodes = compileMigrationFiles();

        // 2. 创建输出目录
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // 3. 根据模式输出
        if (mode == CompileMode.PACKAGED) {
            String name = (fileName != null && !fileName.isEmpty()) ? fileName : "migrations.jmigration.zip";
            String packagePath = new File(outputDirectory, name).getAbsolutePath();
            saveToPackage(packagePath, classBytecodes);
            System.out.println("[migration] 打包文件已生成: " + packagePath);
        } else {
            String classesDirPath = outputDirectory.getAbsolutePath();
            saveToDirectory(classesDirPath, classBytecodes);
            System.out.println("[migration] class 文件已输出到: " + classesDirPath);
        }

        System.out.println("[migration] 预编译完成: 共 " + classBytecodes.size() + " 个类");
        return classBytecodes.size();
    }

    /**
     * 便利方法：预编译所有迁移文件并打包为单个 zip 文件。
     *
     * @param outputDir 输出目录
     * @param fileName  打包文件名（如 "migrations.jmigration.zip"，null 时默认 "migrations.jmigration.zip"）
     * @return 编译成功的文件数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAllToZip(String outputDir, String fileName) throws IOException {
        return compileAll(outputDir, CompileMode.PACKAGED, fileName);
    }

    /**
     * 便利方法：预编译所有迁移文件并输出为散乱 class 文件。
     *
     * @param outputDir 输出目录
     * @return 编译成功的文件数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAllToClasses(String outputDir) throws IOException {
        return compileAll(outputDir, CompileMode.CLASSES, null);
    }

    // ===== 内部实现 =====

    /**
     * 使用 MigrationScanner 编译所有迁移 .java 文件。
     *
     * @return 类全限定名 -> 字节码的映射
     * @throws IOException 如果编译或读取失败
     */
    private Map<String, byte[]> compileMigrationFiles() throws IOException {
        MigrationScanner scanner = new MigrationScanner();
        try {
            System.out.println("[migration] 扫描迁移目录: " + sourceDir);
            scanner.compileFromDirectory(sourceDir);
        } catch (Exception e) {
            throw new IOException("编译迁移文件失败: " + e.getMessage(), e);
        }

        Map<String, byte[]> bytecodes = scanner.getCompiledClasses();
        if (bytecodes.isEmpty()) {
            System.out.println("[migration] 未找到迁移文件或编译无产物");
        }

        // 释放 scanner 资源
        scanner.finish();
        return bytecodes;
    }

    /**
     * 保存字节码到打包文件（zip）。
     * <p>
     * zip 中包含 manifest.txt（类名列表）和所有 .class 文件。
     *
     * @param packagePath     打包文件输出路径
     * @param classBytecodes  类全限定名 -> 字节码
     * @throws IOException 如果写入失败
     */
    private void saveToPackage(String packagePath, Map<String, byte[]> classBytecodes) throws IOException {
        File outputFile = new File(packagePath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            // 1. 写入 manifest.txt
            ZipEntry manifestEntry = new ZipEntry(MANIFEST_NAME);
            zos.putNextEntry(manifestEntry);
            zos.write(formatManifest(classBytecodes.keySet()).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. 写入所有 .class 文件
            for (Map.Entry<String, byte[]> entry : classBytecodes.entrySet()) {
                String className = entry.getKey();
                String path = classNameToPath(className);
                ZipEntry classEntry = new ZipEntry(path);
                zos.putNextEntry(classEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }

    /**
     * 保存字节码到目录。
     * <p>
     * 目录中包含 manifest.txt（类名列表）和按包结构存储的 .class 文件。
     *
     * @param dirPath         目录路径
     * @param classBytecodes  类全限定名 -> 字节码
     * @throws IOException 如果写入失败
     */
    private void saveToDirectory(String dirPath, Map<String, byte[]> classBytecodes) throws IOException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 1. 写入 manifest.txt
        File manifestFile = new File(dir, MANIFEST_NAME);
        try (OutputStream os = new FileOutputStream(manifestFile)) {
            os.write(formatManifest(classBytecodes.keySet()).getBytes(StandardCharsets.UTF_8));
        }

        // 2. 按包结构写入 .class 文件
        for (Map.Entry<String, byte[]> entry : classBytecodes.entrySet()) {
            String className = entry.getKey();
            String path = classNameToPath(className);
            File classFile = new File(dir, path);
            File parent = classFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(classFile.toPath(), entry.getValue());
        }
    }

    /**
     * 将类名列表格式化为 manifest 文本。
     * <p>
     * 格式：每行一个类全限定名，以 {@code #} 开头的行视为注释。
     *
     * @param classNames 类全限定名集合
     * @return manifest 文本
     */
    static String formatManifest(java.util.Collection<String> classNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("# migration precompiled classes manifest\n");
        sb.append("# format: one fully-qualified class name per line\n");
        for (String className : classNames) {
            sb.append(className).append('\n');
        }
        return sb.toString();
    }

    /**
     * 将类全限定名转换为 class 文件路径。
     * 如 {@code com.weacsoft.Foo} -> {@code com/weacsoft/Foo.class}
     *
     * @param className 类全限定名
     * @return class 文件路径
     */
    private static String classNameToPath(String className) {
        return className.replace('.', '/') + CLASS_SUFFIX;
    }
}
