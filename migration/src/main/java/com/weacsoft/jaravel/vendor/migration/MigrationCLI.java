package com.weacsoft.jaravel.vendor.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 独立命令行入口，使 migration 模块可在无 SpringBoot 环境下直接运行。
 * <p>
 * <b>使用方式</b>：
 * <pre>
 * java -cp migration.jar:mysql-connector.jar com.weacsoft.jaravel.vendor.migration.MigrationCLI \
 *   --db-url=jdbc:mysql://localhost:3306/mydb \
 *   --db-user=root \
 *   --db-password=secret \
 *   --source=DIRECTORY \
 *   --directory=/path/to/migrations \
 *   migrate
 * </pre>
 * <p>
 * <b>支持的参数</b>：
 * <ul>
 *   <li>{@code --db-url=<url>}      数据库 JDBC URL（必填）</li>
 *   <li>{@code --db-user=<user>}    数据库用户名（必填）</li>
 *   <li>{@code --db-password=<pwd>} 数据库密码（默认空）</li>
 *   <li>{@code --source=<type>}     迁移源：DIRECTORY / JAR / CLASSPATH（默认 DIRECTORY）</li>
 *   <li>{@code --directory=<path>}  迁移文件目录（DIRECTORY 模式）</li>
 *   <li>{@code --jar-path=<path>}   迁移 JAR 路径（JAR 模式）</li>
 *   <li>{@code --table=<name>}      迁移记录表名（默认 migrations）</li>
 * </ul>
 * <p>
 * <b>支持的命令</b>（放在参数最后）：
 * <ul>
 *   <li>{@code migrate}         执行迁移</li>
 *   <li>{@code rollback[=N]}    回滚 N 批（默认 1）</li>
 *   <li>{@code reset}           回滚全部</li>
 *   <li>{@code refresh}         回滚全部并重新迁移</li>
 *   <li>{@code status}          查看状态</li>
 * </ul>
 * <p>
 * <b>CI/CD 示例</b>：
 * <pre>
 * # 编译迁移模块（不含 SpringBoot）
 * mvn clean package -pl migration,utils -am
 *
 * # 执行迁移
 * java -jar migration/target/migration-*.jar \
 *   --db-url=jdbc:mysql://prod-db:3306/app \
 *   --db-user=deploy \
 *   --db-password=xxx \
 *   --source=DIRECTORY \
 *   --directory=./migrations \
 *   migrate
 * </pre>
 */
public class MigrationCLI {

    private static final Logger log = LoggerFactory.getLogger(MigrationCLI.class);

    /**
     * 主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            System.exit(1);
        }

        try {
            Map<String, String> opts = parseOptions(args);
            List<String> commands = extractCommands(args);

            // 校验必填参数
            String dbUrl = opts.get("db-url");
            String dbUser = opts.get("db-user");
            if (dbUrl == null || dbUrl.isEmpty()) {
                System.err.println("错误：缺少必填参数 --db-url");
                printUsage();
                System.exit(1);
            }
            if (dbUser == null) {
                dbUser = "";
            }
            String dbPassword = opts.getOrDefault("db-password", "");

            // 构建 MigrationProperties
            MigrationProperties properties = new MigrationProperties();
            properties.setEnabled(true);
            properties.setTable(opts.getOrDefault("table", "migrations"));

            String sourceStr = opts.getOrDefault("source", "DIRECTORY").toUpperCase();
            properties.setSource(MigrationSource.valueOf(sourceStr));
            properties.setDirectory(opts.getOrDefault("directory", "migrations"));
            properties.setJarPath(opts.getOrDefault("jar-path", ""));
            properties.setAutoRun(false);

            // 创建 SimpleDataSource（轻量级，不依赖连接池）
            DataSource dataSource = new SimpleDataSource(dbUrl, dbUser, dbPassword);

            // 执行迁移
            MigrationExecutor executor = new MigrationExecutor(dataSource, properties);
            executor.execute(commands.toArray(new String[0]));

            log.info("[migration-cli] 迁移命令执行完成");
        } catch (Exception e) {
            log.error("[migration-cli] 迁移命令执行失败", e);
            System.exit(1);
        }
    }

    /**
     * 解析 --key=value 格式的选项。
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                String key = arg.substring(2, eq);
                String value = arg.substring(eq + 1);
                opts.put(key, value);
            }
        }
        return opts;
    }

    /**
     * 提取命令参数（非 -- 开头的参数）。
     */
    private static List<String> extractCommands(String[] args) {
        List<String> commands = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                commands.add(arg);
            }
        }
        return commands;
    }

    /**
     * 打印用法。
     */
    private static void printUsage() {
        System.out.println("用法: java -cp <classpath> MigrationCLI --db-url=<url> --db-user=<user> [options] <command>");
        System.out.println();
        System.out.println("必填参数:");
        System.out.println("  --db-url=<url>       数据库 JDBC URL");
        System.out.println("  --db-user=<user>     数据库用户名");
        System.out.println();
        System.out.println("可选参数:");
        System.out.println("  --db-password=<pwd>  数据库密码（默认空）");
        System.out.println("  --source=<type>      迁移源：DIRECTORY / JAR / CLASSPATH（默认 DIRECTORY）");
        System.out.println("  --directory=<path>   迁移文件目录（DIRECTORY 模式）");
        System.out.println("  --jar-path=<path>    迁移 JAR 路径（JAR 模式）");
        System.out.println("  --table=<name>       迁移记录表名（默认 migrations）");
        System.out.println();
        System.out.println("命令:");
        System.out.println("  migrate              执行迁移");
        System.out.println("  rollback[=N]         回滚 N 批（默认 1）");
        System.out.println("  reset                回滚全部");
        System.out.println("  refresh              回滚全部并重新迁移");
        System.out.println("  status               查看状态");
    }

    /**
     * 轻量级 DataSource 实现，直接使用 DriverManager 获取连接。
     * 不依赖任何连接池，适用于 CLI 一次性执行场景。
     */
    static class SimpleDataSource implements DataSource {

        private final String url;
        private final String user;
        private final String password;

        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger("migration");
        }
    }
}
