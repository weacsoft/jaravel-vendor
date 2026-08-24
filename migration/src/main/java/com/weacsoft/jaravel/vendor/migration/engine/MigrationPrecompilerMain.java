package com.weacsoft.jaravel.vendor.migration.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 迁移文件预编译命令行工具。
 * <p>
 * 在开发阶段（有 JDK 环境）将迁移 {@code .java} 文件预编译为字节码，
 * 支持打包为 zip 文件（PACKAGED 模式）或输出为散乱 class 文件（CLASSES 模式）。
 * 预编译后，生产环境只需 JRE 即可运行迁移。
 * <p>
 * <b>用法</b>：
 * <pre>
 * java -cp migration.jar MigrationPrecompilerMain \
 *   --source-dir=migrations \
 *   --output-dir=precompiled \
 *   --mode=packaged \          # 或 classes
 *   --file-name=migrations.jmigration.zip  # 仅 packaged 模式
 * </pre>
 * <p>
 * <b>支持的参数</b>：
 * <ul>
 *   <li>{@code --source-dir=<path>}   迁移 .java 文件所在目录（必填）</li>
 *   <li>{@code --output-dir=<path>}   输出目录（必填）</li>
 *   <li>{@code --mode=<mode>}         编译模式：packaged 或 classes（默认 packaged）</li>
 *   <li>{@code --file-name=<name>}    打包文件名（仅 packaged 模式，默认 migrations.jmigration.zip）</li>
 * </ul>
 * <p>
 * <b>示例</b>：
 * <pre>
 * # 打包为 zip 文件
 * java -cp migration.jar MigrationPrecompilerMain \
 *   --source-dir=migrations \
 *   --output-dir=precompiled \
 *   --mode=packaged \
 *   --file-name=migrations.jmigration.zip
 *
 * # 输出为散乱 class 文件
 * java -cp migration.jar MigrationPrecompilerMain \
 *   --source-dir=migrations \
 *   --output-dir=precompiled \
 *   --mode=classes
 * </pre>
 * <p>
 * <b>注意</b>：此工具需要 JDK 运行（因为它要编译 .java 文件）。
 * 预编译产物加载时只需要 JRE。
 *
 * @see MigrationPrecompiler
 */
public class MigrationPrecompilerMain {

    /**
     * 主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            System.exit(1);
        }

        try {
            Map<String, String> opts = parseOptions(args);

            // 校验必填参数
            String sourceDir = opts.get("source-dir");
            String outputDir = opts.get("output-dir");
            if (sourceDir == null || sourceDir.isEmpty()) {
                System.err.println("错误：缺少必填参数 --source-dir");
                printUsage();
                System.exit(1);
            }
            if (outputDir == null || outputDir.isEmpty()) {
                System.err.println("错误：缺少必填参数 --output-dir");
                printUsage();
                System.exit(1);
            }

            // 解析模式
            String modeStr = opts.getOrDefault("mode", "packaged").toLowerCase();
            MigrationPrecompiler.CompileMode mode;
            if ("packaged".equals(modeStr)) {
                mode = MigrationPrecompiler.CompileMode.PACKAGED;
            } else if ("classes".equals(modeStr)) {
                mode = MigrationPrecompiler.CompileMode.CLASSES;
            } else {
                System.err.println("错误：未知的编译模式 --mode=" + modeStr + "（支持: packaged / classes）");
                printUsage();
                System.exit(1);
                return;
            }

            // 解析文件名
            String fileName = opts.get("file-name");

            // 执行预编译
            MigrationPrecompiler precompiler = new MigrationPrecompiler(sourceDir);
            int count = precompiler.compileAll(outputDir, mode, fileName);

            System.out.println("[migration] 预编译成功完成，共编译 " + count + " 个迁移类");
        } catch (Exception e) {
            System.err.println("[migration] 预编译失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 解析 --key=value 格式的选项。
     *
     * @param args 命令行参数
     * @return 选项映射表
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                String key = arg.substring(2, eq);
                String value = arg.substring(eq + 1);
                opts.put(key, value);
            }
        }
        return opts;
    }

    /**
     * 打印用法。
     */
    private static void printUsage() {
        System.out.println("用法: java -cp <classpath> MigrationPrecompilerMain --source-dir=<path> --output-dir=<path> [options]");
        System.out.println();
        System.out.println("必填参数:");
        System.out.println("  --source-dir=<path>   迁移 .java 文件所在目录");
        System.out.println("  --output-dir=<path>   输出目录");
        System.out.println();
        System.out.println("可选参数:");
        System.out.println("  --mode=<mode>         编译模式：packaged 或 classes（默认 packaged）");
        System.out.println("  --file-name=<name>    打包文件名（仅 packaged 模式，默认 migrations.jmigration.zip）");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 打包为 zip 文件");
        System.out.println("  java -cp migration.jar MigrationPrecompilerMain \\");
        System.out.println("    --source-dir=migrations --output-dir=precompiled --mode=packaged");
        System.out.println();
        System.out.println("  # 输出为散乱 class 文件");
        System.out.println("  java -cp migration.jar MigrationPrecompilerMain \\");
        System.out.println("    --source-dir=migrations --output-dir=precompiled --mode=classes");
    }
}
