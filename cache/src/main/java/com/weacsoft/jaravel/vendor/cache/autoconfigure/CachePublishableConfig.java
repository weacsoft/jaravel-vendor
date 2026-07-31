package com.weacsoft.jaravel.vendor.cache.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;

/**
 * cache 模块的可发布配置类模板，由 {@code artisan vendor:publish --tag=cache} 发布。
 * <p>
 * 发布后在业务工程生成 {@code config/CacheConfig.java}，
 * 内含 {@code @RegisterCacheStore} 示例方法，用户可直接修改。
 */
public class CachePublishableConfig implements PublishableConfig {

    @Override
    public String tag() {
        return "cache";
    }

    @Override
    public String className() {
        return "CacheConfig";
    }

    @Override
    public String description() {
        return "缓存 store 注册（array / file / redis）";
    }

    @Override
    public String source(String basePackage) {
        return "package " + basePackage + ".config;\n"
                + "\n"
                + "import com.weacsoft.jaravel.vendor.cache.CacheStore;\n"
                + "import com.weacsoft.jaravel.vendor.cache.RegisterCacheStore;\n"
                + "import com.weacsoft.jaravel.vendor.cache.autoconfigure.CacheProperties;\n"
                + "import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;\n"
                + "import com.weacsoft.jaravel.vendor.cache.driver.FileCacheDriver;\n"
                + "import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "\n"
                + "/**\n"
                + " * 缓存配置，对齐 Laravel config/cache.php。\n"
                + " * <p>\n"
                + " * 由 {@code artisan vendor:publish --tag=cache} 发布生成，可自由修改。\n"
                + " *\n"
                + " * <h3>说明</h3>\n"
                + " * <ul>\n"
                + " *   <li>{@code @RegisterCacheStore} 注册的 store <b>不会</b>成为 Spring Bean，\n"
                + " *       因此 store 名称不会与容器内同名 bean 冲突。</li>\n"
                + " *   <li>方法参数从 Spring 容器按类型自动注入，行为与 {@code @Bean} 一致。</li>\n"
                + " *   <li>删除本文件即可回退到框架默认（array 内存驱动）。</li>\n"
                + " * </ul>\n"
                + " */\n"
                + "@Configuration\n"
                + "public class CacheConfig {\n"
                + "\n"
                + "    /**\n"
                + "     * 内存缓存 store，作为默认 store。\n"
                + "     * <p>\n"
                + "     * 无任何外部依赖，进程重启后数据丢失。\n"
                + "     */\n"
                + "    @RegisterCacheStore(value = \"array\", defaultStore = true)\n"
                + "    public CacheStore arrayStore() {\n"
                + "        return new DefaultCacheStore(new ArrayCacheDriver(), \"jaravel\");\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * 文件缓存 store，数据持久化到磁盘目录。\n"
                + "     * <p>\n"
                + "     * 通过 {@code Cache.store(\"file\")} 使用。\n"
                + "     */\n"
                + "    @RegisterCacheStore(\"file\")\n"
                + "    public CacheStore fileStore(CacheProperties properties) {\n"
                + "        return new DefaultCacheStore(new FileCacheDriver(properties.getFileDir()),\n"
                + "                properties.getPrefix());\n"
                + "    }\n"
                + "}\n";
    }
}
