package com.weacsoft.jaravel.vendor.jblade;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * 预编译模板加载器。
 * <p>
 * 支持从打包文件（.jblade.zip）或散乱 class 文件目录加载预编译的模板字节码。
 * <p>
 * 打包文件 / 目录格式：
 * <ul>
 *   <li>{@code manifest.txt}：模板名 -> 类名的映射（每行: {@code templateName=className}，
 *       以 {@code #} 开头的行视为注释）</li>
 *   <li>其余 {@code .class} 文件按包结构存储（如 {@code com/weacsoft/Foo.class}）</li>
 * </ul>
 * <p>
 * 运行时使用此加载器加载预编译产物后，{@link BladeEngine} 无需 JDK 即可渲染模板。
 */
public class PrecompiledTemplateLoader {

    /** manifest 文件名 */
    static final String MANIFEST_NAME = "manifest.txt";
    /** class 文件后缀 */
    static final String CLASS_SUFFIX = ".class";

    private PrecompiledTemplateLoader() {
    }

    // ===== 预编译数据包 =====

    /**
     * 预编译数据包，包含字节码映射和模板名->类名映射。
     */
    public static class PrecompiledBundle {

        /** 类全限定名 -> 字节码 */
        public final Map<String, byte[]> classBytecodes;
        /** 模板名 -> 类全限定名 */
        public final Map<String, String> templateToClassMapping;

        public PrecompiledBundle(Map<String, byte[]> classBytecodes,
                                 Map<String, String> templateToClassMapping) {
            this.classBytecodes = classBytecodes;
            this.templateToClassMapping = templateToClassMapping;
        }
    }

    // ===== 从打包文件加载 =====

    /**
     * 从打包文件加载所有模板字节码和映射。
     *
     * @param packagePath 打包文件路径（.jblade.zip）
     * @return 包含字节码映射和模板名->类名映射的数据包
     * @throws IOException 如果读取失败
     */
    public static PrecompiledBundle loadBundleFromPackage(String packagePath) throws IOException {
        Map<String, byte[]> classBytecodes = new LinkedHashMap<>();
        Map<String, String> templateToClassMapping = new LinkedHashMap<>();

        try (ZipFile zipFile = new ZipFile(packagePath)) {
            // 1. 读取 manifest.txt 获取模板名->类名映射
            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_NAME);
            if (manifestEntry != null) {
                try (InputStream is = zipFile.getInputStream(manifestEntry)) {
                    templateToClassMapping = parseManifest(is);
                }
            }

            // 2. 读取所有 .class 文件的字节码
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(CLASS_SUFFIX) && !entry.isDirectory()) {
                    String className = pathToClassName(name);
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        classBytecodes.put(className, readAllBytes(is));
                    }
                }
            }
        }

        return new PrecompiledBundle(classBytecodes, templateToClassMapping);
    }

    /**
     * 从打包文件加载所有模板字节码。
     *
     * @param packagePath 打包文件路径（.jblade.zip）
     * @return 类全限定名 -> 字节码
     * @throws IOException 如果读取失败
     */
    public static Map<String, byte[]> loadFromPackage(String packagePath) throws IOException {
        return loadBundleFromPackage(packagePath).classBytecodes;
    }

    // ===== 从目录加载 =====

    /**
     * 从目录加载所有模板字节码和映射。
     *
     * @param dirPath 目录路径
     * @return 包含字节码映射和模板名->类名映射的数据包
     * @throws IOException 如果读取失败
     */
    public static PrecompiledBundle loadBundleFromDirectory(String dirPath) throws IOException {
        Map<String, byte[]> classBytecodes = new LinkedHashMap<>();
        Map<String, String> templateToClassMapping = new LinkedHashMap<>();

        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            throw new IOException("预编译目录不存在: " + dirPath);
        }

        // 1. 读取 manifest.txt
        File manifestFile = new File(dir, MANIFEST_NAME);
        if (manifestFile.isFile()) {
            try (InputStream is = new FileInputStream(manifestFile)) {
                templateToClassMapping = parseManifest(is);
            }
        }

        // 2. 递归扫描所有 .class 文件
        List<File> classFiles = new ArrayList<>();
        collectClassFiles(dir, classFiles);
        for (File classFile : classFiles) {
            String relativePath = dir.toPath().relativize(classFile.toPath())
                    .toString().replace(File.separatorChar, '/');
            String className = pathToClassName(relativePath);
            try (InputStream is = new FileInputStream(classFile)) {
                classBytecodes.put(className, readAllBytes(is));
            }
        }

        return new PrecompiledBundle(classBytecodes, templateToClassMapping);
    }

    /**
     * 从目录加载所有模板字节码。
     *
     * @param dirPath 目录路径
     * @return 类全限定名 -> 字节码
     * @throws IOException 如果读取失败
     */
    public static Map<String, byte[]> loadFromDirectory(String dirPath) throws IOException {
        return loadBundleFromDirectory(dirPath).classBytecodes;
    }

    // ===== 保存到打包文件 =====

    /**
     * 保存模板字节码到打包文件。
     *
     * @param packagePath            打包文件输出路径
     * @param classBytecodes         类全限定名 -> 字节码
     * @param templateToClassMapping 模板名 -> 类全限定名
     * @throws IOException 如果写入失败
     */
    public static void saveToPackage(String packagePath, Map<String, byte[]> classBytecodes,
                                     Map<String, String> templateToClassMapping) throws IOException {
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
            zos.write(formatManifest(templateToClassMapping).getBytes(StandardCharsets.UTF_8));
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

    // ===== 保存到目录 =====

    /**
     * 保存模板字节码到目录。
     *
     * @param dirPath                目录路径
     * @param classBytecodes         类全限定名 -> 字节码
     * @param templateToClassMapping 模板名 -> 类全限定名
     * @throws IOException 如果写入失败
     */
    public static void saveToDirectory(String dirPath, Map<String, byte[]> classBytecodes,
                                       Map<String, String> templateToClassMapping) throws IOException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 1. 写入 manifest.txt
        File manifestFile = new File(dir, MANIFEST_NAME);
        try (OutputStream os = new FileOutputStream(manifestFile)) {
            os.write(formatManifest(templateToClassMapping).getBytes(StandardCharsets.UTF_8));
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

    // ===== 内部工具方法 =====

    /**
     * 解析 manifest 输入流为模板名->类名映射。
     * <p>
     * 格式：每行 {@code templateName=className}，以 {@code #} 开头的行视为注释。
     */
    static Map<String, String> parseManifest(InputStream is) throws IOException {
        Map<String, String> mapping = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String templateName = line.substring(0, eq).trim();
                    String className = line.substring(eq + 1).trim();
                    if (!templateName.isEmpty() && !className.isEmpty()) {
                        mapping.put(templateName, className);
                    }
                }
            }
        }
        return mapping;
    }

    /**
     * 将模板名->类名映射格式化为 manifest 文本。
     */
    static String formatManifest(Map<String, String> templateToClassMapping) {
        StringBuilder sb = new StringBuilder();
        sb.append("# jblade precompiled template manifest\n");
        sb.append("# format: templateName=className\n");
        for (Map.Entry<String, String> entry : templateToClassMapping.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 将 class 文件路径转换为类全限定名。
     * 如 {@code com/weacsoft/Foo.class} -> {@code com.weacsoft.Foo}
     */
    private static String pathToClassName(String path) {
        String name = path;
        if (name.endsWith(CLASS_SUFFIX)) {
            name = name.substring(0, name.length() - CLASS_SUFFIX.length());
        }
        return name.replace('/', '.').replace('\\', '.');
    }

    /**
     * 将类全限定名转换为 class 文件路径。
     * 如 {@code com.weacsoft.Foo} -> {@code com/weacsoft/Foo.class}
     */
    private static String classNameToPath(String className) {
        return className.replace('.', '/') + CLASS_SUFFIX;
    }

    /**
     * 递归收集目录下所有 .class 文件。
     */
    private static void collectClassFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectClassFiles(file, result);
            } else if (file.getName().endsWith(CLASS_SUFFIX)) {
                result.add(file);
            }
        }
    }

    /**
     * 读取输入流的全部字节。
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        return bos.toByteArray();
    }
}
