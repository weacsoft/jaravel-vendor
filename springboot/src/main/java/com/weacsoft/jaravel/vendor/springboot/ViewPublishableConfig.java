package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * jblade 视图模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=view} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/ViewConfig.java}，
 * 内含 {@code @RegisterDirective} 示例（对齐 Laravel 的
 * {@code Blade::directive()} 与 {@code Blade::if()}），
 * 以及静态资源配置 {@code staticResourceConfigurer}（对齐原 StaticResourceConfig）。
 */
public class ViewPublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "view";
    }

    @Override
    public String className() {
        return "ViewConfig";
    }

    @Override
    public String description() {
        return "Blade 自定义指令注册";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.jblade.BladeDirectives;\n"
                + "import com.weacsoft.jaravel.vendor.jblade.RegisterDirective;\n"
                + "import com.weacsoft.jaravel.vendor.core.pagination.Paginator;\n"
                + "import org.slf4j.Logger;\n"
                + "import org.slf4j.LoggerFactory;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;\n"
                + "import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n"
                + "\n"
                + "import java.io.File;\n"
                + "import java.text.SimpleDateFormat;\n"
                + "import java.util.Date;\n"
                + "\n"
                + "/**\n"
                + " * 视图配置：注册 Blade 自定义指令 + 静态资源。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=view} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>说明</h3>\n"
                + " * <ul>\n"
                + " *   <li>指令是<b>命名多实例</b>组件，可注册任意多个；同名后注册者覆盖先注册者。</li>\n"
                + " *   <li>{@code condition = false}（默认）为输出指令，返回 {@code Handler}；\n"
                + " *       {@code condition = true} 为条件指令，返回 {@code Condition}。</li>\n"
                + " *   <li>删除本文件不影响启动，仅表示不注册任何自定义指令。</li>\n"
                + " *   <li>{@code strictMode}：开启后，模板访问未定义变量/数组 key 时会输出 warning。</li>\n"
                + " *   <li>{@code paginatorViewName}：自定义默认分页模板名。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@Configuration\n"
                + "public class ViewConfig {\n"
                + "\n"
                + "    private static final Logger log = LoggerFactory.getLogger(ViewConfig.class);\n"
                + "\n"
                + "    /**\n"
                + "     * 严格模式：开启后，jblade 模板引擎在访问未定义变量或数组 key 时会输出 warning。\n"
                + "     * <p>\n"
                + "     * 注意：此配置不需要注入 Spring，直接作为静态字段使用。\n"
                + "     * </p>\n"
                + "     */\n"
                + "    public static boolean strictMode = false;\n"
                + "\n"
                + "    /**\n"
                + "     * 默认分页模板名，对齐{@code Paginator.setDefaultViewName()}。\n"
                + "     * 如需自定义，修改此字段即可。\n"
                + "     */\n"
                + "    public static String paginatorViewName = \"layouts.mdui.pageinator\";\n"
                + "\n"
                + "    /**\n"
                + "     * 初始化方法：在类加载时设置分页器默认模板名。\n"
                + "     */\n"
                + "    static {\n"
                + "        Paginator.setDefaultViewName(paginatorViewName);\n"
                + "        log.info(\"[view] strictMode={}\", strictMode);\n"
                + "        log.info(\"[view] paginatorViewName={}\", paginatorViewName);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 输出指令：{@code @datetime($value)} 格式化日期。\n"
                + "     */\n"
                + "    @RegisterDirective(\"datetime\")\n"
                + "    public BladeDirectives.Handler datetime() {\n"
                + "        return args -> {\n"
                + "            if (args.length == 0 || args[0] == null) {\n"
                + "                return \"\";\n"
                + "            }\n"
                + "            Object value = args[0];\n"
                + "            Date date = value instanceof Date d ? d : new Date();\n"
                + "            return new SimpleDateFormat(\"yyyy-MM-dd HH:mm:ss\").format(date);\n"
                + "        };\n"
                + "    }\n"
                + "\n"
                + "    // 条件指令示例：@admin ... @endadmin\n"
                + "    // @RegisterDirective(value = \"admin\", condition = true)\n"
                + "    // public BladeDirectives.Condition admin() {\n"
                + "    //     return args -> Auth.check() && Auth.user().isAdmin();\n"
                + "    // }\n"
                + "\n"
                + "    /**\n"
                + "     * 静态资源配置（对齐原 StaticResourceConfig）。\n"
                + "     * <p>\n"
                + "     * 以 {@code @Bean} 暴露 {@link WebMvcConfigurer}，注册静态资源处理器：\n"
                + "     * <ul>\n"
                + "     *   <li>优先从文件系统 {@code ./public/} 加载</li>\n"
                + "     *   <li>回退到 {@code classpath:/static/}</li>\n"
                + "     * </ul>\n"
                + "     * 访问路径前缀为 {@code /static/**}。\n"
                + "     */\n"
                + "    @Bean\n"
                + "    public WebMvcConfigurer staticResourceConfigurer() {\n"
                + "        return new WebMvcConfigurer() {\n"
                + "            @Override\n"
                + "            public void addResourceHandlers(ResourceHandlerRegistry registry) {\n"
                + "                File publicDir = new File(\"public\");\n"
                + "                boolean hasPublicDir = publicDir.isDirectory();\n"
                + "\n"
                + "                if (hasPublicDir) {\n"
                + "                    registry.addResourceHandler(\"/static/**\")\n"
                + "                            .addResourceLocations(\"file:./public/\", \"classpath:/static/\");\n"
                + "                    log.info(\"[static] 静态资源配置: /static/** -> file:./public/ + classpath:/static/\");\n"
                + "                } else {\n"
                + "                    registry.addResourceHandler(\"/static/**\")\n"
                + "                            .addResourceLocations(\"classpath:/static/\");\n"
                + "                    log.info(\"[static] 静态资源配置: /static/** -> classpath:/static/\");\n"
                + "                }\n"
                + "            }\n"
                + "        };\n"
                + "    }\n"
                + "}\n";
    }
}
