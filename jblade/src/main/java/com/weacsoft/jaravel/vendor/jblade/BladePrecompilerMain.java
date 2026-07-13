package com.weacsoft.jaravel.vendor.jblade;

/**
 * jblade 预编译命令行工具。
 * <p>
 * 在开发阶段（有 JDK 环境）将所有 Blade 模板预编译为字节码，预编译后运行时无需 JDK。
 * <p>
 * 用法：
 * <pre>
 * java -cp jblade.jar com.weacsoft.jaravel.vendor.jblade.BladePrecompilerMain \
 *   --template-dir=templates \
 *   --suffix=.blade.java \
 *   --output-dir=precompiled \
 *   --mode=packaged \          # 或 classes
 *   --package-name=myapp \     # 仅 packaged 模式
 *   --file-suffix=.jblade.zip  # 仅 packaged 模式，默认 .jblade.zip
 * </pre>
 */
public class BladePrecompilerMain {

    public static void main(String[] args) {
        String templateDir = null;
        String suffix = BladeCompiler.DEFAULT_SUFFIX;
        String outputDir = "precompiled";
        String mode = "packaged";
        String packageName = "templates";
        String fileSuffix = ".jblade.zip";

        // 解析参数
        for (String arg : args) {
            if (arg.startsWith("--template-dir=")) {
                templateDir = arg.substring("--template-dir=".length());
            } else if (arg.startsWith("--suffix=")) {
                suffix = arg.substring("--suffix=".length());
            } else if (arg.startsWith("--output-dir=")) {
                outputDir = arg.substring("--output-dir=".length());
            } else if (arg.startsWith("--mode=")) {
                mode = arg.substring("--mode=".length());
            } else if (arg.startsWith("--package-name=")) {
                packageName = arg.substring("--package-name=".length());
            } else if (arg.startsWith("--file-suffix=")) {
                fileSuffix = arg.substring("--file-suffix=".length());
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                return;
            }
        }

        if (templateDir == null || templateDir.isEmpty()) {
            System.err.println("错误: 必须指定 --template-dir 参数");
            printUsage();
            System.exit(1);
            return;
        }

        BladePrecompiler.CompileMode compileMode;
        if ("classes".equalsIgnoreCase(mode)) {
            compileMode = BladePrecompiler.CompileMode.CLASSES;
        } else if ("packaged".equalsIgnoreCase(mode)) {
            compileMode = BladePrecompiler.CompileMode.PACKAGED;
        } else {
            System.err.println("错误: --mode 仅支持 packaged 或 classes，当前值: " + mode);
            printUsage();
            System.exit(1);
            return;
        }

        try {
            BladePrecompiler precompiler = new BladePrecompiler(templateDir, suffix);
            int count = precompiler.compileAll(outputDir, compileMode, packageName, fileSuffix);
            System.out.println("预编译完成，共编译 " + count + " 个模板");
            if (count == 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("预编译失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("jblade 预编译命令行工具");
        System.out.println("在开发阶段（有 JDK 环境）将所有 Blade 模板预编译为字节码。");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java -cp jblade.jar com.weacsoft.jaravel.vendor.jblade.BladePrecompilerMain \\");
        System.out.println("    --template-dir=templates \\");
        System.out.println("    --suffix=.blade.java \\");
        System.out.println("    --output-dir=precompiled \\");
        System.out.println("    --mode=packaged \\          # 或 classes");
        System.out.println("    --package-name=myapp \\     # 仅 packaged 模式");
        System.out.println("    --file-suffix=.jblade.zip  # 仅 packaged 模式");
        System.out.println();
        System.out.println("参数说明:");
        System.out.println("  --template-dir   模板目录路径 (必需)");
        System.out.println("  --suffix         模板文件后缀 (默认: .blade.java)");
        System.out.println("  --output-dir     输出目录 (默认: precompiled)");
        System.out.println("  --mode           编译模式: packaged 或 classes (默认: packaged)");
        System.out.println("  --package-name   打包文件名前缀 (仅 packaged 模式, 默认: templates)");
        System.out.println("  --file-suffix    打包文件后缀 (仅 packaged 模式, 默认: .jblade.zip)");
        System.out.println();
        System.out.println("输出说明:");
        System.out.println("  packaged 模式: 生成单个 .jblade.zip 文件，包含 manifest.txt 和所有 .class");
        System.out.println("  classes  模式: 输出散乱 .class 文件和 manifest.txt 到目录");
        System.out.println();
        System.out.println("运行时加载预编译模板:");
        System.out.println("  BladeEngine.fromPrecompiledPackage(\"path/to/templates.jblade.zip\")");
        System.out.println("  BladeEngine.fromPrecompiledClasses(\"path/to/precompiled\")");
    }
}
