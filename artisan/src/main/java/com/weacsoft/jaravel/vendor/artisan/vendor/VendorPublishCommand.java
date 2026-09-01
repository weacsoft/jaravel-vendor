package com.weacsoft.jaravel.vendor.artisan.vendor;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import com.weacsoft.jaravel.vendor.core.publish.PublishableMigration;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;
import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;
import com.weacsoft.jaravel.vendor.core.publish.PublishType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code vendor:publish} 命令，对齐 Laravel 的 {@code php artisan vendor:publish}。
 * <p>
 * <b>静态扫描</b>{@link PublishableRegistry} 中所有已注册的可发布项（{@link PublishableConfig} 配置类源码 +
 * {@link PublishableStatic} 静态前端资源），一次扫描、按需发布，不再区分两条命令。
 * <p>
 * 各模块通过 {@link PublishableRegistry#register(Publishable)} 注册自己的可发布项，
 * 不需要任何 Spring Bean。
 *
 * <h3>用法</h3>
 * <pre>
 * artisan vendor:publish                          # 列出所有可发布项（配置 + 资源 + 迁移）
 * artisan vendor:publish --all                    # 发布全部（配置类 + 静态资源 + 迁移文件）
 * artisan vendor:publish --tag=cache              # 只发布 cache 模块（其配置与资源）
 * artisan vendor:publish --tag=resources          # 只发布全部静态前端资源
 * artisan vendor:publish --tag=config             # 只发布全部 Java 配置类
 * artisan vendor:publish --tag=migrations         # 发布所有模块的建表迁移文件（对齐 Laravel vendor:publish --tag=migrations）
 * artisan vendor:publish --tag=cache-database     # 只发布 cache-database 模块的迁移
 * artisan vendor:publish --tag=captcha --force    # 覆盖已存在文件
 * </pre>
 *
 * <h3>迁移文件发布</h3>
 * 实现 {@link PublishableMigration} 的模块（cache-database / storage-database /
 * queue-database 等）把<b>内置迁移 Java 源文件</b>（打包在模块 jar 内）发布到业务工程的
 * 迁移源代码目录（{@code MakeCodeProperties#getMigrationSourceDir}，默认
 * {@code src/main/java/<basePackage 路径>/database/migrations}），
 * 并自动把包名重写为工程迁移包（{@code <basePackage>.database.migrations}）。
 * 随后执行 {@code artisan migrate} 即完成全部模块的建表——对齐 Laravel 的工作流：
 * 模块自带迁移 → vendor:publish 发布到业务工程 → migrate 执行。
 *
 * <h3>可选依赖说明</h3>
 * 本命令通过 {@link PublishableRegistry} 扫描已注册的可发布项。
 * 若工程未引入任何声明了可发布项的模块，列表为空，命令只提示无可发布项，不会报错。
 */
public class VendorPublishCommand extends ArtisanCommand {

    private final MakeCodeProperties properties;

    public VendorPublishCommand(MakeCodeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String signature() {
        return "vendor:publish {--tag} {--all} {--force} {--list}";
    }

    @Override
    public String description() {
        return "发布模块配置类与静态前端资源（统一处理配置与资源）";
    }

    @Override
    public int handle() {
        List<Publishable> publishables = PublishableRegistry.list();
        if (publishables.isEmpty()) {
            warn("没有任何可发布项。");
            info("请确认已引入对应 jaravel 模块（如 jaravel-cache / jaravel-wire）。");
            return 0;
        }

        if (hasOption("list")) {
            printList(publishables);
            return 0;
        }

        List<Publishable> targets = resolveTargets(publishables);
        if (targets == null) {
            return 1;
        }
        if (targets.isEmpty()) {
            return 0;
        }

        boolean force = hasOption("force");
        Counters c = new Counters();
        for (Publishable p : targets) {
            if (p instanceof PublishableConfig cfg) {
                publishConfig(cfg, force, c);
            } else if (p instanceof PublishableStatic st) {
                publishStatic(st, force, c);
            } else if (p instanceof PublishableMigration mig) {
                publishMigration(mig, force, c);
            }
        }

        info("");
        info("发布完成: 新增 " + c.published + " 个，跳过 " + c.skipped + " 个"
                + (c.failed > 0 ? "，失败 " + c.failed + " 个。" : "。"));
        return c.failed > 0 ? 1 : 0;
    }

    /** 发布计数聚合（published / skipped / failed）。 */
    private static final class Counters {
        int published;
        int skipped;
        int failed;
    }

    /**
     * 解析本次要发布的目标集合。
     *
     * @return null 表示参数非法（调用方返回失败码）；空列表表示无需写文件
     */
    private List<Publishable> resolveTargets(List<Publishable> all) {
        if (hasOption("all")) {
            return new ArrayList<>(all);
        }

        String tag = option("tag");
        if (tag != null && !tag.isEmpty() && !"true".equals(tag)) {
            if ("resources".equals(tag)) {
                return filterByType(all, PublishType.RESOURCE);
            }
            if ("config".equals(tag)) {
                return filterByType(all, PublishType.CONFIG);
            }
            if ("migrations".equals(tag)) {
                return filterByType(all, PublishType.MIGRATION);
            }
            List<Publishable> matched = new ArrayList<>();
            for (Publishable p : all) {
                if (tag.equals(p.tag())) {
                    matched.add(p);
                }
            }
            if (matched.isEmpty()) {
                error("未知的 tag: " + tag);
                info("可用的 tag: " + String.join(", ", tags(all))
                        + "（保留标签: resources=全部静态资源, config=全部配置类, migrations=全部模块建表迁移）");
                return null;
            }
            return matched;
        }

        // 未指定任何选项时，展示清单并提示用法，避免误覆盖用户文件
        printList(all);
        info("");
        info("请使用 --all 发布全部；--tag=<标签> 发布指定模块；"
                + "--tag=resources 发布静态资源；--tag=config 发布配置类；"
                + "--tag=migrations 发布所有模块的建表迁移文件（之后执行 artisan migrate）。");
        return new ArrayList<>();
    }

    private List<Publishable> filterByType(List<Publishable> all, PublishType type) {
        List<Publishable> r = new ArrayList<>();
        for (Publishable p : all) {
            if (p.type() == type) {
                r.add(p);
            }
        }
        return r;
    }

    private List<String> tags(List<Publishable> all) {
        List<String> t = new ArrayList<>();
        for (Publishable p : all) {
            if (!t.contains(p.tag())) {
                t.add(p.tag());
            }
        }
        return t;
    }

    private void printList(List<Publishable> all) {
        Map<String, List<Publishable>> grouped = new LinkedHashMap<>();
        for (Publishable p : all) {
            grouped.computeIfAbsent(p.tag(), k -> new ArrayList<>()).add(p);
        }

        info("可发布项（标签 / 类型 / 描述）:");
        for (Map.Entry<String, List<Publishable>> entry : grouped.entrySet()) {
            info("  [" + entry.getKey() + "]");
            for (Publishable p : entry.getValue()) {
                PublishType t = p.type();
                String type = t == PublishType.RESOURCE ? "resource"
                        : (t == PublishType.MIGRATION ? "migration" : "config");
                String name;
                if (p instanceof PublishableConfig c) {
                    name = c.className();
                } else if (p instanceof PublishableStatic s) {
                    name = String.join(", ", s.resources().values());
                } else if (p instanceof PublishableMigration m) {
                    List<String> files = new ArrayList<>();
                    for (Map.Entry<String, String> f : m.migrationFiles()) {
                        files.add(f.getValue());
                    }
                    name = String.join(", ", files);
                } else {
                    name = p.tag();
                }
                String desc = p.description();
                info("    - (" + type + ") " + name + (desc.isEmpty() ? "" : "  # " + desc));
            }
        }
    }

    private void publishConfig(PublishableConfig config, boolean force, Counters c) {
        Path configDir = resolveConfigDir();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            error("无法创建配置目录: " + configDir + " (" + e.getMessage() + ")");
            c.failed++;
            return;
        }

        Path target = configDir.resolve(config.className() + ".java");
        if (Files.exists(target) && !force) {
            warn("已存在，跳过: " + target + "  (使用 --force 覆盖)");
            c.skipped++;
            return;
        }
        try {
            Files.write(target, config.source(properties.getBasePackage())
                    .getBytes(StandardCharsets.UTF_8));
            info("已发布(配置): " + target);
            c.published++;
        } catch (IOException e) {
            error("写入失败: " + target + " (" + e.getMessage() + ")");
            c.failed++;
        }
    }

    private void publishStatic(PublishableStatic item, boolean force, Counters c) {
        Path resourcesRoot = resolveResourcesRoot();
        ClassLoader loader = item.resourceClassLoader();

        for (Map.Entry<String, String> res : item.resources().entrySet()) {
            String source = normalizeClasspath(res.getKey());
            Path target = resourcesRoot.resolve(res.getValue()).normalize();

            // 防目录穿越：目标必须落在 resources 根目录内
            if (!target.startsWith(resourcesRoot)) {
                error("非法的目标路径（越出 resources 根目录）: " + res.getValue());
                c.failed++;
                continue;
            }

            if (Files.exists(target) && !force) {
                warn("已存在，跳过: " + target + "  (使用 --force 覆盖)");
                c.skipped++;
                continue;
            }

            byte[] content = readClasspath(loader, source);
            if (content == null) {
                error("找不到 classpath 资源: " + source);
                c.failed++;
                continue;
            }

            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(target, content);
                info("已发布(资源): " + target + "  (" + content.length + " 字节)");
                c.published++;
            } catch (IOException e) {
                error("写入失败: " + target + " (" + e.getMessage() + ")");
                c.failed++;
            }
        }
    }

    /**
     * 发布模块内置迁移文件（{@link PublishableMigration}）到业务工程迁移源代码目录：
     * 从模块 jar 读取 Java 源文件 → 把 {@code package} 声明重写为工程迁移包
     * （{@code MakeCodeProperties#getMigrationPackage}，与目标目录
     * {@code getMigrationSourceDir()} 严格对应，保证发布后直接可编译）→ 落盘。
     */
    private void publishMigration(PublishableMigration item, boolean force, Counters c) {
        Path migrationDir = resolveMigrationDir();
        try {
            Files.createDirectories(migrationDir);
        } catch (IOException e) {
            error("无法创建迁移目录: " + migrationDir + " (" + e.getMessage() + ")");
            c.failed++;
            return;
        }

        ClassLoader loader = item.sourceClassLoader();
        for (Map.Entry<String, String> file : item.migrationFiles()) {
            String source = normalizeClasspath(file.getKey());
            String fileName = file.getValue();
            Path target = migrationDir.resolve(fileName).normalize();

            // 防目录穿越：目标必须落在迁移目录内
            if (!target.startsWith(migrationDir)) {
                error("非法的目标路径（越出迁移目录）: " + fileName);
                c.failed++;
                continue;
            }
            if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
                error("非法的迁移文件名（必须是不含目录的路径）: " + fileName);
                c.failed++;
                continue;
            }

            if (Files.exists(target) && !force) {
                warn("已存在，跳过: " + target + "  (使用 --force 覆盖)");
                c.skipped++;
                continue;
            }

            byte[] content = readClasspath(loader, source);
            if (content == null) {
                error("找不到模块内置迁移资源: " + source);
                c.failed++;
                continue;
            }

            String text = rewritePackage(new String(content, StandardCharsets.UTF_8),
                    properties.getMigrationPackage());

            try {
                Files.write(target, text.getBytes(StandardCharsets.UTF_8));
                info("已发布(迁移): " + target);
                c.published++;
            } catch (IOException e) {
                error("写入失败: " + target + " (" + e.getMessage() + ")");
                c.failed++;
            }
        }
    }

    /**
     * 把迁移源码的第一个 {@code package ...;} 声明重写为工程迁移包，
     * 使其落在 {@code getMigrationSourceDir()} 目录后可直接参与编译。
     * 若源文件没有 package 声明（不应发生），保持不变并在结果中提示。
     */
    private static String rewritePackage(String source, String newPackage) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].strip().startsWith("package ")) {
                continue;
            }
            String before = lines[i].substring(0, lines[i].indexOf('p'));
            String tail = lines[i].substring(lines[i].indexOf(';') + 1); // 同行尾注释（少见），原样保留
            String newLine = before + "package " + newPackage + ";" + tail;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                sb.append(lines[j]).append('\n');
            }
            sb.append(newLine);
            for (int j = i + 1; j < lines.length; j++) {
                sb.append('\n').append(lines[j]);
            }
            return sb.toString();
        }
        // 未找到 package 声明（不应发生）：原样输出
        return source;
    }

    /**
     * 读取 classpath 资源为字节数组。
     */
    private byte[] readClasspath(ClassLoader loader, String path) {
        ClassLoader cl = loader != null ? loader : Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = VendorPublishCommand.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private String normalizeClasspath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * 计算配置类输出目录：{@code <outputDir>/<基础包>/config}。
     */
    private Path resolveConfigDir() {
        String packagePath = properties.getBasePackage().replace('.', '/');
        return Paths.get(properties.getOutputDir(), packagePath, "config")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 计算静态资源输出根目录：{@code <resourcesDir>}。
     */
    private Path resolveResourcesRoot() {
        return Paths.get(properties.getResourcesDir())
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 计算迁移文件输出目录：{@code MakeCodeProperties#getMigrationSourceDir}
     *（默认 {@code src/main/java/<basePackage 路径>/database/migrations}），
     * 与 {@code MakeCodeProperties#getMigrationPackage()} 一一对应。
     */
    private Path resolveMigrationDir() {
        return Paths.get(properties.getMigrationSourceDir())
                .toAbsolutePath()
                .normalize();
    }
}
