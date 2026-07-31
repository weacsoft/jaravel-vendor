package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * jblade 视图模块的可发布配置类模板，
 * 由 {@code artisan vendor:publish --tag=view} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/ViewConfig.java}，
 * 内含 {@code @RegisterDirective} 示例，对齐 Laravel 的
 * {@code Blade::directive()} 与 {@code Blade::if()}。
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
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "import java.text.SimpleDateFormat;\n"
                + "import java.util.Date;\n"
                + "\n"
                + "/**\n"
                + " * 视图配置：注册 Blade 自定义指令。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=view} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>说明</h3>\n"
                + " * <ul>\n"
                + " *   <li>指令是<b>命名多实例</b>组件，可注册任意多个；同名后注册者覆盖先注册者。</li>\n"
                + " *   <li>{@code condition = false}（默认）为输出指令，返回 {@code Handler}；\n"
                + " *       {@code condition = true} 为条件指令，返回 {@code Condition}。</li>\n"
                + " *   <li>删除本文件不影响启动，仅表示不注册任何自定义指令。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@Configuration\n"
                + "public class ViewConfig {\n"
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
                + "}\n";
    }
}
