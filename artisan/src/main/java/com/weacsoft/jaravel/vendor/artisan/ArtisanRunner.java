package com.weacsoft.jaravel.vendor.artisan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Artisan 运行入口，对齐 Laravel {@code php artisan} 命令行入口。
 * <p>
 * 在应用主类中调用 {@link #isArtisanMode(String[])} 检测是否为 artisan 模式，
 * 若是则调用 {@link #run(ArtisanApplication, String[])} 执行命令并返回退出码。
 *
 * <h3>使用方式</h3>
 * <pre>
 * public static void main(String[] args) {
 *     if (ArtisanRunner.isArtisanMode(args)) {
 *         // artisan 模式：不启动 HTTP 服务，仅运行 CLI 命令
 *         SpringApplicationBuilder builder = new SpringApplicationBuilder(Application.class)
 *                 .web(WebApplicationType.NONE);
 *         ConfigurableApplicationContext ctx = builder.run(args);
 *         int exitCode = ArtisanRunner.run(ctx.getBean(ArtisanApplication.class), args);
 *         ctx.close();
 *         System.exit(exitCode);
 *     } else {
 *         // 正常 HTTP 模式
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 *
 * <h3>命令行格式</h3>
 * <pre>
 * java -jar app.jar artisan                          # 列出所有命令
 * java -jar app.jar artisan list                     # 列出所有命令
 * java -jar app.jar artisan user:score:cacheScore    # 执行指定命令
 * java -jar app.jar artisan migrate --force          # 带选项
 * java -jar app.jar artisan user:score:cacheOne 20210001  # 带参数
 * </pre>
 */
public class ArtisanRunner {

    private static final Logger logger = LoggerFactory.getLogger(ArtisanRunner.class);

    /** artisan 模式标识 */
    public static final String ARTISAN_FLAG = "artisan";

    /**
     * 检测是否为 artisan 模式。
     * <p>
     * 当 args 的第一个元素为 {@code "artisan"} 时返回 true。
     *
     * @param args 命令行参数
     * @return 是否为 artisan 模式
     */
    public static boolean isArtisanMode(String[] args) {
        return args != null && args.length > 0 && ARTISAN_FLAG.equals(args[0]);
    }

    /**
     * 运行 artisan 命令。
     * <p>
     * 从 args 中解析命令名和参数，调度到 {@link ArtisanApplication} 执行。
     *
     * @param app  Artisan 应用
     * @param args 命令行参数（args[0] 为 "artisan"）
     * @return 退出码，0 表示成功
     */
    public static int run(ArtisanApplication app, String[] args) {
        if (args.length <= 1) {
            // 无命令名，列出所有命令
            app.listCommands();
            return 0;
        }

        // args[0] = "artisan", args[1] = commandName, args[2:] = command args
        if ("list".equals(args[1]) || "--list".equals(args[1])) {
            app.listCommands();
            return 0;
        }

        String commandName = args[1];
        String[] commandArgs = new String[args.length - 2];
        if (commandArgs.length > 0) {
            System.arraycopy(args, 2, commandArgs, 0, commandArgs.length);
        }

        return app.call(commandName, commandArgs);
    }

    /**
     * 运行 artisan 命令并退出 JVM。
     *
     * @param app  Artisan 应用
     * @param args 命令行参数
     */
    public static void runAndExit(ArtisanApplication app, String[] args) {
        int exitCode = run(app, args);
        logger.info("[artisan] 命令执行完毕，退出码: {}", exitCode);
        System.exit(exitCode);
    }
}
