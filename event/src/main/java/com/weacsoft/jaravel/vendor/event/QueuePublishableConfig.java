package com.weacsoft.jaravel.vendor.event;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * queue 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=queue} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/QueueConfig.java}，
 * 内含 {@code @RegisterQueueDriver} 示例，对齐 Laravel {@code config/queue.php}。
 * <p>
 * 置于 {@code event} 基础模块（队列原始功能所在），不依赖 {@code queue-database}，
 * 因此只要引入框架基础（starter）即可发布队列配置，无需引入额外的数据库驱动模块。
 */
public class QueuePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "queue";
    }

    @Override
    public String className() {
        return "QueueConfig";
    }

    @Override
    public String description() {
        return "队列驱动注册（database / redis / sync）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;\n"
                + "import com.weacsoft.jaravel.vendor.core.queue.RegisterQueueDriver;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "/**\n"
                + " * 队列配置，对齐 Laravel config/queue.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=queue} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>驱动选择与回退</h3>\n"
                + " * <ol>\n"
                + " *   <li>{@code @RegisterQueueDriver} 注解注册的驱动（<b>全局只允许一个</b>）</li>\n"
                + " *   <li>自动装配：{@code jaravel.queue.driver=redis} 且引入 jaravel-redis</li>\n"
                + " *   <li>自动装配：{@code jaravel.queue.driver=database} 且存在 DataSource</li>\n"
                + " *   <li>都没有 → <b>sync 同步模式</b>，任务在当前线程立即执行</li>\n"
                + " * </ol>\n"
                + " *\n"
                + " * <h3>数据库表要求</h3>\n"
                + " * 使用 database 驱动时需要 {@code jobs} 与 {@code failed_jobs} 两张表，\n"
                + " * 执行 {@code artisan queue:table} 生成迁移后再 {@code artisan migrate}。\n"
                + " */\n"
                + "@Configuration\n"
                + "public class QueueConfig {\n"
                + "\n"
                + "    // 自定义队列驱动（全局唯一）。不声明则使用框架自动装配的驱动。\n"
                + "    // 如需覆盖框架已装配的驱动，请使用 @RegisterQueueDriver(override = true)\n"
                + "    //\n"
                + "    // @RegisterQueueDriver\n"
                + "    // public QueueDriver myQueueDriver() {\n"
                + "    //     return new MyQueueDriver();\n"
                + "    // }\n"
                + "}\n";
    }
}
