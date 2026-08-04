package com.weacsoft.jaravel.vendor.artisan.vendor;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.core.publish.PublishableStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code vendor:publish:static} 命令：发布模块自带的静态前端资源到业务工程
 * {@code src/main/resources/static/} 目录。
 * <p>
 * 与 {@link VendorPublishCommand} 完全隔离：
 * <ul>
 *   <li>{@code vendor:publish} 只处理 {@code PublishableConfig}（Java 配置类源码），
 *       <b>不会</b>发布任何静态资源；</li>
 *   <li>{@code vendor:publish:static} 只处理 {@link PublishableStatic}（js / css / html），
 *       <b>不会</b>生成任何 Java 源码。</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>
 * artisan vendor:publish:static                     # 列出可发布资源并提示用法
 * artisan vendor:publish:static --list              # 仅列出可发布资源
 * artisan vendor:publish:static --all               # 发布全部静态资源
 * artisan vendor:publish:static --tag=captcha       # 只发布 captcha 模块的静态资源
 * artisan vendor:publish:static --tag=captcha --force  # 覆盖已存在的文件
 * </pre>
 *
 * <h3>可选依赖说明</h3>
 * 本命令通过构造器接收 {@code List<PublishableStatic>}，由 Spring 收集。
 * 若工程未引入任何声明了静态资源的模块，列表为空，命令只提示无可发布项，不会报错。
 */
public class VendorPublishStaticCommand extends ArtisanCommand {

    private final List<PublishableStatic> publishables;
    private final MakeCodeProperties properties;

    public VendorPublishStaticCommand(List<PublishableStatic> publishables, MakeCodeProperties properties) {
        this.publishables = publishables == null ? new ArrayList<>() : publishables;
        this.properties = properties;
    }

    @Override
    public String signature() {
        return "vendor:publish:static {--tag} {--all} {--force} {--list}";
    }

    @Override
    public String description() {
        return "发布模块自带的静态前端资源到 resources/static 目录";
    }

    @Override
    public int handle() {
        if (publishables.isEmpty()) {
            warn("没有任何可发布的静态资源。");
            info("请确认已引入 jaravel-captcha / jaravel-wire 等自带前端资源的模块。");
            return 0;
        }

        Map<String, List<PublishableStatic>> grouped = groupByTag();

        if (hasOption("list")) {
            printList(grouped);
            return 0;
        }

        List<PublishableStatic> targets = resolveTargets(grouped);
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
    private Map<String, List<PublishableStatic>> groupByTag() {
        Map<String, List<PublishableStatic>> grouped = new LinkedHashMap<>();
        for (PublishableStatic item : publishables) {
            grouped.computeIfAbsent(item.tag(), k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * 解析本次要发布的目标集合。
     *
     * @return null 表示参数非法（调用方返回失败码）；空列表表示无需写文件
     */
    private List<PublishableStatic> resolveTargets(Map<String, List<PublishableStatic>> grouped) {
        if (hasOption("all")) {
            return new ArrayList<>(publishables);
        }

        String tag = option("tag");
        if (tag != null && !tag.isEmpty() && !"true".equals(tag)) {
            List<PublishableStatic> matched = grouped.get(tag);
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

    private void printList(Map<String, List<PublishableStatic>> grouped) {
        info("可发布的静态资源:");
        for (Map.Entry<String, List<PublishableStatic>> entry : grouped.entrySet()) {
            info("  [" + entry.getKey() + "]");
            for (PublishableStatic item : entry.getValue()) {
                String desc = item.description();
                if (!desc.isEmpty()) {
                    info("    # " + desc);
                }
                for (Map.Entry<String, String> res : item.resources().entrySet()) {
                    info("    - " + res.getValue() + "  <= classpath:" + res.getKey());
                }
            }
        }
    }

    private int publishAll(List<PublishableStatic> targets, boolean force) {
        Path resourcesRoot = resolveResourcesRoot();

        int published = 0;
        int skipped = 0;
        for (PublishableStatic item : targets) {
            ClassLoader loader = item.resourceClassLoader();
            for (Map.Entry<String, String> res : item.resources().entrySet()) {
                String source = normalizeClasspath(res.getKey());
                Path target = resourcesRoot.resolve(res.getValue()).normalize();

                // 防目录穿越：目标必须落在 resources 根目录内
                if (!target.startsWith(resourcesRoot)) {
                    error("非法的目标路径（越出 resources 根目录）: " + res.getValue());
                    return 1;
                }

                if (Files.exists(target) && !force) {
                    warn("已存在，跳过: " + target + "  (使用 --force 覆盖)");
                    skipped++;
                    continue;
                }

                byte[] content = readClasspath(loader, source);
                if (content == null) {
                    error("找不到 classpath 资源: " + source);
                    return 1;
                }

                try {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(target, content);
                    info("已发布: " + target + "  (" + content.length + " 字节)");
                    published++;
                } catch (IOException e) {
                    error("写入失败: " + target + " (" + e.getMessage() + ")");
                    return 1;
                }
            }
        }

        info("");
        info("静态资源发布完成: 新增 " + published + " 个，跳过 " + skipped + " 个。");
        return 0;
    }

    /**
     * 读取 classpath 资源为字节数组。
     *
     * @param loader 类加载器
     * @param path   资源路径（不以 / 开头）
     * @return 资源内容，找不到返回 {@code null}
     */
    private byte[] readClasspath(ClassLoader loader, String path) {
        ClassLoader cl = loader != null ? loader : Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = VendorPublishStaticCommand.class.getClassLoader();
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
     * 计算静态资源输出根目录：{@code <resourcesDir>}。
     */
    private Path resolveResourcesRoot() {
        return Paths.get(properties.getResourcesDir())
                .toAbsolutePath()
                .normalize();
    }
}
