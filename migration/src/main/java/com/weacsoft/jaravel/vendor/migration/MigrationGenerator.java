package com.weacsoft.jaravel.vendor.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 迁移类源码生成器，对齐 Laravel {@code php artisan make:migration} 命令。
 * <p>
 * 按 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 命名约定生成一个空的迁移 Java 源文件，
 * 类名自带日期前缀，{@link Migrator} 按类名字典序排序即可获得正确的执行顺序。
 * 该约定与 <a href="https://github.com/weacsoft/database-all-support">weacsoft/database-all-support</a>
 * 框架保持一致。
 * <p>
 * 用法示例：
 * <pre>
 * MigrationGenerator.generate("src/main/java", "com.weacsoft.jaravel.database.migration", "create products table");
 * // 生成文件：src/main/java/com/weacsoft/jaravel/database/migration/Migration_2024_06_20_CreateProductsTable.java
 * </pre>
 * 生成的类继承自 {@link Migration} 接口，包含空的 {@code up()} 与 {@code down()} 方法，由开发者自行填充。
 */
public final class MigrationGenerator {

    /** 日期格式：YYYY_MM_DD，用于类名前缀 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private MigrationGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 生成一个迁移 Java 源文件。
     * <p>
     * 文件名为 {@code Migration_YYYY_MM_DD_PascalCaseDescription.java}，其中 {@code YYYY_MM_DD} 为当前日期，
     * {@code PascalCaseDescription} 由 {@code description} 转换而来（去除非字母数字字符后转 PascalCase）。
     * 若目标文件已存在，将抛出 {@link IllegalStateException} 以避免覆盖已有迁移。
     *
     * @param outputDir    输出根目录（如 {@code "src/main/java"}），可为相对或绝对路径
     * @param packageName  生成类所属的包名（如 {@code "com.weacsoft.jaravel.database.migration"}）
     * @param description  迁移描述（如 {@code "create products table"} 或 {@code "add email to users table"}）
     * @return 生成的文件绝对路径
     * @throws IOException              写入文件失败
     * @throws IllegalArgumentException 参数为空或包名非法
     * @throws IllegalStateException     目标文件已存在
     */
    public static String generate(String outputDir, String packageName, String description) throws IOException {
        if (outputDir == null || outputDir.trim().isEmpty()) {
            throw new IllegalArgumentException("outputDir 不能为空");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName 不能为空");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("description 不能为空");
        }

        String datePrefix = LocalDate.now().format(DATE_FORMATTER);
        String pascalName = toPascalCase(description);
        String className = "Migration_" + datePrefix + "_" + pascalName;

        // 包名 -> 目录路径
        String packagePath = packageName.replace('.', '/');
        Path dir = Paths.get(outputDir, packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        if (Files.exists(file)) {
            throw new IllegalStateException("迁移文件已存在，拒绝覆盖: " + file.toAbsolutePath());
        }

        String content = buildClassSource(packageName, className, description);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));

        return file.toAbsolutePath().toString();
    }

    /**
     * 构建迁移类的 Java 源码。
     *
     * @param packageName 包名
     * @param className   类名（含 Migration_YYYY_MM_DD_ 前缀）
     * @param description 原始描述（写入 Javadoc）
     * @return Java 源码字符串
     */
    private static String buildClassSource(String packageName, String className, String description) {
        String sb = "package " + packageName + ";\n\n" +
                "import com.weacsoft.jaravel.vendor.migration.Migration;\n" +
                "import com.weacsoft.jaravel.vendor.migration.Schema;\n" +
                "import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;\n\n" +
                "/**\n" +
                " * 迁移：" + description + "。\n" +
                " * <p>\n" +
                " * 类名采用 {@code Migration_YYYY_MM_DD_PascalCaseDescription} 约定，\n" +
                " * {@link Migration#getName()} 默认返回类名，{@code Migrator} 按类名字典序排序即可保证执行顺序。\n" +
                " * 一次 {@code up()} 可处理多张表，{@code down()} 应对称回滚。\n" +
                " * <p>\n" +
                " * 使用 {@code @MigrationAnnotation} 标记（非 Spring {@code @Component}），\n" +
                " * 迁移文件在运行时由 {@code MigrationScanner} 内存编译、反射实例化、执行后自动释放。\n" +
                " */\n" +
                "@MigrationAnnotation\n" +
                "public class " + className + " implements Migration {\n\n" +
                "    @Override\n" +
                "    public void up(Schema schema) {\n" +
                "        // TODO: 在此编写正向迁移逻辑，例如：\n" +
                "        // schema.create(\"table_name\", table -> {\n" +
                "        //     table.id();\n" +
                "        //     table.string(\"name\");\n" +
                "        //     table.timestamps();\n" +
                "        // });\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public void down(Schema schema) {\n" +
                "        // TODO: 在此编写回滚逻辑，例如：\n" +
                "        // schema.dropIfExists(\"table_name\");\n" +
                "    }\n" +
                "}\n";
        return sb;
    }

    /**
     * 将任意描述字符串转换为 PascalCase。
     * <p>
     * 处理规则：
     * <ul>
     *   <li>以空格、下划线、连字符等非字母数字字符作为单词分隔；</li>
     *   <li>每个单词首字母大写、其余小写；</li>
     *   <li>去除所有非字母数字字符；</li>
     *   <li>若结果为空（描述全是非法字符），返回 "Migration"。</li>
     * </ul>
     * 示例：
     * <ul>
     *   <li>{@code "create users table"} -&gt; {@code "CreateUsersTable"}</li>
     *   <li>{@code "add_email_to_users_table"} -&gt; {@code "AddEmailToUsersTable"}</li>
     *   <li>{@code "create-products-table"} -&gt; {@code "CreateProductsTable"}</li>
     * </ul>
     *
     * @param description 原始描述
     * @return PascalCase 形式的字符串
     */
    static String toPascalCase(String description) {
        // 以非字母数字字符作为分隔
        String[] words = description.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        if (sb.length() == 0) {
            return "Migration";
        }
        return sb.toString();
    }
}
