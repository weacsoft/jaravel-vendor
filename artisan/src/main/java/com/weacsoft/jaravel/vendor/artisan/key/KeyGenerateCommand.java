package com.weacsoft.jaravel.vendor.artisan.key;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.make.MakeCodeProperties;
import com.weacsoft.jaravel.vendor.core.crypto.DefaultAppKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code key:generate} 命令，对齐 Laravel 的 {@code php artisan key:generate}。
 * <p>
 * 生成一把 Base64 编码的随机应用密钥，并写入 application 配置的 {@code jaravel.key}。
 * 该密钥是框架的全局主密钥：captcha / jwt / http-cookies 等模块在未单独配置
 * 专用密钥时，统一回退到它（见
 * {@link com.weacsoft.jaravel.vendor.core.crypto.AppKey#resolve(String, String)}）。
 *
 * <h3>用法</h3>
 * <pre>
 * artisan key:generate                 # 生成并写入 application.yml / application.properties
 * artisan key:generate --show          # 仅打印，不修改任何文件
 * artisan key:generate --length=64     # 生成 64 字节（512-bit）密钥
 * artisan key:generate --file=src/main/resources/application-prod.yml
 * artisan key:generate --force         # 已存在 jaravel.key 时强制覆盖
 * </pre>
 *
 * <h3>写入规则</h3>
 * <ul>
 *   <li>已存在 {@code jaravel.key} 行 → 就地替换（需 {@code --force}，否则提示并退出）</li>
 *   <li>存在 {@code jaravel:} 根节点（YAML）→ 作为其首个子键插入</li>
 *   <li>都不存在 → 追加一个新的 {@code jaravel:} 块 / {@code jaravel.key=} 行</li>
 * </ul>
 */
public class KeyGenerateCommand extends ArtisanCommand {

    /** 候选配置文件名（按优先级顺序探测） */
    private static final String[] CANDIDATES = {
            "application.yml", "application.yaml", "application.properties"
    };

    private final MakeCodeProperties properties;

    public KeyGenerateCommand(MakeCodeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String signature() {
        return "key:generate {--length=32} {--show} {--force} {--file=}";
    }

    @Override
    public String description() {
        return "生成 Base64 应用密钥并写入 application 配置的 jaravel.key";
    }

    @Override
    public int handle() {
        int length = parseLength(option("length", "32"));
        String key = DefaultAppKey.generateRandomKey(length);

        if (hasOption("show")) {
            info("");
            info("  jaravel.key = " + key);
            info("");
            info("  （--show 模式，未修改任何文件；请自行写入 application 配置）");
            return 0;
        }

        Path target = resolveTargetFile(option("file"));
        if (target == null) {
            warn("未找到 application 配置文件（已探测 " + properties.getResourcesDir() + "/{application.yml,application.yaml,application.properties}）。");
            info("");
            info("  jaravel.key = " + key);
            info("");
            info("  请手动写入你的 application 配置。");
            return 0;
        }

        try {
            WriteResult result = writeKey(target, key, hasOption("force"));
            switch (result) {
                case SKIPPED_EXISTS:
                    warn("配置文件 " + target + " 中已存在 jaravel.key，未做修改。");
                    info("  如需覆盖请加 --force；仅查看新密钥请加 --show。");
                    return 1;
                case REPLACED:
                    info("[key:generate] 已覆盖 " + target + " 中的 jaravel.key");
                    break;
                case INSERTED:
                    info("[key:generate] 已写入 " + target + " 的 jaravel.key");
                    break;
                default:
                    break;
            }
            info("  jaravel.key = " + key);
            return 0;
        } catch (IOException e) {
            error("写入配置文件失败：" + e.getMessage());
            info("");
            info("  jaravel.key = " + key);
            return 1;
        }
    }

    // ==================== 内部实现 ====================

    /** 写入结果 */
    private enum WriteResult { INSERTED, REPLACED, SKIPPED_EXISTS }

    private int parseLength(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(16, Math.min(v, 256));
        } catch (NumberFormatException e) {
            warn("--length 不是合法数字（" + raw + "），已回退为 32。");
            return 32;
        }
    }

    /**
     * 解析目标配置文件：显式 {@code --file} 优先，否则在 resources 目录下按优先级探测。
     *
     * @param explicit --file 指定的路径，可能为 null
     * @return 目标文件路径；找不到返回 null
     */
    private Path resolveTargetFile(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit.trim());
        }
        for (String name : CANDIDATES) {
            Path p = Paths.get(properties.getResourcesDir(), name);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 将密钥写入配置文件。
     *
     * @param file  目标文件
     * @param key   Base64 密钥
     * @param force 已存在时是否覆盖
     * @return 写入结果
     * @throws IOException 读写失败
     */
    private WriteResult writeKey(Path file, String key, boolean force) throws IOException {
        boolean yaml = file.getFileName().toString().endsWith(".yml")
                || file.getFileName().toString().endsWith(".yaml");
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));

        return yaml ? writeYaml(file, lines, key, force) : writeProperties(file, lines, key, force);
    }

    private WriteResult writeProperties(Path file, List<String> lines, String key, boolean force) throws IOException {
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("jaravel.key") && t.contains("=")) {
                if (!force) {
                    return WriteResult.SKIPPED_EXISTS;
                }
                lines.set(i, "jaravel.key=" + key);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return WriteResult.REPLACED;
            }
        }
        lines.add("");
        lines.add("# Jaravel 全局应用密钥（由 artisan key:generate 生成）");
        lines.add("jaravel.key=" + key);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return WriteResult.INSERTED;
    }

    private WriteResult writeYaml(Path file, List<String> lines, String key, boolean force) throws IOException {
        int jaravelRoot = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            // 已存在 jaravel.key（扁平写法）
            if (trimmed.startsWith("jaravel.key:")) {
                if (!force) {
                    return WriteResult.SKIPPED_EXISTS;
                }
                lines.set(i, "jaravel.key: " + key);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return WriteResult.REPLACED;
            }
            // jaravel: 根节点
            if (jaravelRoot < 0 && line.startsWith("jaravel:")) {
                jaravelRoot = i;
                continue;
            }
            // 根节点下的 key:（缩进 2 空格且父节点为 jaravel）
            if (jaravelRoot >= 0 && indentOf(line) == 2 && trimmed.startsWith("key:")) {
                if (!force) {
                    return WriteResult.SKIPPED_EXISTS;
                }
                lines.set(i, "  key: " + key);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return WriteResult.REPLACED;
            }
            // 遇到新的顶层节点，说明已越过 jaravel 块
            if (jaravelRoot >= 0 && !line.isBlank() && indentOf(line) == 0 && !line.startsWith("jaravel:")) {
                break;
            }
        }

        if (jaravelRoot >= 0) {
            lines.add(jaravelRoot + 1, "  # 全局应用密钥（由 artisan key:generate 生成）");
            lines.add(jaravelRoot + 2, "  key: " + key);
        } else {
            lines.add("");
            lines.add("jaravel:");
            lines.add("  # 全局应用密钥（由 artisan key:generate 生成）");
            lines.add("  key: " + key);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return WriteResult.INSERTED;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }
}
