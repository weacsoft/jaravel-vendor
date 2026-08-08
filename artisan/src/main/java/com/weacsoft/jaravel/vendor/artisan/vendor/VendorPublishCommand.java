package com.weacsoft.jaravel.vendor.artisan.vendor;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.core.publish.Publishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
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
 * <b>统一扫描</b>容器中所有 {@link Publishable}（{@link PublishableConfig} 配置类源码 +
 * {@link PublishableStatic} 静态前端资源），一次扫描、按需发布，不再区分两条命令。
 *
 * <h3>用法</h3>
 * <pre>
 * artisan vendor:publish                       # 列出所有可发布项（配置 + 资源）
 * artisan vendor:publish --all                 # 发布全部（配置类 + 静态资源）
 * artisan vendor:publish --tag=cache           # 只发布 cache 模块（其配置与资源）
 * artisan vendor:publish --tag=resources       # 只发布全部静态前端资源
 * artisan vendor:publish --tag=config          # 只发布全部 Java 配置类
 * artisan vendor:publish --tag=captcha --force # 覆盖已存在文件
 * </pre>
 *
 * <h3>可选依赖说明</h3>
 * 本命令通过构造器接收 {@code List<Publishable>}，由 Spring 收集。
 * 若工程未引入任何声明了可发布项的模块，列表为空，命令只提示无可发布项，不会报错。
 */
public class VendorPublishCommand extends ArtisanCommand {

    private final List<Publishable> publishables;
    private final MakeCodeProperties properties;

    public VendorPublishCommand(List<Publishable> publishables, MakeCodeProperties properties) {
        this.publishables = publishables == null ? new ArrayList<>() : publishables;
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
        if (publishables.isEmpty()) {
            warn("没有任何可发布项。");
            info("请确认已引入对应 jaravel 模块（如 jaravel-cache / jaravel-wire）。");
            return 0;
        }

        if (hasOption("list")) {
            printList();
            return 0;
        }

        List<Publishable> targets = resolveTargets();
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
    private List<Publishable> resolveTargets() {
        if (hasOption("all")) {
            return new ArrayList<>(publishables);
        }

        String tag = option("tag");
        if (tag != null && !tag.isEmpty() && !"true".equals(tag)) {
            if ("resources".equals(tag)) {
                return filterByType(PublishType.RESOURCE);
            }
            if ("config".equals(tag)) {
                return filterByType(PublishType.CONFIG);
            }
            List<Publishable> matched = new ArrayList<>();
            for (Publishable p : publishables) {
                if (tag.equals(p.tag())) {
                    matched.add(p);
                }
            }
            if (matched.isEmpty()) {
                error("未知的 tag: " + tag);
                info("可用的 tag: " + String.join(", ", tags())
                        + "（保留标签: resources=全部静态资源, config=全部配置类）");
                return null;
            }
            return matched;
        }

        // 未指定任何选项时，展示清单并提示用法，避免误覆盖用户文件
        printList();
        info("");
        info("请使用 --all 发布全部；--tag=<标签> 发布指定模块；"
                + "--tag=resources 发布静态资源；--tag=config 发布配置类。");
        return new ArrayList<>();
    }

    private List<Publishable> filterByType(PublishType type) {
        List<Publishable> r = new ArrayList<>();
        for (Publishable p : publishables) {
            if (p.type() == type) {
                r.add(p);
            }
        }
        return r;
    }

    private List<String> tags() {
        List<String> t = new ArrayList<>();
        for (Publishable p : publishables) {
            if (!t.contains(p.tag())) {
                t.add(p.tag());
            }
        }
        return t;
    }

    private void printList() {
        Map<String, List<Publishable>> grouped = new LinkedHashMap<>();
        for (Publishable p : publishables) {
            grouped.computeIfAbsent(p.tag(), k -> new ArrayList<>()).add(p);
        }

        info("可发布项（标签 / 类型 / 描述）:");
        for (Map.Entry<String, List<Publishable>> entry : grouped.entrySet()) {
            info("  [" + entry.getKey() + "]");
            for (Publishable p : entry.getValue()) {
                String type = p.type() == PublishType.RESOURCE ? "resource" : "config";
                String name = p instanceof PublishableConfig c
                        ? c.className()
                        : (p instanceof PublishableStatic s ? String.join(", ", s.resources().values()) : p.tag());
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
}
