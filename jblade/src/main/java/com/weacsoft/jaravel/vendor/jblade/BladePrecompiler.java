package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Blade 模板预编译工具。
 * <p>
 * 在开发阶段（有 JDK 环境）将所有 Blade 模板预编译为字节码，支持两种输出模式：
 * <ul>
 *   <li>模式一：打包为单个文件（.jblade.zip 或自定义后缀），运行时整体加载</li>
 *   <li>模式二：输出为散乱的 .class 文件到目录，运行时从目录加载</li>
 * </ul>
 * 预编译后，运行时无需 JDK，只需 JRE 即可运行。
 * <p>
 * 典型用法：
 * <pre>
 * BladePrecompiler precompiler = new BladePrecompiler("templates", ".blade.java");
 * precompiler.compileAll("precompiled", BladePrecompiler.CompileMode.PACKAGED, "myapp", ".jblade.zip");
 * </pre>
 */
public class BladePrecompiler {

    /**
     * 预编译模式
     */
    public enum CompileMode {
        /** 打包为单个文件 */
        PACKAGED,
        /** 散乱 class 文件到目录 */
        CLASSES
    }

    private final BladeCompiler compiler;
    private final MemoryClassLoader classLoader;
    private final String templateDir;
    private final String suffix;

    /**
     * 创建预编译工具。
     *
     * @param templateDir 模板目录路径
     * @param suffix      模板文件后缀（null 时使用默认值 .blade.java）
     */
    public BladePrecompiler(String templateDir, String suffix) {
        this.templateDir = templateDir;
        this.suffix = (suffix != null && !suffix.isEmpty()) ? suffix : BladeCompiler.DEFAULT_SUFFIX;
        // 创建 BladeCompiler 用于生成源码和编译
        // 注意：BladePrecompiler 本身需要 JDK 运行（因为它要编译模板）
        this.classLoader = new MemoryClassLoader();
        this.compiler = new BladeCompiler(templateDir, this.classLoader, this.suffix);
    }

    /**
     * 预编译指定目录下的所有模板。
     *
     * @param outputDir   输出目录
     * @param mode        编译模式 (PACKAGED 或 CLASSES)
     * @param packageName 打包文件名前缀（仅 PACKAGED 模式，null 时默认 "templates"）
     * @param fileSuffix  打包文件后缀（仅 PACKAGED 模式，null 时默认 ".jblade.zip"）
     * @return 编译成功的模板数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAll(String outputDir, CompileMode mode, String packageName, String fileSuffix) throws IOException {
        CompileResult result = compileAllTemplates();

        // 输出
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        if (mode == CompileMode.PACKAGED) {
            String name = (packageName != null && !packageName.isEmpty()) ? packageName : "templates";
            String ext = (fileSuffix != null && !fileSuffix.isEmpty()) ? fileSuffix : ".jblade.zip";
            String packagePath = new File(outputDirectory, name + ext).getAbsolutePath();
            PrecompiledTemplateLoader.saveToPackage(packagePath,
                    result.classBytecodes, result.templateToClassMapping);
            System.out.println("[jblade] 打包文件已生成: " + packagePath);
        } else {
            String classesDirPath = outputDirectory.getAbsolutePath();
            PrecompiledTemplateLoader.saveToDirectory(classesDirPath,
                    result.classBytecodes, result.templateToClassMapping);
            System.out.println("[jblade] class 文件已输出到: " + classesDirPath);
        }

        System.out.println("[jblade] 预编译完成: 成功 " + result.successCount + ", 失败 " + result.failedCount);
        return result.successCount;
    }

    /**
     * 便利方法：预编译所有模板并打包为单个 zip 文件。
     *
     * @param outputDir 输出目录
     * @param fileName  打包文件名（如 "myapp.jblade.zip"，null 时默认 "templates.jblade.zip"）
     * @return 编译成功的模板数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAllToZip(String outputDir, String fileName) throws IOException {
        CompileResult result = compileAllTemplates();

        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        String name = (fileName != null && !fileName.isEmpty()) ? fileName : "templates.jblade.zip";
        String packagePath = new File(outputDirectory, name).getAbsolutePath();
        PrecompiledTemplateLoader.saveToPackage(packagePath,
                result.classBytecodes, result.templateToClassMapping);
        System.out.println("[jblade] 打包文件已生成: " + packagePath);
        System.out.println("[jblade] 预编译完成: 成功 " + result.successCount + ", 失败 " + result.failedCount);
        return result.successCount;
    }

    /**
     * 便利方法：预编译所有模板并输出为散乱 class 文件。
     *
     * @param outputDir 输出目录
     * @return 编译成功的模板数量
     * @throws IOException 如果 IO 出错
     */
    public int compileAllToClasses(String outputDir) throws IOException {
        return compileAll(outputDir, CompileMode.CLASSES, null, null);
    }

    // ===== 内部实现 =====

    /**
     * 编译结果。
     */
    private static class CompileResult {
        final Map<String, byte[]> classBytecodes = new LinkedHashMap<>();
        final Map<String, String> templateToClassMapping = new LinkedHashMap<>();
        int successCount = 0;
        int failedCount = 0;
    }

    /**
     * 扫描并编译所有模板。
     */
    private CompileResult compileAllTemplates() throws IOException {
        CompileResult result = new CompileResult();

        // 1. 解析模板根目录
        File templateRoot = resolveTemplateRoot();
        System.out.println("[jblade] 扫描模板目录: " + templateRoot.getAbsolutePath());

        // 2. 扫描所有模板文件
        List<String> templateNames = new ArrayList<>();
        scanTemplates(templateRoot, "", templateNames);
        Collections.sort(templateNames);
        System.out.println("[jblade] 发现 " + templateNames.size() + " 个模板");

        if (templateNames.isEmpty()) {
            System.out.println("[jblade] 未找到模板文件");
            return result;
        }

        // 3. 逐个编译
        for (String templateName : templateNames) {
            try {
                // 直接读取模板文件内容
                String relativePath = templateName.replace('.', '/') + suffix;
                File templateFile = new File(templateRoot, relativePath);
                String content = readFile(templateFile);

                // 编译（字节码存入 classLoader）
                String className = compiler.compileSource(templateName, content);

                // 从 classLoader 获取字节码
                byte[] bytecode = classLoader.getCompiledClasses().get(className);
                if (bytecode != null) {
                    result.classBytecodes.put(className, bytecode);
                    result.templateToClassMapping.put(templateName, className);
                    result.successCount++;
                    System.out.println("[jblade] 编译成功: " + templateName + " -> " + className);
                } else {
                    result.failedCount++;
                    System.err.println("[jblade] 编译失败(无字节码): " + templateName);
                }
            } catch (Exception e) {
                result.failedCount++;
                System.err.println("[jblade] 编译失败: " + templateName + " - " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 解析模板根目录。
     * <p>
     * 优先使用 {@code templateDir} 直接作为目录；若不存在则尝试 {@code resources/templateDir}
     * （与 BladeCompiler 的资源加载逻辑一致）。
     */
    private File resolveTemplateRoot() throws FileNotFoundException {
        File direct = new File(templateDir);
        if (direct.isDirectory()) {
            return direct;
        }
        File underResources = new File("resources", templateDir);
        if (underResources.isDirectory()) {
            return underResources;
        }
        throw new FileNotFoundException(
                "模板目录不存在: " + templateDir + " (也尝试了 resources/" + templateDir + ")");
    }

    /**
     * 递归扫描目录下的模板文件，收集模板名。
     *
     * @param dir          当前扫描目录
     * @param relativePath 相对于模板根目录的路径（用 / 分隔）
     * @param templates    收集到的模板名列表
     */
    private void scanTemplates(File dir, String relativePath, List<String> templates) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                String subPath = relativePath.isEmpty() ? name : relativePath + "/" + name;
                scanTemplates(file, subPath, templates);
            } else if (name.endsWith(suffix)) {
                String path = relativePath.isEmpty() ? name : relativePath + "/" + name;
                // 去掉后缀
                String nameWithoutSuffix = path.substring(0, path.length() - suffix.length());
                // 路径分隔符转换为点号，得到模板名
                String templateName = nameWithoutSuffix.replace('/', '.').replace('\\', '.');
                templates.add(templateName);
            }
        }
    }

    /**
     * 读取文件内容为字符串（UTF-8）。
     */
    private String readFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
