package com.weacsoft.jaravel.vendor.artisan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Artisan 命令管理器，对齐 Laravel {@code Illuminate\Console\Application}。
 * <p>
 * 维护命令注册表，解析命令签名（参数/选项），调度命令执行。
 * 支持通过 Spring 容器自动发现 {@link ArtisanCommand} bean，也支持手动注册。
 *
 * <h3>命令签名解析</h3>
 * 对齐 Laravel signature 语法：
 * <pre>
 * "user:score:cacheScore"                    -> 命令名: user:score:cacheScore
 * "migrate {--force}"                        -> 命令名: migrate, 选项: force(布尔)
 * "user:score:cacheOne {studentId}"          -> 命令名: user:score:cacheOne, 参数: studentId
 * "user:score:cacheOne {studentId} {--sync}" -> 混合
 * </pre>
 *
 * <h3>命令行参数解析</h3>
 * <ul>
 *   <li>位置参数：直接传递，如 {@code artisan user:score:cacheOne 20210001}</li>
 *   <li>长选项：{@code --force} / {@code --sync=true}</li>
 *   <li>短选项：{@code -f}（单字母，映射到 force）</li>
 * </ul>
 */
public class ArtisanApplication {

    private static final Logger logger = LoggerFactory.getLogger(ArtisanApplication.class);

    /** 命令名 -> 命令实例，进程级共享 */
    private final Map<String, ArtisanCommand> commands = new ConcurrentHashMap<>();

    /** Spring 应用上下文，用于自动发现命令 bean */
    private final ApplicationContext applicationContext;

    /** 是否已从 Spring 容器扫描命令 */
    private volatile boolean scanned = false;

    /** 签名参数解析正则：{name} 或 {name?} 或 {--option} */
    private static final Pattern ARG_PATTERN = Pattern.compile("\\{(\\w+)(\\?)?\\}");
    private static final Pattern OPT_PATTERN = Pattern.compile("\\{--(\\w+)(?:=(\\S+))?\\}");

    public ArtisanApplication(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 手动注册命令。
     *
     * @param command 命令实例
     */
    public void register(ArtisanCommand command) {
        String name = command.commandName();
        commands.put(name, command);
        logger.debug("[artisan] 注册命令: {} -> {}", name, command.getClass().getName());
    }

    /**
     * 从 Spring 容器自动发现所有 {@link ArtisanCommand} bean 并注册。
     */
    public synchronized void scanCommands() {
        if (scanned || applicationContext == null) {
            return;
        }
        scanned = true;
        Map<String, ArtisanCommand> beans = applicationContext.getBeansOfType(ArtisanCommand.class);
        for (ArtisanCommand cmd : beans.values()) {
            register(cmd);
        }
        logger.info("[artisan] 从 Spring 容器扫描到 {} 个命令", beans.size());
    }

    /**
     * 获取所有已注册命令。
     *
     * @return 命令名 -> 命令实例
     */
    public Map<String, ArtisanCommand> all() {
        scanCommands();
        return new LinkedHashMap<>(commands);
    }

    /**
     * 调度命令执行。
     *
     * @param commandName 命令名
     * @param args        命令行参数（不含命令名本身）
     * @return 退出码，0 表示成功
     */
    public int call(String commandName, String[] args) {
        scanCommands();
        ArtisanCommand command = commands.get(commandName);
        if (command == null) {
            error("命令不存在: " + commandName);
            error("可用命令: " + String.join(", ", commands.keySet()));
            return 1;
        }

        // 解析签名，获取参数和选项定义
        ParsedSignature sig = parseSignature(command.signature());

        // 解析命令行参数
        Map<String, String> parsedArgs = new LinkedHashMap<>();
        Map<String, String> parsedOpts = new LinkedHashMap<>();

        int posIndex = 0;
        List<String> positionalDefs = sig.positionalArgs;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                // 长选项
                String optName = arg.substring(2);
                String optValue = "true";
                if (optName.contains("=")) {
                    int eq = optName.indexOf('=');
                    optValue = optName.substring(eq + 1);
                    optName = optName.substring(0, eq);
                }
                parsedOpts.put(optName, optValue);
            } else if (arg.startsWith("-") && arg.length() == 2) {
                // 短选项，映射到长选项（取首字母匹配）
                String shortName = arg.substring(1);
                for (String opt : sig.options) {
                    if (opt.startsWith(shortName)) {
                        parsedOpts.put(opt, "true");
                        break;
                    }
                }
            } else {
                // 位置参数
                if (posIndex < positionalDefs.size()) {
                    parsedArgs.put(positionalDefs.get(posIndex), arg);
                    posIndex++;
                }
            }
        }

        // 设置默认选项值
        for (Map.Entry<String, String> entry : sig.optionDefaults.entrySet()) {
            if (!parsedOpts.containsKey(entry.getKey())) {
                parsedOpts.put(entry.getKey(), entry.getValue());
            }
        }

        // 注入参数到命令
        command.setParsed(parsedArgs, parsedOpts);

        // 执行
        try {
            logger.info("[artisan] 执行命令: {} (args={}, opts={})", commandName, parsedArgs, parsedOpts);
            return command.handle();
        } catch (Exception e) {
            logger.error("[artisan] 命令执行异常: {} - {}", commandName, e.getMessage(), e);
            error("命令执行异常: " + e.getMessage());
            return 1;
        }
    }

    /**
     * 列出所有命令及其描述。
     */
    public void listCommands() {
        scanCommands();
        info("");
        info("可用命令:");
        info("");
        for (Map.Entry<String, ArtisanCommand> entry : commands.entrySet()) {
            String name = entry.getKey();
            String desc = entry.getValue().description();
            info(String.format("  %-40s %s", name, desc));
        }
        info("");
    }

    /** 解析命令签名 */
    private ParsedSignature parseSignature(String signature) {
        ParsedSignature sig = new ParsedSignature();
        Matcher argMatcher = ARG_PATTERN.matcher(signature);
        Matcher optMatcher = OPT_PATTERN.matcher(signature);

        while (argMatcher.find()) {
            String name = argMatcher.group(1);
            sig.positionalArgs.add(name);
        }

        while (optMatcher.find()) {
            String name = optMatcher.group(1);
            String defaultValue = optMatcher.group(2);
            sig.options.add(name);
            if (defaultValue != null) {
                sig.optionDefaults.put(name, defaultValue);
            }
        }

        return sig;
    }

    private void info(String msg) {
        System.out.println(msg);
    }

    private void error(String msg) {
        System.err.println(msg);
    }

    /** 解析后的签名 */
    private static class ParsedSignature {
        List<String> positionalArgs = new ArrayList<>();
        List<String> options = new ArrayList<>();
        Map<String, String> optionDefaults = new LinkedHashMap<>();
    }
}
