package com.weacsoft.jaravel.vendor.artisan.vendor;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

import java.io.IOException;
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
 * 把各模块声明的 {@link PublishableConfig} 发布为 Java 配置类源码，
 * 输出到业务工程 {@code src/main/java/<基础包>/config/} 目录下
 * （与 {@code app/} 包同级）。
 *
 * <h3>用法</h3>
 * <pre>
 * artisan vendor:publish                       # 列出可发布项并提示用法
 * artisan vendor:publish --list                # 仅列出可发布项
 * artisan vendor:publish --all                 # 发布全部配置
 * artisan vendor:publish --tag=cache           # 只发布 cache 模块的配置
 * artisan vendor:publish --tag=cache --force   # 覆盖已存在的文件
 * </pre>
 *
 * <h3>可选依赖说明</h3>
 * 本命令通过构造器接收 {@code List<PublishableConfig>}，由 Spring 收集。
 * 若工程未引入任何声明了可发布配置的模块，列表为空，命令只提示无可发布项，
 * 不会报错。
 */
public class VendorPublishCommand extends ArtisanCommand {

    private final List<PublishableConfig> publishables;
    private final MakeCodeProperties properties;

    public VendorPublishCommand(List<PublishableConfig> publishables, MakeCodeProperties properties) {
        this.publishables = publishables == null ? new ArrayList<>() : publishables;
        this.properties = properties;
    }

    @Override
    public String signature() {
        return "vendor:publish {--tag} {--all} {--force} {--list}";
    }

    @Override
    public String description() {
        return "发布模块配置类到业务工程 config/ 包下";
    }

    @Override
    public int handle() {
        if (publishables.isEmpty()) {
            warn("没有任何可发布的配置。");
            info("请确认已引入 jaravel-cache / jaravel-storage 等模块。");
            return 0;
        }

        Map<String, List<PublishableConfig>> grouped = groupByTag();

        if (hasOption("list")) {
            printList(grouped);
            return 0;
        }

        List<PublishableConfig> targets = resolveTargets(grouped);
        if (targets == null) {
            return 1;
        }
        if (targets.isEmpty()) {
            return 0;
        }

        return publishAll(targets, hasOption("force"));
    }

    /**
     * 按 tag 分组，保持模块声明顺序。
     */
    private Map<String, List<PublishableConfig>> groupByTag() {
        Map<String, List<PublishableConfig>> grouped = new LinkedHashMap<>();
        for (PublishableConfig config : publishables) {
            grouped.computeIfAbsent(config.tag(), k -> new ArrayList<>()).add(config);
        }
        return grouped;
    }

    /**
     * 解析本次要发布的目标集合。
     *
     * @return null 表示参数非法（调用方返回失败码）；空列表表示无需写文件
     */
    private List<PublishableConfig> resolveTargets(Map<String, List<PublishableConfig>> grouped) {
        if (hasOption("all")) {
            return new ArrayList<>(publishables);
        }

        String tag = option("tag");
        if (tag != null && !tag.isEmpty() && !"true".equals(tag)) {
            List<PublishableConfig> matched = grouped.get(tag);
            if (matched == null) {
                error("未知的 tag: " + tag);
                info("可用的 tag: " + String.join(", ", grouped.keySet()));
                return null;
            }
            return matched;
        }

        // 未指定任何选项时，展示清单并提示用法，避免误覆盖用户文件
        printList(grouped);
        info("");
        info("请使用 --all 发布全部，或 --tag=<标签> 发布指定模块。");
        return new ArrayList<>();
    }

    private void printList(Map<String, List<PublishableConfig>> grouped) {
        info("可发布的配置类:");
        for (Map.Entry<String, List<PublishableConfig>> entry : grouped.entrySet()) {
            info("  [" + entry.getKey() + "]");
            for (PublishableConfig config : entry.getValue()) {
                String desc = config.description();
                info("    - " + config.className() + (desc.isEmpty() ? "" : "  # " + desc));
            }
        }
    }

    private int publishAll(List<PublishableConfig> targets, boolean force) {
        Path configDir = resolveConfigDir();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            error("无法创建配置目录: " + configDir + " (" + e.getMessage() + ")");
            return 1;
        }

        int published = 0;
        int skipped = 0;
        for (PublishableConfig config : targets) {
            Path target = configDir.resolve(config.className() + ".java");
            if (Files.exists(target) && !force) {
                warn("已存在，跳过: " + target + "  (使用 --force 覆盖)");
                skipped++;
                continue;
            }
            try {
                Files.write(target, config.source(properties.getBasePackage())
                        .getBytes(StandardCharsets.UTF_8));
                info("已发布: " + target);
                published++;
            } catch (IOException e) {
                error("写入失败: " + target + " (" + e.getMessage() + ")");
                return 1;
            }
        }

        info("");
        info("发布完成: 新增 " + published + " 个，跳过 " + skipped + " 个。");
        return 0;
    }

    /**
     * 计算配置类输出目录：{@code <outputDir>/<基础包>/config}。
     * <p>
     * 复用 {@link MakeCodeProperties}，使 {@code vendor:publish} 与
     * {@code make:*} 系列命令共享同一套路径配置，可由业务工程覆盖。
     */
    private Path resolveConfigDir() {
        String packagePath = properties.getBasePackage().replace('.', '/');
        return Paths.get(properties.getOutputDir(), packagePath, "config")
                .toAbsolutePath()
                .normalize();
    }
}
