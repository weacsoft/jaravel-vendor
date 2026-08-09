package com.weacsoft.jaravel.vendor.database.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * database 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=database} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/DatabaseConfig.java}，内含：
 * <ul>
 *   <li>{@code containerBootstrap()} —— 创建并初始化<b>全局唯一</b>的 gaarason
 *       {@code ContainerBootstrap}（SpringBoot 环境下每个 {@code GaarasonDataSource} 必须携带它），
 *       并存入框架的 {@code ConnectionManager} 门面；</li>
 *   <li>{@code @RegisterConnection} 示例方法 —— 用<b>别名 + 注解</b>注册连接，
 *       与 auth 模块的 {@code @RegisterGuard} 完全一致的机制，产物不进 Spring 容器。</li>
 * </ul>
 * 对齐 Laravel {@code config/database.php}。
 */
public class DatabasePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "database";
    }

    @Override
    public String className() {
        return "DatabaseConfig";
    }

    @Override
    public String description() {
        return "数据库连接注册（gaarason Container + 多连接别名）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.alibaba.druid.pool.DruidDataSource;\n"
                + "import com.weacsoft.jaravel.vendor.core.SpringContext;\n"
                + "import com.weacsoft.jaravel.vendor.database.ConnectionManager;\n"
                + "import com.weacsoft.jaravel.vendor.database.RegisterConnection;\n"
                + "import gaarason.database.bootstrap.ContainerBootstrap;\n"
                + "import gaarason.database.connection.GaarasonDataSourceBuilder;\n"
                + "import gaarason.database.contract.connection.GaarasonDataSource;\n"
                + "import gaarason.database.provider.ModelInstanceProvider;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "import org.springframework.core.env.Environment;\n"
                + "\n"
                + "import javax.sql.DataSource;\n"
                + "\n"
                + "/**\n"
                + " * 数据库配置，对齐 Laravel config/database.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=database} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>ContainerBootstrap（务必只有一个）</h3>\n"
                + " * gaarason 在 SpringBoot 环境下，每个 {@code GaarasonDataSource} 都<b>必须</b>携带\n"
                + " * {@code ContainerBootstrap}，且所有数据源必须<b>共用同一个实例</b>，\n"
                + " * 否则 Model 注册表、类型转换器会分裂到不同容器，导致查询报错。\n"
                + " * 因此下方所有连接方法都注入同一个 {@code bootstrap} 参数，切勿在别处再次\n"
                + " * {@code ContainerBootstrap.build()}。\n"
                + " *\n"
                + " * <h3>为什么用 &#64;RegisterConnection 而不是 &#64;Bean</h3>\n"
                + " * 与 auth 的 {@code @RegisterGuard} 一致：连接别名与 Spring bean name 解耦，\n"
                + " * 别名可自由取名（如 {@code mysql}），不会触发 BeanDefinitionOverrideException。\n"
                + " * Model 通过 {@code @DataSource(\"别名\")} 使用时，框架<b>先查别名注册表</b>，\n"
                + " * 找不到才回退 Spring 容器。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class DatabaseConfig {\n"
                + "\n"
                + "    /**\n"
                + "     * 创建并初始化全局唯一的 gaarason Container。\n"
                + "     * <p>\n"
                + "     * 注册自定义 {@link ModelInstanceProvider}，使 gaarason 需要 Model 实例时\n"
                + "     * 通过 Spring 容器获取托管单例；随后存入 {@link ConnectionManager}，\n"
                + "     * 供框架内部（迁移、seeder、DB 门面等）复用同一实例。\n"
                + "     */\n"
                + "    @Bean\n"
                + "    public ContainerBootstrap containerBootstrap(@Autowired Environment env) {\n"
                + "        String scanPackages = env.getProperty(\"gaarason.database.scan.packages\",\n"
                + "                \"" + basePackage + ".app.model\");\n"
                + "        if (System.getProperty(\"gaarason.database.scan.packages\") == null) {\n"
                + "            System.setProperty(\"gaarason.database.scan.packages\", scanPackages);\n"
                + "        }\n"
                + "\n"
                + "        ContainerBootstrap bootstrap = ContainerBootstrap.build();\n"
                + "        bootstrap.defaultRegister();\n"
                + "\n"
                + "        ModelInstanceProvider modelInstanceProvider = bootstrap.getBean(ModelInstanceProvider.class);\n"
                + "        modelInstanceProvider.register(modelClass -> SpringContext.bean(modelClass));\n"
                + "\n"
                + "        bootstrap.bootstrapGaarasonAutoconfiguration();\n"
                + "        bootstrap.initialization();\n"
                + "\n"
                + "        // 存入框架门面，保证全框架自始至终使用同一个 ContainerBootstrap\n"
                + "        ConnectionManager.setContainer(bootstrap);\n"
                + "        return bootstrap;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 默认连接（别名 sqlite）。\n"
                + "     * <p>\n"
                + "     * Model 未标注 {@code @DataSource} 时使用本连接。用同一个 {@code bootstrap}\n"
                + "     * 把主库 {@link DataSource} 包装为 {@link GaarasonDataSource}。\n"
                + "     *\n"
                + "     * <h3>不需要再写 &#64;Bean DataSource</h3>\n"
                + "     * 框架会自动把<b>默认连接</b>以惰性委托的形式注册为 &#64;Primary 的 Spring\n"
                + "     * {@link DataSource} Bean，供事务管理器、JdbcTemplate 以及各类\n"
                + "     * {@code @ConditionalOnBean(DataSource.class)} 使用。\n"
                + "     * <p>\n"
                + "     * 默认连接 = 标记了 {@code defaultConnection = true} 的连接；\n"
                + "     * 若一个都没标记，则<b>第一个注册的连接</b>自动成为默认连接。\n"
                + "     */\n"
                + "    @RegisterConnection(value = \"sqlite\", defaultConnection = true)\n"
                + "    public GaarasonDataSource sqliteConnection(Environment env, ContainerBootstrap bootstrap) {\n"
                + "        DruidDataSource druid = new DruidDataSource();\n"
                + "        druid.setUrl(env.getProperty(\"spring.datasource.url\", \"jdbc:sqlite:database.sqlite\"));\n"
                + "        druid.setDriverClassName(env.getProperty(\"spring.datasource.driver-class-name\", \"org.sqlite.JDBC\"));\n"
                + "        druid.setUsername(env.getProperty(\"spring.datasource.username\", \"\"));\n"
                + "        druid.setPassword(env.getProperty(\"spring.datasource.password\", \"\"));\n"
                + "        return GaarasonDataSourceBuilder.build(druid, bootstrap);\n"
                + "    }\n"
                + "\n"
                + "    // 额外连接示例：Model 上写 @DataSource(\"mysql\")、迁移里写 connection() { return \"mysql\"; } 即可使用。\n"
                + "    // 额外连接无需注册为 Spring Bean，别名可自由取名，不会与同名 bean 冲突。\n"
                + "    // 注意：这里复用同一个 bootstrap 参数，切勿另行 build()。\n"
                + "    //\n"
                + "    // @RegisterConnection(\"mysql\")\n"
                + "    // public GaarasonDataSource mysqlConnection(Environment env, ContainerBootstrap bootstrap) {\n"
                + "    //     DruidDataSource druid = new DruidDataSource();\n"
                + "    //     druid.setUrl(env.getProperty(\"jaravel.database.mysql.url\"));\n"
                + "    //     druid.setDriverClassName(\"com.mysql.cj.jdbc.Driver\");\n"
                + "    //     druid.setUsername(env.getProperty(\"jaravel.database.mysql.username\"));\n"
                + "    //     druid.setPassword(env.getProperty(\"jaravel.database.mysql.password\"));\n"
                + "    //     return GaarasonDataSourceBuilder.build(druid, bootstrap);\n"
                + "    // }\n"
                + "}\n";
    }
}
